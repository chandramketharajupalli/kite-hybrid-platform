# Populate this PowerShell process for host Java from resolved local Compose values.
# No services are started and no configuration values or credentials are printed.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $repositoryRoot '.env'
if (-not (Test-Path -LiteralPath $environmentFile -PathType Leaf)) {
    throw 'Create .env from .env.example and set local DB_PASSWORD and REDIS_PASSWORD first.'
}
if (-not (Get-Command docker -CommandType Application -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI is unavailable. Install/configure Docker Desktop before using this helper.'
}

$kiteNames = @('KITE_REST_ENABLED', 'KITE_API_KEY', 'KITE_API_SECRET', 'KITE_REDIRECT_URL',
    'KITE_TOKEN_ENCRYPTION_KEY')
$sourceNames = @('DB_NAME', 'DB_USER', 'DB_PASSWORD', 'REDIS_PASSWORD') + $kiteNames
$previousSourceValues = @{}
foreach ($name in $sourceNames) {
    $previousSourceValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    # Compose owns dotenv quoting/interpolation; never evaluate .env as PowerShell.
    # Shell variables override --env-file in Compose. Hide this helper's managed
    # source values while resolving .env so repeated loads cannot retain stale keys.
    # Capture stdout and suppress stderr because resolved configuration contains secrets.
    try {
        foreach ($name in $sourceNames) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
        # Windows PowerShell 5.1 treats native stderr as errors, including warnings.
        $ErrorActionPreference = 'Continue'
        $composeJson = & docker compose --project-directory $repositoryRoot `
            --file (Join-Path $repositoryRoot 'docker-compose.yml') `
            --env-file $environmentFile config --format json 2>$null
        $composeExit = $LASTEXITCODE
        $composeEnvironment = & docker compose --project-directory $repositoryRoot `
            --file (Join-Path $repositoryRoot 'docker-compose.yml') `
            --env-file $environmentFile config --environment 2>$null
        $environmentExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = 'Stop'
        foreach ($name in $sourceNames) {
            [Environment]::SetEnvironmentVariable($name, $previousSourceValues[$name], 'Process')
        }
    }
    if ($composeExit -ne 0 -or $environmentExit -ne 0) { throw 'Compose configuration failed.' }
    $configuration = ($composeJson -join "`n") | ConvertFrom-Json
    $postgres = $configuration.services.postgres.environment
    $redisPassword = [string]$configuration.services.redis.environment.REDISCLI_AUTH
    $database = [string]$postgres.POSTGRES_DB
    $kiteEnvironment = @{}
    foreach ($entry in $composeEnvironment) {
        if ($entry -match '^(KITE_REST_ENABLED|KITE_API_KEY|KITE_API_SECRET|KITE_REDIRECT_URL|KITE_TOKEN_ENCRYPTION_KEY)=(.*)$') {
            $kiteEnvironment[$Matches[1]] = $Matches[2]
        }
    }
    if ($kiteEnvironment.ContainsKey('KITE_REST_ENABLED') -and
        $kiteEnvironment['KITE_REST_ENABLED'] -notin @('true', 'false')) {
        throw 'Invalid Kite REST setting.'
    }
    if ($database -notmatch '^[A-Za-z_][A-Za-z0-9_]{0,62}$' -or
        [string]::IsNullOrWhiteSpace([string]$postgres.POSTGRES_USER) -or
        [string]::IsNullOrEmpty([string]$postgres.POSTGRES_PASSWORD) -or
        [string]::IsNullOrEmpty($redisPassword)) {
        throw 'Invalid development infrastructure configuration.'
    }
} catch {
    # Do not propagate parser/native errors that might include resolved values.
    throw 'Cannot load local Compose configuration. Check Docker Compose and the required .env variables. DB_NAME must be 1-63 letters, digits or underscores and cannot start with a digit.'
}

$env:DB_URL = "jdbc:postgresql://localhost:5432/$database"
$env:DB_USER = [string]$postgres.POSTGRES_USER
$env:DB_PASSWORD = [string]$postgres.POSTGRES_PASSWORD
$env:REDIS_HOST = 'localhost'
$env:REDIS_PORT = '6379'
$env:REDIS_PASSWORD = $redisPassword
$env:SPRING_PROFILES_ACTIVE = 'development'
$env:TRADING_MODE = 'PAPER'
$env:ENABLE_LIVE_TRADING = 'false'
$env:EMERGENCY_STOP = 'true'
foreach ($name in $kiteNames) {
    # Missing and explicitly empty values both clear any previously loaded credential.
    $value = if ($kiteEnvironment.ContainsKey($name)) { $kiteEnvironment[$name] } else { $null }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
if (-not $kiteEnvironment.ContainsKey('KITE_REST_ENABLED')) { $env:KITE_REST_ENABLED = 'false' }
Write-Output 'Local infrastructure environment loaded for this PowerShell process; trading halted, Kite settings loaded.'
