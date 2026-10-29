
param(
    [Parameter(Mandatory = $true)]
    [long]$PortfolioId,

    [int]$Warmup = 10,

    [int]$Iterations = 100
)

$ErrorActionPreference = "Stop"

if ($Warmup -lt 0 -or $Iterations -lt 1) {
    throw "Invalid benchmark parameters"
}

$url = "http://localhost:8080/api/portfolios/$PortfolioId/valuation"

Write-Host "Target: $url"
Write-Host "Warmup: $Warmup"
Write-Host "Iterations: $Iterations"

# Warm up the JVM, database connection pool and caches.
for ($i = 0; $i -lt $Warmup; $i++) {
    $null = Invoke-WebRequest `
        -Uri $url `
        -UseBasicParsing
}

$times = New-Object 'System.Collections.Generic.List[double]'

for ($i = 0; $i -lt $Iterations; $i++) {

    $watch = [System.Diagnostics.Stopwatch]::StartNew()

    $response = Invoke-WebRequest `
        -Uri $url `
        -UseBasicParsing

    $watch.Stop()

    if ($response.StatusCode -ne 200) {
        throw "Unexpected HTTP status: $($response.StatusCode)"
    }

    $times.Add($watch.Elapsed.TotalMilliseconds)
}

$sorted = @($times | Sort-Object)

function Get-Percentile {
    param(
        [double[]]$Values,
        [double]$Percentile
    )

    $rank = [math]::Ceiling(
        $Percentile * $Values.Count
    ) - 1

    $rank = [math]::Max(
        0,
        [math]::Min($rank, $Values.Count - 1)
    )

    return $Values[$rank]
}

$average = ($times | Measure-Object -Average).Average

$result = [PSCustomObject]@{
    PortfolioId = $PortfolioId
    Iterations  = $Iterations
    AverageMs   = [math]::Round($average, 3)
    P50Ms       = [math]::Round(
        (Get-Percentile $sorted 0.50), 3
    )
    P95Ms       = [math]::Round(
        (Get-Percentile $sorted 0.95), 3
    )
    P99Ms       = [math]::Round(
        (Get-Percentile $sorted 0.99), 3
    )
    MaxMs       = [math]::Round($sorted[-1], 3)
}

$result | Format-List

New-Item -ItemType Directory `
    -Force -Path "performance/results" |
    Out-Null

$result |
    Export-Csv `
        -Path "performance/results/valuation-latest.csv" `
        -NoTypeInformation `
        -Encoding UTF8
