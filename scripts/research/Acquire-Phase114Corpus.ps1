# Explicit local research tool; never invoked by application startup or tests.
[CmdletBinding()]
param([Parameter(Mandatory=$true)][switch]$Acquire)
$ErrorActionPreference = 'Stop'
if (-not $Acquire) { throw 'Explicit -Acquire is required.' }
$reviewRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $reviewRoot
$reviewEnvironmentHash = (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash
$reviewJava = (Get-Command java -CommandType Application -ErrorAction Stop).Source
$reviewJavac = (Get-Command javac -CommandType Application -ErrorAction Stop).Source
# Use only a previously tested build. Dependency generation does not start the application.
& .\mvnw.cmd -pl apps/trading-core dependency:build-classpath '-Dmdep.outputFile=target/research-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Cannot build research classpath.' }
$reviewClasspath = (Resolve-Path 'apps/trading-core/target/classes').Path + ';' +
    (Get-Content 'apps/trading-core/target/research-classpath.txt' -Raw).Trim()
New-Item -ItemType Directory -Force 'tmp/phase114-classes' | Out-Null
& $reviewJavac -cp $reviewClasspath -d tmp/phase114-classes scripts/research/Phase114CorpusAcquisition.java
if ($LASTEXITCODE -ne 0) { throw 'Research harness compilation failed.' }
. .\scripts\Use-DevelopmentInfrastructure.ps1
foreach ($reviewName in @('KITE_ORDER_EXECUTION_ENABLED','KITE_OPERATOR_CONTROL_ENABLED',
    'KITE_LIVE_TEST_ENABLED','ENABLE_LIVE_TRADING','KITE_MARKET_DATA_ENABLED')) {
    [Environment]::SetEnvironmentVariable($reviewName, 'false', 'Process')
}
$env:SERVER_ADDRESS='127.0.0.1'
$env:EMERGENCY_STOP='true'
$env:KITE_REST_ENABLED='true'
try {
    & $reviewJava '-Duser.timezone=UTC' -cp ('tmp/phase114-classes;' + $reviewClasspath) `
        com.kitehybrid.platform.broker.infrastructure.kite.Phase114CorpusAcquisition
    if ($LASTEXITCODE -ne 0) { throw 'Research acquisition failed.' }
} finally {
    if ($reviewEnvironmentHash -ne (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash) {
        throw 'Environment file changed during research.'
    }
}
