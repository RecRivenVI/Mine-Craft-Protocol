[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUri,

    [Parameter(Mandatory = $true)]
    [string]$TokenFile,

    [Parameter(Mandatory = $true)]
    [string]$ExpectedTarget,

    [ValidateSet('opengl', 'vulkan')]
    [string]$ExpectedBackend = 'opengl',

    [switch]$RequireWorldAuthority
)

$ErrorActionPreference = 'Stop'
$base = $BaseUri.TrimEnd('/')
$token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth = @{ Authorization = "Bearer $token" }

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw "Phase 4 conformance assertion failed: $Message"
    }
}

function Invoke-Json {
    param(
        [ValidateSet('GET', 'POST', 'DELETE')]
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = $auth,
        [object]$Body
    )
    $parameters = @{
        Uri = "$base$Path"
        Method = $Method
        Headers = $Headers
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    Invoke-RestMethod @parameters
}

function Invoke-Error {
    param([string]$Method, [string]$Path)
    $response = Invoke-WebRequest -Uri "$base$Path" -Method $Method -Headers $auth -SkipHttpErrorCheck
    [pscustomobject]@{
        Status = [int]$response.StatusCode
        Json = $response.Content | ConvertFrom-Json
    }
}

function Wait-Operation {
    param([string]$OperationId, [int]$TimeoutSeconds = 65)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        Start-Sleep -Milliseconds 100
        $operation = Invoke-Json -Method GET -Path "/v0/operations/$OperationId"
    } while ($operation.status -eq 'running' -and [DateTime]::UtcNow -lt $deadline)
    Assert-True ($operation.status -ne 'running') "operation $OperationId must finish"
    return $operation
}

function Acquire-Lease {
    Invoke-Json -Method POST -Path '/v0/control/emergency-release' | Out-Null
    $lease = Invoke-Json -Method POST -Path '/v0/control/acquire' -Body @{ ttlMs = 60000 }
    @{
        Authorization = "Bearer $token"
        'X-MCP-Control-Lease' = $lease.leaseId
    }
}

$session = Invoke-Json -Method GET -Path '/v0/session'
Assert-True ($session.target -eq $ExpectedTarget) "target must be $ExpectedTarget"
if ($session.screenClass -match 'AccessibilityOnboardingScreen') {
    $onboardingLease = Acquire-Lease
    Invoke-Json -Method POST -Path '/v0/ui/action' -Headers $onboardingLease -Body @{
        action = 'click'
        selector = @{ role = 'button'; label = 'Continue' }
    } | Out-Null
    Invoke-Json -Method POST -Path '/v0/wait/until' -Body @{
        condition = @{ type = 'screen'; classContains = 'TitleScreen' }
        timeoutMs = 5000
    } | Out-Null
    $session = Invoke-Json -Method GET -Path '/v0/session'
}
Assert-True ($session.screenClass -match 'TitleScreen') 'Phase 4 conformance must start at the title screen'

$unavailableServer = Invoke-Error -Method GET -Path '/v0/server/player'
Assert-True ($unavailableServer.Status -eq 409) 'server authority must be unavailable outside an integrated world'
Assert-True ($unavailableServer.Json.error -eq 'SERVER_AUTHORITATIVE_UNAVAILABLE') 'server authority failure must be typed'

$clientAtTitle = Invoke-Json -Method GET -Path '/v0/player'
Assert-True (-not [bool]$clientAtTitle.available) 'client player must be unavailable at title'
Assert-True ($clientAtTitle.dataSource -eq 'LIVE') 'unavailable client state must still declare LIVE source'
Assert-True (-not [bool]$clientAtTitle.storageAccessed) 'client query must not touch storage'

$providers = Invoke-Json -Method GET -Path '/v0/providers'
Assert-True ($providers.dataSource -eq 'LIVE') 'Provider SPI must be LIVE-only'
Assert-True (-not [bool]$providers.persistentStorageAvailable) 'Provider SPI must not expose persistent storage'
Assert-True ($null -ne ($providers.providers | Where-Object id -eq 'minecraft:server/player')) 'server player provider must exist'
$echoDescriptor = $providers.providers | Where-Object id -eq 'minecraft_protocol_probe:echo'
Assert-True ($null -ne $echoDescriptor) 'registered Mod provider must be discoverable'
Assert-True ([bool]$echoDescriptor.thirdParty) 'registered Mod provider must be marked third-party'
Assert-True ($echoDescriptor.trust -eq 'untrusted_mod_provider') 'registered Mod provider must be untrusted data'

$echo = Invoke-Json -Method POST -Path '/v0/providers/read' -Body @{
    providerId = 'minecraft_protocol_probe:echo'
    query = @{ message = 'phase4-provider' }
}
Assert-True ($echo.data.echo.message -eq 'phase4-provider') 'registered provider read must execute'
Assert-True ($echo.dataSource -eq 'LIVE' -and -not [bool]$echo.storageAccessed) 'registered provider must remain LIVE-only'
Assert-True (-not [string]::IsNullOrWhiteSpace($echo.querySnapshotId)) 'provider read must have querySnapshotId'

$captureInfo = Invoke-Json -Method GET -Path '/v0/capture/info'
Assert-True ($captureInfo.backend -eq $ExpectedBackend) "capture backend must be $ExpectedBackend"
Assert-True ($captureInfo.mode -eq 'COMPOSITE' -and $captureInfo.format -eq 'PNG') 'capture contract must be Composite PNG'
Assert-True ([bool]$captureInfo.inputConcurrent) 'capture must declare input concurrency'

$titleFrame = Invoke-Json -Method POST -Path '/v0/state/frames' -Body @{
    reads = @(
        @{ providerId = 'minecraft:client/player' },
        @{ providerId = 'minecraft:capture/info' },
        @{ providerId = 'minecraft_protocol_probe:echo'; query = @{ frame = 'title' } }
    )
}
Assert-True ($titleFrame.consistency -eq 'coordinated_best_effort') 'State Frame consistency must be explicit'
Assert-True ($titleFrame.dataSource -eq 'LIVE' -and -not [bool]$titleFrame.storageAccessed) 'State Frame must not touch storage'
Assert-True ($titleFrame.reads.Count -eq 3) 'title State Frame must include requested reads'

$leaseHeaders = Acquire-Lease
$pipeline = Invoke-Json -Method POST -Path '/v0/pipelines' -Headers $leaseHeaders -Body @{
    timeoutMs = 10000
    steps = @(
        @{ type = 'key'; key = 87; scanCode = 17; action = 1 },
        @{ type = 'delay'; durationMs = 1200 },
        @{ type = 'mouse.move'; x = 100; y = 100 },
        @{ type = 'mouse.move'; x = 120; y = 110 },
        @{ type = 'key'; key = 87; scanCode = 17; action = 0 }
    )
}
$heldDeadline = [DateTime]::UtcNow.AddSeconds(3)
do {
    Start-Sleep -Milliseconds 25
    $held = Invoke-Json -Method GET -Path '/v0/input/state'
} while ($held.pressedKeyCount -lt 1 -and [DateTime]::UtcNow -lt $heldDeadline)
Assert-True ($held.pressedKeys -contains 87) 'W must be held before parallel captures begin'

$httpClient = [System.Net.Http.HttpClient]::new()
$httpClient.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
try {
    $captureTasks = @()
    foreach ($index in 1..8) {
        $captureTasks += $httpClient.GetAsync("$base/v0/capture")
    }
    $pipelineResult = Wait-Operation -OperationId $pipeline.operationId -TimeoutSeconds 15
    Assert-True ($pipelineResult.status -eq 'completed') 'input Pipeline must complete while captures are active'
    [void][System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]]$captureTasks, 15000)
    foreach ($task in $captureTasks) {
        $response = $task.Result
        Assert-True ($response.IsSuccessStatusCode) 'parallel capture request must succeed'
        $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        Assert-True ($bytes.Length -gt 8) 'parallel capture must contain PNG bytes'
        Assert-True ([BitConverter]::ToString($bytes[0..7]) -eq '89-50-4E-47-0D-0A-1A-0A') 'parallel capture must have PNG signature'
        $response.Dispose()
    }
}
finally {
    $httpClient.Dispose()
}
$cleared = Invoke-Json -Method GET -Path '/v0/input/state'
Assert-True ($cleared.pressedKeyCount -eq 0 -and $cleared.pressedButtonCount -eq 0) 'capture/input concurrency test must finish with clean input'
$verifiedCapture = Invoke-Json -Method GET -Path '/v0/capture/info'
Assert-True ([bool]$verifiedCapture.captureVerified) 'capture info must become runtime verified'

$authorityResult = 'NOT_REQUESTED'
if ($RequireWorldAuthority) {
    $leaseHeaders = Acquire-Lease
    $worldPipeline = Invoke-Json -Method POST -Path '/v0/pipelines' -Headers $leaseHeaders -Body @{
        timeoutMs = 55000
        steps = @(
            @{ type = 'ui.action'; action = 'click'; selector = @{ role = 'button'; label = 'Singleplayer' } },
            @{ type = 'wait.until'; timeoutMs = 5000; condition = @{ type = 'screen'; classContains = 'SelectWorldScreen' } },
            @{ type = 'mouse.click'; x = 200; y = 75 },
            @{ type = 'delay'; durationMs = 250 },
            @{ type = 'ui.action'; action = 'click'; selector = @{ role = 'button'; label = 'Play Selected World' } },
            @{ type = 'wait.until'; timeoutMs = 30000; condition = @{ type = 'screen'; open = $false } }
        )
    }
    $worldOperation = Wait-Operation -OperationId $worldPipeline.operationId -TimeoutSeconds 65
    Assert-True ($worldOperation.status -eq 'completed') 'Pipeline must enter the integrated world'

    $clientPlayer = Invoke-Json -Method GET -Path '/v0/player'
    $serverPlayer = Invoke-Json -Method GET -Path '/v0/server/player'
    Assert-True ($clientPlayer.perspective -eq 'client_known') 'client player perspective must be explicit'
    Assert-True ($serverPlayer.perspective -eq 'server_authoritative_live') 'server player perspective must be explicit'
    Assert-True ($clientPlayer.uuid -eq $serverPlayer.uuid) 'client and server snapshots must identify the same player'
    Assert-True ($serverPlayer.thread -match 'Server thread') 'server snapshot must be produced on Server thread'
    Assert-True (-not [bool]$clientPlayer.storageAccessed -and -not [bool]$serverPlayer.storageAccessed) 'player reads must not touch storage'

    $x = [math]::Floor($serverPlayer.x)
    $y = [math]::Floor($serverPlayer.y) - 1
    $z = [math]::Floor($serverPlayer.z)
    $clientBlock = Invoke-Json -Method GET -Path "/v0/world/block?x=$x&y=$y&z=$z"
    $serverBlock = Invoke-Json -Method GET -Path "/v0/server/world/block?x=$x&y=$y&z=$z"
    Assert-True ([bool]$clientBlock.available -and [bool]$serverBlock.available) 'loaded block must be available in both views'
    Assert-True ($clientBlock.block -eq $serverBlock.block) 'client and server loaded block IDs must agree'
    Assert-True ($serverBlock.authority -eq 'server_authoritative') 'server block authority must be explicit'
    Assert-True (-not [bool]$serverBlock.chunkLoadRequested) 'server block query must not request chunk loading'

    $farX = $x + 1000000
    $farBlock = Invoke-Json -Method GET -Path "/v0/server/world/block?x=$farX&y=$y&z=$z"
    Assert-True (-not [bool]$farBlock.available) 'unloaded far block must remain unavailable'
    Assert-True ($farBlock.reason -eq 'chunk_not_loaded') 'unloaded block must report chunk_not_loaded'
    Assert-True (-not [bool]$farBlock.chunkLoadRequested -and -not [bool]$farBlock.storageAccessed) 'unloaded query must neither load chunk nor read storage'

    $clientEntities = Invoke-Json -Method GET -Path '/v0/world/entities?radius=32'
    $serverEntities = Invoke-Json -Method GET -Path '/v0/server/world/entities?radius=32'
    Assert-True ($clientEntities.source -eq 'client_live') 'client entity source must be explicit'
    Assert-True ($serverEntities.source -eq 'integrated_server_live') 'server entity source must be explicit'

    $frame = Invoke-Json -Method POST -Path '/v0/state/frames' -Body @{
        reads = @(
            @{ providerId = 'minecraft:client/player' },
            @{ providerId = 'minecraft:server/player' },
            @{ providerId = 'minecraft:client/world/block'; query = @{ x = $x; y = $y; z = $z } },
            @{ providerId = 'minecraft:server/world/block'; query = @{ x = $x; y = $y; z = $z } },
            @{ providerId = 'minecraft:client/world/entities'; query = @{ radius = 32 } },
            @{ providerId = 'minecraft:server/world/entities'; query = @{ radius = 32 } }
        )
    }
    Assert-True ($frame.reads.Count -eq 6) 'world State Frame must include every requested view'
    Assert-True (-not [string]::IsNullOrWhiteSpace($frame.stateFrameId)) 'State Frame must have stable ID'
    Assert-True (-not [bool]$frame.storageAccessed) 'world State Frame must not access storage'
    foreach ($read in $frame.reads) {
        Assert-True ($read.dataSource -eq 'LIVE') 'every State Frame provider result must be LIVE'
        Assert-True (-not [bool]$read.storageAccessed) 'every State Frame provider result must avoid storage'
        Assert-True (-not [string]::IsNullOrWhiteSpace($read.querySnapshotId)) 'every State Frame read must have querySnapshotId'
    }
    $authorityResult = 'PASS'
}

[pscustomobject]@{
    Target = $ExpectedTarget
    Backend = $ExpectedBackend
    ClientLiveBoundary = 'PASS'
    ProviderReadSpi = 'PASS'
    StateFrame = 'PASS'
    CaptureInputParallel = 'PASS'
    PersistentStorageIsolation = 'PASS'
    IntegratedServerAuthority = $authorityResult
    Result = 'PASS'
}
