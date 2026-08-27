[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUri,

    [Parameter(Mandatory = $true)]
    [string]$TokenFile
)

$ErrorActionPreference = 'Stop'
$base = $BaseUri.TrimEnd('/')
$token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
$headers = @{ Authorization = "Bearer $token" }

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw "Phase 2 scope assertion failed: $Message"
    }
}

function Invoke-Status {
    param([string]$Path)
    Invoke-WebRequest -Uri "$base$Path" -Headers $headers -SkipHttpErrorCheck
}

$read = Invoke-Status -Path '/v0/session'
Assert-True ($read.StatusCode -eq 200) 'read scope must remain available'

foreach ($path in @('/v0/ui/tree', '/v0/capture', '/v0/control/status', '/v0/trace')) {
    $denied = Invoke-Status -Path $path
    Assert-True ($denied.StatusCode -eq 403) "$path must be denied under read-only scope"
    $error = $denied.Content | ConvertFrom-Json
    Assert-True ($error.error -eq 'SCOPE_DENIED') "$path must return typed SCOPE_DENIED"
}

[pscustomobject]@{
    Granted = 'read'
    UiDenied = 'PASS'
    CaptureDenied = 'PASS'
    ControlDenied = 'PASS'
    DiagnosticsDenied = 'PASS'
    Result = 'PASS'
}
