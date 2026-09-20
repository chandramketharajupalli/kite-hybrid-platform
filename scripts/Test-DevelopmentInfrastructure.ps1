# Synthetic dotenv regression checks; no Docker daemon, broker, or real .env is used.
[CmdletBinding()]
param([switch]$Child)

$ErrorActionPreference = 'Stop'
if (-not $Child) {
    # Test changes must never affect the calling developer shell.
    & (Join-Path $PSHOME 'powershell.exe') -NoProfile -NonInteractive -ExecutionPolicy Bypass `
        -File $PSCommandPath -Child
    if ($LASTEXITCODE -ne 0) { throw 'Development environment regression checks failed.' }
    return
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$temporaryParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$fixtureRoot = Join-Path $temporaryParent ('kite env regression ' + [Guid]::NewGuid().ToString('N'))
$fixtureScripts = Join-Path $fixtureRoot 'scripts'
$fixtureEnv = Join-Path $fixtureRoot '.env'
$fixtureHelper = Join-Path $fixtureScripts 'Use-DevelopmentInfrastructure.ps1'
$kiteNames = @('KITE_REST_ENABLED', 'KITE_API_KEY', 'KITE_API_SECRET', 'KITE_REDIRECT_URL',
    'KITE_TOKEN_ENCRYPTION_KEY')
$sourceNames = @('DB_NAME', 'DB_USER', 'DB_PASSWORD', 'REDIS_PASSWORD') + $kiteNames
$allNames = $sourceNames + @('DB_URL', 'REDIS_HOST', 'REDIS_PORT', 'SPRING_PROFILES_ACTIVE',
    'TRADING_MODE', 'ENABLE_LIVE_TRADING', 'EMERGENCY_STOP')
$fixtureKeyBytes = New-Object byte[] 32
for ($index = 0; $index -lt $fixtureKeyBytes.Length; $index++) { $fixtureKeyBytes[$index] = 251 }
$fixtureKey = [Convert]::ToBase64String($fixtureKeyBytes)
$replacementKey = [Convert]::ToBase64String((New-Object byte[] 32))
$fixturePassword = 'synthetic quoted # value=with=equals'
$fixtureSecret = 'synthetic $literal $(throw "must not execute") # value=with=equals ' + [Guid]::NewGuid().ToString('N')
$fixtureApiKey = 'synthetic-key-' + [Guid]::NewGuid().ToString('N')
$fixtureRedirect = 'http://localhost:8080/api/broker/kite/auth/callback'
$checksPassed = 0

function Write-FixtureEnvironment([string]$EncryptionKey, [string]$KiteSettings = 'configured',
        [string]$Database = 'synthetic_database', [switch]$QuoteKey) {
    $lines = @(
        "DB_NAME=$Database", 'DB_USER=synthetic_user', "DB_PASSWORD='$fixturePassword'",
        "REDIS_PASSWORD='$fixturePassword'"
    )
    if ($KiteSettings -eq 'configured') {
        $keySetting = if ($QuoteKey) { "KITE_TOKEN_ENCRYPTION_KEY=`"$EncryptionKey`"" } else {
            "KITE_TOKEN_ENCRYPTION_KEY=$EncryptionKey"
        }
        $lines += @('KITE_REST_ENABLED=true', "KITE_API_KEY='$fixtureApiKey'",
            "KITE_API_SECRET='$fixtureSecret'", "KITE_REDIRECT_URL=$fixtureRedirect",
            $keySetting)
    } elseif ($KiteSettings -eq 'blank') {
        $lines += @('KITE_REST_ENABLED=false', 'KITE_API_KEY=', 'KITE_API_SECRET=',
            'KITE_REDIRECT_URL=', 'KITE_TOKEN_ENCRYPTION_KEY=')
    }
    [IO.File]::WriteAllLines($fixtureEnv, $lines, (New-Object Text.UTF8Encoding($false)))
}

function Invoke-FixtureHelper {
    $result = & $fixtureHelper 2>&1 | Out-String
    Assert-True ($result.Trim() -eq 'Local infrastructure environment loaded for this PowerShell process; trading halted, Kite settings loaded.') `
        'Helper output must contain only its safe status message.'
    foreach ($sensitiveValue in @($fixtureKey, $replacementKey, $fixtureSecret, $fixtureApiKey, $fixturePassword)) {
        Assert-True (-not $result.Contains($sensitiveValue)) 'Helper output leaked a synthetic sensitive value.'
    }
}

try {
    New-Item -ItemType Directory -Path $fixtureScripts | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Use-DevelopmentInfrastructure.ps1') -Destination $fixtureHelper
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docker-compose.yml') -Destination $fixtureRoot
    foreach ($name in $allNames) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }

    Write-FixtureEnvironment $fixtureKey
    foreach ($name in @('KITE_TOKEN_ENCRYPTION_KEY', 'KITE_API_KEY', 'KITE_API_SECRET', 'DB_PASSWORD')) {
        [Environment]::SetEnvironmentVariable($name, 'stale-process-value', 'Process')
    }
    Invoke-FixtureHelper
    Assert-True ($env:KITE_TOKEN_ENCRYPTION_KEY -ceq $fixtureKey) 'A stale process key overrode the dotenv key.'
    Assert-True ($env:KITE_API_KEY -ceq $fixtureApiKey -and $env:KITE_API_SECRET -ceq $fixtureSecret) `
        'A stale process credential overrode dotenv.'
    Assert-True ($env:DB_PASSWORD -ceq $fixturePassword) 'A stale process database password overrode dotenv.'
    $checksPassed++

    Assert-True ($env:KITE_TOKEN_ENCRYPTION_KEY.Length -eq 44) 'The encryption key length changed.'
    Assert-True ($env:KITE_TOKEN_ENCRYPTION_KEY.EndsWith('=')) 'Base64 padding was lost.'
    Assert-True ([Convert]::FromBase64String($env:KITE_TOKEN_ENCRYPTION_KEY).Length -eq 32) `
        'The imported encryption key did not decode to 32 bytes.'
    Assert-True ($env:REDIS_PASSWORD -ceq $fixturePassword) 'Quoted dotenv text was altered or evaluated.'
    Assert-True ($env:KITE_API_SECRET -ceq $fixtureSecret) 'Single-quoted dotenv text was altered or evaluated.'
    Assert-True ($env:KITE_REDIRECT_URL -ceq $fixtureRedirect) 'The redirect URL was altered.'
    $checksPassed++

    Write-FixtureEnvironment $replacementKey -QuoteKey
    Invoke-FixtureHelper
    Assert-True ($env:KITE_TOKEN_ENCRYPTION_KEY -ceq $replacementKey) 'Reload retained the previously loaded key.'
    Assert-True ($env:SPRING_PROFILES_ACTIVE -eq 'development' -and $env:TRADING_MODE -eq 'PAPER' -and
        $env:ENABLE_LIVE_TRADING -eq 'false' -and $env:EMERGENCY_STOP -eq 'true') 'Safe defaults were not applied.'
    $checksPassed++

    Write-FixtureEnvironment $fixtureKey 'blank'
    Invoke-FixtureHelper
    foreach ($name in $kiteNames | Where-Object { $_ -ne 'KITE_REST_ENABLED' }) {
        Assert-True ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name, 'Process'))) `
            'An explicitly empty dotenv value retained stale process credentials.'
    }
    Assert-True ($env:KITE_REST_ENABLED -eq 'false') 'An explicit Kite opt-out was ignored.'
    $checksPassed++

    Write-FixtureEnvironment $fixtureKey 'omitted'
    foreach ($name in $kiteNames) { [Environment]::SetEnvironmentVariable($name, 'stale-process-value', 'Process') }
    Invoke-FixtureHelper
    foreach ($name in $kiteNames | Where-Object { $_ -ne 'KITE_REST_ENABLED' }) {
        Assert-True ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name, 'Process'))) `
            'An omitted dotenv credential retained a stale process value.'
    }
    Assert-True ($env:KITE_REST_ENABLED -eq 'false') 'Kite must default to disabled when dotenv omits opt-in.'
    $checksPassed++

    $savedEnvironment = @{}
    foreach ($name in $allNames) { $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
    Write-FixtureEnvironment $fixtureKey 'configured' 'invalid-database-name'
    $failedSafely = $false
    try { Invoke-FixtureHelper } catch {
        $failedSafely = $_.Exception.Message -eq 'Cannot load local Compose configuration. Check Docker Compose and the required .env variables. DB_NAME must be 1-63 letters, digits or underscores and cannot start with a digit.'
    }
    Assert-True $failedSafely 'Invalid configuration did not fail with the generic safe message.'
    foreach ($name in $allNames) {
        Assert-True ([Environment]::GetEnvironmentVariable($name, 'Process') -ceq $savedEnvironment[$name]) `
            'A failed reload changed the calling process environment.'
    }
    $checksPassed++

    # Compose must fail safely too, before any shell values are published.
    [IO.File]::WriteAllLines($fixtureEnv, @('DB_NAME=synthetic_database'), (New-Object Text.UTF8Encoding($false)))
    $failedSafely = $false
    try { Invoke-FixtureHelper } catch {
        $failedSafely = $_.Exception.Message -eq 'Cannot load local Compose configuration. Check Docker Compose and the required .env variables. DB_NAME must be 1-63 letters, digits or underscores and cannot start with a digit.'
    }
    Assert-True $failedSafely 'A Compose failure did not return the generic safe message.'
    foreach ($name in $allNames) {
        Assert-True ([Environment]::GetEnvironmentVariable($name, 'Process') -ceq $savedEnvironment[$name]) `
            'A Compose failure changed the calling process environment.'
    }
    $checksPassed++

    Write-Output "$checksPassed development environment regression checks passed (synthetic fixtures only)."
} finally {
    # This is the exact new test directory, resolved within the system temporary directory.
    $resolvedFixture = [IO.Path]::GetFullPath($fixtureRoot)
    if (-not $resolvedFixture.StartsWith($temporaryParent, [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path -Leaf $resolvedFixture) -notmatch '^kite env regression [0-9a-f]{32}$') {
        throw 'Refusing to remove an unexpected fixture directory.'
    }
    if (Test-Path -LiteralPath $resolvedFixture) { Remove-Item -LiteralPath $resolvedFixture -Recurse -Force }
}
