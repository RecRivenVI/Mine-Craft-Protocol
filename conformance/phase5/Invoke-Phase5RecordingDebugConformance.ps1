[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUri,

    [Parameter(Mandatory = $true)]
    [string]$TokenFile,

    [Parameter(Mandatory = $true)]
    [string]$ExpectedTarget
)

$ErrorActionPreference = 'Stop'
$base = $BaseUri.TrimEnd('/')
$token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth = @{ Authorization = "Bearer $token" }

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Phase 5 conformance assertion failed: $Message" }
}

function Invoke-Json {
    param(
        [ValidateSet('GET', 'POST', 'DELETE')]
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = $auth,
        [object]$Body
    )
    $parameters = @{ Uri = "$base$Path"; Method = $Method; Headers = $Headers }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    Invoke-RestMethod @parameters
}

function Invoke-ErrorResponse {
    param([string]$Path, [hashtable]$Headers, [object]$Body)
    $response = Invoke-WebRequest -Uri "$base$Path" -Method Post -Headers $Headers `
        -ContentType 'application/json' -Body ($Body | ConvertTo-Json -Depth 20 -Compress) -SkipHttpErrorCheck
    [pscustomobject]@{ Status = [int]$response.StatusCode; Json = $response.Content | ConvertFrom-Json }
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

function Read-ZipText {
    param([System.IO.Compression.ZipArchive]$Zip, [string]$Name)
    $entry = $Zip.GetEntry($Name)
    Assert-True ($null -ne $entry) "Artifact must contain $Name"
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

$session = Invoke-Json -Method GET -Path '/v0/session'
Assert-True ($session.target -eq $ExpectedTarget) "target must be $ExpectedTarget"
if ($session.screenClass -match 'AccessibilityOnboardingScreen') {
    $onboardingLease = Acquire-Lease
    Invoke-Json -Method POST -Path '/v0/ui/action' -Headers $onboardingLease -Body @{
        action = 'click'; selector = @{ role = 'button'; label = 'Continue' }
    } | Out-Null
    Invoke-Json -Method POST -Path '/v0/wait/until' -Body @{
        condition = @{ type = 'screen'; classContains = 'TitleScreen' }; timeoutMs = 5000
    } | Out-Null
    $session = Invoke-Json -Method GET -Path '/v0/session'
}
Assert-True ($session.screenClass -match 'TitleScreen') 'Phase 5 conformance must start at title'
$security = Invoke-Json -Method GET -Path '/v0/security/context'
Assert-True ($security.grantedScopes -contains 'fixture') 'fixture scope must be explicitly enabled for Phase 5 conformance'
Assert-True ($security.grantedScopes -contains 'debug') 'debug scope must be explicitly enabled for Phase 5 conformance'

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
$worldResult = Wait-Operation -OperationId $worldPipeline.operationId
Assert-True ($worldResult.status -eq 'completed') 'world Arrange pipeline must complete'

$fingerprint = Invoke-Json -Method GET -Path '/v0/world/fingerprint'
Assert-True (-not [string]::IsNullOrWhiteSpace($fingerprint.worldFingerprint)) 'world fingerprint must exist'

$withoutArm = Invoke-ErrorResponse -Path '/v0/debug/player/health' -Headers $leaseHeaders -Body @{ health = 20 }
Assert-True ($withoutArm.Status -eq 409 -and $withoutArm.Json.error -eq 'DEBUG_ARM_REQUIRED') 'Debug mutation must fail without Arm'

$wrongFingerprint = Invoke-ErrorResponse -Path '/v0/debug/arm' -Headers $leaseHeaders -Body @{
    worldFingerprint = "wrong-$([guid]::NewGuid())"
    ttlMs = 30000
}
Assert-True ($wrongFingerprint.Status -eq 409 -and $wrongFingerprint.Json.error -eq 'WORLD_FINGERPRINT_MISMATCH') 'Debug Arm must bind world fingerprint'

$arm = Invoke-Json -Method POST -Path '/v0/debug/arm' -Headers $leaseHeaders -Body @{
    worldFingerprint = $fingerprint.worldFingerprint
    ttlMs = 30000
}
$debugHeaders = @{
    Authorization = "Bearer $token"
    'X-MCP-Control-Lease' = $leaseHeaders.'X-MCP-Control-Lease'
    'X-MCP-Debug-Arm' = $arm.debugArmId
}

$recording = Invoke-Json -Method POST -Path '/v0/recordings' -Body @{
    intervalMs = 100
    durationMs = 2500
    maxSamples = 20
    captureFrames = $true
    stateReads = @(
        @{ providerId = 'minecraft:client/player' },
        @{ providerId = 'minecraft:server/player' },
        @{ providerId = 'minecraft:capture/info' }
    )
    contactSheet = @{ enabled = $true; columns = 4; cellWidth = 160; cellHeight = 90; spacing = 2 }
}
Assert-True ($recording.status -eq 'recording') 'Recording Session must start'
Assert-True ($recording.writerQueueCapacity -eq 64) 'Recording writer queue must be bounded'

$player = Invoke-Json -Method GET -Path '/v0/server/player'
$fixture = Invoke-Json -Method POST -Path '/v0/fixture/player/teleport' -Headers $leaseHeaders -Body @{
    x = $player.x; y = $player.y; z = $player.z
}
Assert-True ($fixture.mode -eq 'FIXTURE' -and [bool]$fixture.evidenceContaminated) 'Fixture must mark contamination'

$health = Invoke-Json -Method POST -Path '/v0/debug/player/health' -Headers $debugHeaders -Body @{
    health = $player.health
}
Assert-True ($health.mode -eq 'DEBUG_PRIVILEGED') 'Health mutation must be typed DEBUG_PRIVILEGED'
Assert-True ($health.mechanism -eq 'DIRECT_MUTATION') 'Health mutation mechanism must be explicit'

$x = [math]::Floor($player.x)
$y = [math]::Floor($player.y) - 1
$z = [math]::Floor($player.z)
$beforeBlock = Invoke-Json -Method GET -Path "/v0/server/world/block?x=$x&y=$y&z=$z"
$blockMutation = Invoke-Json -Method POST -Path '/v0/debug/world/block' -Headers $debugHeaders -Body @{
    x = $x; y = $y; z = $z
    blockId = $beforeBlock.block
    expectedBlockId = $beforeBlock.block
}
Assert-True ($blockMutation.before -eq $blockMutation.after) 'same-state Debug block probe must avoid material world change'
Assert-True ([bool]$blockMutation.evidenceContaminated) 'Debug block mutation must contaminate evidence'

$inputPipeline = Invoke-Json -Method POST -Path '/v0/pipelines' -Headers $leaseHeaders -Body @{
    timeoutMs = 5000
    steps = @(
        @{ type = 'mouse.move'; x = 100; y = 100 },
        @{ type = 'delay'; durationMs = 500 },
        @{ type = 'mouse.move'; x = 140; y = 120 },
        @{ type = 'key.chord'; holdMs = 100; keys = @(@{ key = 340; scanCode = 42 }, @{ key = 341; scanCode = 29 }) }
    )
}
$inputResult = Wait-Operation -OperationId $inputPipeline.operationId -TimeoutSeconds 10
Assert-True ($inputResult.status -eq 'completed') 'input Pipeline must complete during Recording'

$recordingDeadline = [DateTime]::UtcNow.AddSeconds(20)
do {
    Start-Sleep -Milliseconds 200
    $recordingStatus = Invoke-Json -Method GET -Path "/v0/recordings/$($recording.recordingId)"
} while ($recordingStatus.status -notin @('completed', 'failed') -and [DateTime]::UtcNow -lt $recordingDeadline)
Assert-True ($recordingStatus.status -eq 'completed') 'Recording must finalize successfully'
Assert-True ($recordingStatus.writtenFrames -gt 0) 'Recording must persist frames'
Assert-True ($recordingStatus.writtenStates -gt 0) 'Recording must persist State Frames'
Assert-True ([bool]$recordingStatus.evidenceContaminated) 'Recording must propagate Fixture/Debug contamination'
Assert-True ([bool]$recordingStatus.artifactReady) 'Artifact Bundle must become ready'

$artifactResponse = Invoke-WebRequest -Uri "$base/v0/recordings/$($recording.recordingId)/artifact" -Headers $auth
$artifactBytes = [byte[]]$artifactResponse.Content
Assert-True ($artifactBytes.Length -gt 1024) 'Artifact ZIP must contain data'
$memory = [IO.MemoryStream]::new($artifactBytes)
$zip = [IO.Compression.ZipArchive]::new($memory, [IO.Compression.ZipArchiveMode]::Read)
try {
    $names = @($zip.Entries.FullName)
    foreach ($required in @(
        'manifest.json', 'frame-index.json', 'timeline/timeline.ndjson', 'canonical/store-v0.bin',
        'derivatives/contact-sheet.png', 'checksums.json'
    )) {
        Assert-True ($names -contains $required) "Artifact must contain $required"
    }
    Assert-True (($names | Where-Object { $_ -match '^frames/.+\.png$' }).Count -gt 0) 'Artifact must contain frame PNGs'
    Assert-True (($names | Where-Object { $_ -match '^state/.+\.json$' }).Count -gt 0) 'Artifact must contain State Frames'

    $manifest = Read-ZipText -Zip $zip -Name 'manifest.json' | ConvertFrom-Json
    Assert-True ($manifest.artifactVersion -eq 'mcp-artifact-v0') 'Artifact version must be explicit'
    Assert-True ($manifest.schemaVersion -eq '0.0.1-phase5') 'Artifact schema must identify Phase 5'
    Assert-True (-not [bool]$manifest.canonicalStore.frozen) 'experimental binary codec must not be frozen'
    Assert-True ([bool]$manifest.evidenceContaminated) 'Artifact manifest must preserve contamination'
    Assert-True ($manifest.backpressurePolicy -eq 'drop_sample_and_record_gap') 'backpressure policy must be explicit'

    $timeline = Read-ZipText -Zip $zip -Name 'timeline/timeline.ndjson'
    Assert-True ($timeline -match 'evidence\.contamination') 'human-readable timeline must contain contamination events'

    $canonical = $zip.GetEntry('canonical/store-v0.bin').Open()
    try {
        $magic = New-Object byte[] 4
        [void]$canonical.Read($magic, 0, 4)
        Assert-True ([BitConverter]::ToString($magic) -eq '4D-43-50-52') 'canonical store magic must be MCPR'
    }
    finally { $canonical.Dispose() }
}
finally {
    $zip.Dispose()
    $memory.Dispose()
}

$disarmed = Invoke-Json -Method POST -Path '/v0/debug/disarm' -Headers $leaseHeaders
Assert-True ($disarmed.status -eq 'disarmed') 'Debug Arm must disarm'
$afterDisarm = Invoke-ErrorResponse -Path '/v0/debug/player/health' -Headers $debugHeaders -Body @{ health = $player.health }
Assert-True ($afterDisarm.Status -eq 409 -and $afterDisarm.Json.error -eq 'DEBUG_ARM_REQUIRED') 'Debug mutation must fail after disarm'

$shortArm = Invoke-Json -Method POST -Path '/v0/debug/arm' -Headers $leaseHeaders -Body @{
    worldFingerprint = $fingerprint.worldFingerprint
    ttlMs = 1000
}
$shortHeaders = @{
    Authorization = "Bearer $token"
    'X-MCP-Control-Lease' = $leaseHeaders.'X-MCP-Control-Lease'
    'X-MCP-Debug-Arm' = $shortArm.debugArmId
}
Start-Sleep -Milliseconds 1200
$afterExpiry = Invoke-ErrorResponse -Path '/v0/debug/player/health' -Headers $shortHeaders -Body @{ health = $player.health }
Assert-True ($afterExpiry.Status -eq 409 -and $afterExpiry.Json.error -eq 'DEBUG_ARM_REQUIRED') 'expired Debug Arm must fail closed'

[pscustomobject]@{
    Target = $ExpectedTarget
    RecordingFrames = $recordingStatus.writtenFrames
    RecordingStates = $recordingStatus.writtenStates
    RecordingGaps = $recordingStatus.gaps
    ArtifactBytes = $artifactBytes.Length
    ContactSheet = 'PASS'
    CanonicalBinary = 'PASS'
    ProviderStateTrack = 'PASS'
    FixtureContamination = 'PASS'
    DebugArm = 'PASS'
    TypedDebug = 'PASS'
    Result = 'PASS'
}
