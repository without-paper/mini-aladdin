# LO3-F / R7: reproducible sequential HTTP valuation benchmark.
# Run from the Mini Aladdin repository root on Windows PowerShell 5.1+.
# Creates a NEW portfolio, 100 committed BUY trades, and 10 fresh quotes.
# These records persist in the configured database; existing data is untouched.
param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Runs = 3,
    [int]$WarmupPerRun = 20,
    [int]$MeasuredPerRun = 100,
    [double]$P95ThresholdMs = 200.0
)

$ErrorActionPreference = "Stop"
if ($Runs -lt 1 -or $WarmupPerRun -lt 0 -or $MeasuredPerRun -lt 1) {
    throw "Runs must be positive; warmups cannot be negative; measured requests must be positive."
}
$BaseUrl = $BaseUrl.TrimEnd('/')
$seedToken = ([guid]::NewGuid().ToString('N')).Substring(0, 8).ToUpperInvariant()
$resultsDir = Join-Path (Get-Location).Path "performance/results"
New-Item -ItemType Directory -Path $resultsDir -Force | Out-Null
$prefix = Join-Path $resultsDir "r7-$seedToken"
$rawFile = "$prefix-raw.csv"
$summaryFile = "$prefix-summary.csv"
$metadataFile = "$prefix-environment.json"

function Get-Json([string]$Path) {
    return Invoke-RestMethod -Method Get -Uri "$BaseUrl$Path"
}
function Post-Json([string]$Path, [object]$Payload, [hashtable]$Headers = @{}) {
    $json = ConvertTo-Json -InputObject $Payload -Compress -Depth 5
    return Invoke-RestMethod -Method Post -Uri "$BaseUrl$Path" `
        -Headers $Headers -ContentType "application/json" -Body $json
}
function Put-Json([string]$Path, [object]$Payload) {
    $json = ConvertTo-Json -InputObject $Payload -Compress -Depth 5
    return Invoke-RestMethod -Method Put -Uri "$BaseUrl$Path" `
        -ContentType "application/json" -Body $json
}

$health = Get-Json "/actuator/health"
if ($health.status -ne "UP") { throw "Application health is not UP." }

Write-Host "R7 benchmark: seed token $seedToken"
Write-Host "Creating isolated benchmark portfolio in the configured database."
$portfolio = Post-Json "/api/portfolios" @{
    name = "R7 Benchmark $seedToken"
    initialCash = 100000.00
}
$portfolioId = [long]$portfolio.id

$symbols = @()
for ($i = 0; $i -lt 10; $i++) {
    $symbol = "R7${seedToken}$($i.ToString('00'))"
    $symbols += $symbol
    $null = Put-Json "/api/market-prices/$symbol" @{ price = 101.00 }
}

for ($i = 0; $i -lt 100; $i++) {
    $symbol = $symbols[$i % 10]
    $key = "r7-${seedToken}-$($i.ToString('000'))"
    $json = ConvertTo-Json -InputObject @{
        symbol = $symbol
        side = "BUY"
        quantity = 1
        price = 100.00
    } -Compress
    $response = Invoke-WebRequest -Method Post `
        -Uri "$BaseUrl/api/portfolios/$portfolioId/trades" `
        -Headers @{ "Idempotency-Key" = $key } `
        -ContentType "application/json" -Body $json -UseBasicParsing
    if ([int]$response.StatusCode -ne 201) {
        throw "Seeding trade $i returned HTTP $($response.StatusCode), expected 201."
    }
}

# Independent correctness / workload oracle (NOT derived from calculator output).
# 100 BUY x 1 share x $100 = $10,000 spending.
# 10 positions x 10 shares x $101 = $10,100 market value.
$tradesResponse = Invoke-WebRequest `
    -Uri "$BaseUrl/api/portfolios/$portfolioId/trades" `
    -UseBasicParsing

$trades = ConvertFrom-Json -InputObject $tradesResponse.Content
$valuation = Get-Json "/api/portfolios/$portfolioId/valuation"
if ($trades.Count -ne 100 -or @($valuation.positions).Count -ne 10) {
    throw "Wrong dataset: $($trades.Count) trades and $(@($valuation.positions).Count) positions."
}
if ([decimal]$valuation.availableCash -ne [decimal]90000 -or
    [decimal]$valuation.marketValue -ne [decimal]10100 -or
    [decimal]$valuation.totalValue -ne [decimal]100100 -or
    [decimal]$valuation.totalPnl -ne [decimal]100) {
    throw "Independent financial oracle failed; benchmark aborted."
}

$cpu = Get-CimInstance Win32_Processor -ErrorAction SilentlyContinue | Select-Object -First 1
$computer = Get-CimInstance Win32_ComputerSystem -ErrorAction SilentlyContinue
$commit = (& git rev-parse HEAD 2>$null)
$meta = [ordered]@{
    generatedUtc = [DateTime]::UtcNow.ToString('o')
    commit = "$commit".Trim()
    seedToken = $seedToken
    portfolioId = $portfolioId
    baseUrl = $BaseUrl
    rounds = $Runs
    warmupPerRound = $WarmupPerRun
    measuredPerRound = $MeasuredPerRun
    p95ThresholdMs = $P95ThresholdMs
    measurement = "Sequential client-observed Invoke-WebRequest elapsed time, including HTTP client and local network overhead"
    environmentOS = [System.Environment]::OSVersion.VersionString
    powershellVersion = $PSVersionTable.PSVersion.ToString()
    cpu = if ($cpu) { $cpu.Name } else { "unknown" }
    memoryGiB = if ($computer) { [math]::Round($computer.TotalPhysicalMemory / 1GB, 2) } else { $null }
    workload = "100 committed BUY trades; 10 open symbols; 10 shares/symbol; quotes refreshed before measurement"
    expectedCash = 90000
    expectedMarketValue = 10100
    expectedTotalValue = 100100
    expectedTotalPnl = 100
}
$meta | ConvertTo-Json -Depth 5 | Set-Content -Path $metadataFile -Encoding UTF8

$raw = New-Object 'System.Collections.Generic.List[object]'
$summary = New-Object 'System.Collections.Generic.List[object]'
$url = "$BaseUrl/api/portfolios/$portfolioId/valuation"

try {
    for ($run = 1; $run -le $Runs; $run++) {
        Write-Host "Run $run/${Runs}: $WarmupPerRun warmups then $MeasuredPerRun measured requests"
        for ($w = 1; $w -le $WarmupPerRun; $w++) {
            $warm = Invoke-WebRequest -Uri $url -UseBasicParsing
            if ([int]$warm.StatusCode -ne 200) { throw "Warmup returned HTTP $($warm.StatusCode)." }
        }

        $measured = New-Object 'System.Collections.Generic.List[double]'
        for ($iteration = 1; $iteration -le $MeasuredPerRun; $iteration++) {
            $watch = [System.Diagnostics.Stopwatch]::StartNew()
            try {
                $response = Invoke-WebRequest -Uri $url -UseBasicParsing
                $watch.Stop()
                $status = [int]$response.StatusCode
            } catch {
                $watch.Stop()
                $raw.Add([PSCustomObject]@{
                    Run = $run; Iteration = $iteration; Status = "ERROR"
                    DurationMs = [math]::Round($watch.Elapsed.TotalMilliseconds, 3)
                    TimestampUtc = [DateTime]::UtcNow.ToString('o')
                    CorrelationId = $null
                })
                throw "Measured request $iteration of run $run failed: $_"
            }
            $duration = $watch.Elapsed.TotalMilliseconds
            $raw.Add([PSCustomObject]@{
                Run = $run
                Iteration = $iteration
                Status = $status
                DurationMs = [math]::Round($duration, 3)
                TimestampUtc = [DateTime]::UtcNow.ToString('o')
                CorrelationId = [string]$response.Headers["X-Correlation-ID"]
            })

            if ($status -ne 200) {
                throw "Measured request returned HTTP $status."
            }
            $measured.Add($duration)
        }

        $sorted = @($measured | Sort-Object)
        $n = $sorted.Count
        $p95index = [math]::Ceiling(0.95 * $n) - 1
        $p50index = [math]::Ceiling(0.50 * $n) - 1
        $p99index = [math]::Ceiling(0.99 * $n) - 1
        $p95 = $sorted[$p95index]
        $summary.Add([PSCustomObject]@{
            Run = $run
            MeasuredRequests = $n
            SuccessfulRequests = $n
            AverageMs = [math]::Round(($measured | Measure-Object -Average).Average, 3)
            P50Ms = [math]::Round($sorted[$p50index], 3)
            P95Ms = [math]::Round($p95, 3)
            P99Ms = [math]::Round($sorted[$p99index], 3)
            MaxMs = [math]::Round($sorted[$n - 1], 3)
            PassR7 = ($p95 -le $P95ThresholdMs)
        })
        Write-Host "Run $run P95: $([math]::Round($p95, 3)) ms"
    }
} finally {
    if ($raw.Count -gt 0) {
        $raw | Export-Csv -Path $rawFile -NoTypeInformation -Encoding UTF8
    }
    if ($summary.Count -gt 0) {
        $summary | Export-Csv -Path $summaryFile -NoTypeInformation -Encoding UTF8
    }
}

$summary | Format-Table -AutoSize
$allPassed = @($summary | Where-Object { -not $_.PassR7 }).Count -eq 0
Write-Host "Raw latency data: $rawFile"
Write-Host "Summary: $summaryFile"
Write-Host "Environment: $metadataFile"
Write-Host "R7 compliant: $allPassed"
if (-not $allPassed) { throw "One or more P95 runs exceeded $P95ThresholdMs ms." }
