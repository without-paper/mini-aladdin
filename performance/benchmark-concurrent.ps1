
param(
    [Parameter(Mandatory = $true)]
    [long]$PortfolioId,

    [int]$Concurrency = 5,

    [int]$Requests = 50
)

$ErrorActionPreference = "Stop"

if ($Concurrency -lt 1 -or $Requests -lt 1) {
    throw "Invalid benchmark parameters"
}

$url = "http://localhost:8080/api/portfolios/$PortfolioId/valuation"

$jobs = @()
$allResults = @()

$watch = [System.Diagnostics.Stopwatch]::StartNew()

try {
    $remaining = $Requests

    while ($remaining -gt 0) {

        $batch = [math]::Min($Concurrency, $remaining)

        $jobs = @(
            1..$batch | ForEach-Object {

                Start-Job -ScriptBlock {
                    param($target)

                    $timer = [System.Diagnostics.Stopwatch]::StartNew()

                    try {
                        $response = Invoke-WebRequest `
                            -Uri $target `
                            -UseBasicParsing

                        $timer.Stop()

                        [PSCustomObject]@{
                            Status = [int]$response.StatusCode
                            DurationMs = $timer.Elapsed.TotalMilliseconds
                        }

                    } catch {
                        $timer.Stop()

                        [PSCustomObject]@{
                            Status = 0
                            DurationMs = $timer.Elapsed.TotalMilliseconds
                        }
                    }

                } -ArgumentList $url
            }
        )

        $null = $jobs | Wait-Job

        $results = @($jobs | Receive-Job)

        foreach ($result in $results) {
            if ($result.Status -ne 200) {
                throw "A request failed with status $($result.Status)"
            }
        }

        $allResults += $results

        $jobs | Remove-Job

        $jobs = @()
        $allResults = @()
        $remaining -= $batch
    }

} finally {
    if ($jobs.Count -gt 0) {
        $jobs | Stop-Job -ErrorAction SilentlyContinue
        $jobs | Remove-Job -Force
    }

    $watch.Stop()
}

$latencies = @($allResults | ForEach-Object {
    $_.DurationMs
} | Sort-Object)

$average = ($latencies | Measure-Object -Average).Average

Write-Host "Requests: $Requests"
Write-Host "Concurrency: $Concurrency"
Write-Host "Wall Time: $([math]::Round($watch.Elapsed.TotalSeconds, 3)) s"
Write-Host "Average Client Duration: $([math]::Round($average, 3)) ms"
Write-Host "Throughput: $([math]::Round($Requests / $watch.Elapsed.TotalSeconds, 2)) req/s"
