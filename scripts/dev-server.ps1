[CmdletBinding()]
param(
    [string]$EnvFile = '',
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if (-not $EnvFile) { $EnvFile = Join-Path $repoRoot 'server/.env' }
$resolvedEnvFile = if ([IO.Path]::IsPathRooted($EnvFile)) {
    [IO.Path]::GetFullPath($EnvFile)
}
else {
    [IO.Path]::GetFullPath((Join-Path $repoRoot $EnvFile))
}

if (-not (Test-Path -LiteralPath $resolvedEnvFile -PathType Leaf)) {
    throw "Environment file not found: $resolvedEnvFile"
}

$loadedNames = [Collections.Generic.List[string]]::new()
foreach ($rawLine in Get-Content -LiteralPath $resolvedEnvFile) {
    $line = $rawLine.Trim()
    if (-not $line -or $line.StartsWith('#')) { continue }

    $separator = $line.IndexOf('=')
    if ($separator -lt 1) { throw "Invalid environment entry in $resolvedEnvFile" }

    $name = $line.Substring(0, $separator).Trim()
    if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        throw "Invalid environment variable name: $name"
    }

    $value = $line.Substring($separator + 1).Trim()
    if ($value.Length -ge 2) {
        $quotedWithDouble = $value.StartsWith('"') -and $value.EndsWith('"')
        $quotedWithSingle = $value.StartsWith("'") -and $value.EndsWith("'")
        if ($quotedWithDouble -or $quotedWithSingle) {
            $value = $value.Substring(1, $value.Length - 2)
        }
    }

    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    $loadedNames.Add($name)
}

function Set-ProcessDefault([string]$Name, [string]$Value) {
    if (-not [Environment]::GetEnvironmentVariable($Name, 'Process')) {
        [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
    }
}

function New-LocalSecret {
    $bytes = [byte[]]::new(32)
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

if (-not [Environment]::GetEnvironmentVariable('OPENAI_API_KEY', 'Process')) {
    throw 'OPENAI_API_KEY is required in the local environment file.'
}

Set-ProcessDefault 'OPENMD_QUIZ_GENERATION_ENABLED' 'true'
Set-ProcessDefault 'OPENMD_QUIZ_GENERATION_MODEL' 'gpt-5.6-luna'
Set-ProcessDefault 'OPENMD_QUIZ_GENERATION_REASONING_EFFORT' 'low'
Set-ProcessDefault 'OPENMD_CORS_ALLOWED_ORIGINS' 'http://localhost:5173'
Set-ProcessDefault 'OPENMD_AUTH_BROWSER_ALLOWED_ORIGINS' 'http://localhost:5173'
Set-ProcessDefault 'OPENMD_AUTH_BROWSER_COOKIE_NAME' 'openmd_refresh'
Set-ProcessDefault 'OPENMD_AUTH_BROWSER_COOKIE_SECURE' 'false'
Set-ProcessDefault 'OPENMD_AUTH_ACCESS_TOKEN_SECRET' (New-LocalSecret)
Set-ProcessDefault 'OPENMD_AUTH_EMAIL_CODE_HMAC_SECRET' (New-LocalSecret)
Set-ProcessDefault 'OPENMD_MAIL_FROM' 'no-reply@localhost'
Set-ProcessDefault 'SPRING_MAIL_HOST' 'localhost'

Write-Host "Loaded local environment variable names: $($loadedNames -join ', ')"
Write-Host 'Quiz generation: enabled (gpt-5.6-luna, low)'
Write-Host 'Browser origin: http://localhost:5173'

if ($ValidateOnly) {
    Write-Host 'Local server environment validation passed.'
    exit 0
}

Push-Location (Join-Path $repoRoot 'server')
try {
    & .\gradlew.bat bootRun --no-daemon
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
    Pop-Location
}
