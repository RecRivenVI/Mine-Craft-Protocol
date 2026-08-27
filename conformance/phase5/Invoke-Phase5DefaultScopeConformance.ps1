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
    if (-not $Condition) { throw "Phase 5 default-scope assertion failed: $Message" }
}

$security = Invoke-RestMethod -Uri "$base/v0/security/context" -Headers $headers
Assert-True ($security.grantedScopes -notcontains 'debug') 'debug scope must be disabled by default'
Assert-True ($security.grantedScopes -notcontains 'fixture') 'fixture scope must be disabled by default'

foreach ($probe in @(
    @{ Path = '/v0/debug/status'; Method = 'GET'; Body = $null },
    @{ Path = '/v0/fixture/player/teleport'; Method = 'POST'; Body = '{}' }
)) {
    $parameters = @{
        Uri = "$base$($probe.Path)"
        Method = $probe.Method
        Headers = $headers
        SkipHttpErrorCheck = $true
    }
    if ($null -ne $probe.Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $probe.Body
    }
    $response = Invoke-WebRequest @parameters
    Assert-True ($response.StatusCode -eq 403) "$($probe.Path) must be denied by default"
    $error = $response.Content | ConvertFrom-Json
    Assert-True ($error.error -eq 'SCOPE_DENIED') "$($probe.Path) must return SCOPE_DENIED"
}

[pscustomobject]@{
    DebugDefault = 'DENIED'
    FixtureDefault = 'DENIED'
    Result = 'PASS'
}
