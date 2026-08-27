[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BaseUri,
    [Parameter(Mandatory = $true)][string]$TokenFile,
    [Parameter(Mandatory = $true)][string]$ExpectedTarget
)

$ErrorActionPreference = 'Stop'
$base = $BaseUri.TrimEnd('/')
$token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth = @{ Authorization = "Bearer $token" }

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 8 security conformance failed: $Message" }
}

function Invoke-Probe([string]$Method, [string]$Path, [hashtable]$Headers = $auth, [object]$Body) {
    $parameters = @{ Uri = "$base$Path"; Method = $Method; Headers = $Headers; SkipHttpErrorCheck = $true }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    $response = Invoke-WebRequest @parameters
    [pscustomobject]@{
        Status = [int]$response.StatusCode
        Json = $(if ($response.Content) { $response.Content | ConvertFrom-Json } else { $null })
    }
}

$unauthorized = Invoke-Probe GET '/v0/session' @{}
Assert-True ($unauthorized.Status -eq 401) 'missing token must fail'
$wrongHost = Invoke-Probe GET '/v0/session' @{ Authorization = "Bearer $token"; Host = 'example.com' }
Assert-True ($wrongHost.Status -eq 403 -and $wrongHost.Json.error -eq 'HOST_REJECTED') 'non-loopback Host must fail'
$wrongOrigin = Invoke-Probe GET '/v0/session' @{ Authorization = "Bearer $token"; Origin = 'https://example.com' }
Assert-True ($wrongOrigin.Status -eq 403 -and $wrongOrigin.Json.error -eq 'ORIGIN_REJECTED') 'non-loopback Origin must fail'

$context = (Invoke-Probe GET '/v0/security/context').Json
Assert-True ($context.bindAddress -eq '127.0.0.1') 'V1 must be loopback-only'
Assert-True ($context.principalId -match '^token:' -and $context.principalLifecycle -eq 'runtime_token_lifetime') 'principal lifecycle must be explicit'
Assert-True ($context.grantedScopes -contains 'command') 'current-player command scope must be explicit'

Start-Sleep -Seconds 3
$handler = [Net.Http.SocketsHttpHandler]::new()
$handler.MaxConnectionsPerServer = 32
$client = [Net.Http.HttpClient]::new($handler)
try {
    $requests = @()
    for ($index = 0; $index -lt 20; $index++) {
        $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get, "$base/v0/capture")
        $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
        $requests += $client.SendAsync($request)
    }
    [Threading.Tasks.Task]::WhenAll([Threading.Tasks.Task[]]$requests).GetAwaiter().GetResult()
    $statuses = @($requests | ForEach-Object { [int]$_.Result.StatusCode })
    Assert-True (($statuses | Where-Object { $_ -eq 429 }).Count -gt 0) 'principal capture budget must reject a burst across connections'
    foreach ($task in $requests) { $task.Result.Dispose() }
}
finally {
    $client.Dispose()
    $handler.Dispose()
}

$largeBody = 'x' * (1024 * 1024 + 1)
$oversized = Invoke-WebRequest -Uri "$base/v0/assert" -Method Post -Headers $auth `
    -ContentType 'application/json' -Body $largeBody -SkipHttpErrorCheck
Assert-True ([int]$oversized.StatusCode -in @(400,413)) 'body above 1 MiB must fail before dispatch'

$operationIds = @()
for ($index = 0; $index -lt 17; $index++) {
    $started = Invoke-Probe POST '/v0/operations/wait/screen' -Body @{
        classContains = "security-never-$index-$([guid]::NewGuid())"; timeoutMs = 30000
    }
    if ($started.Status -eq 200) { $operationIds += $started.Json.operationId }
    else {
        Assert-True ($started.Status -eq 429 -and $started.Json.error -eq 'TOO_MANY_OPERATIONS') '17th active operation must be bounded'
        break
    }
}
Assert-True ($operationIds.Count -eq 16) 'exactly 16 active operations may be retained'
foreach ($operationId in $operationIds) { [void](Invoke-Probe DELETE "/v0/operations/$operationId") }

$audit = (Invoke-Probe GET '/v0/audit?limit=64').Json
$correlated = @($audit.entries | Where-Object {
    -not [string]::IsNullOrWhiteSpace($_.principalId) -and
    -not [string]::IsNullOrWhiteSpace($_.connectionId)
})
Assert-True ($correlated.Count -gt 0) 'audit must correlate principal and connection'

[pscustomobject]@{
    Target = $ExpectedTarget
    Authentication = 'PASS'
    HostOrigin = 'PASS'
    Principal = 'PASS'
    RequestBodyBudget = 'PASS'
    ExpensiveRateBudget = 'PASS'
    ConcurrentOperations = 'PASS'
    AuditCorrelation = 'PASS'
    Result = 'PASS'
}
