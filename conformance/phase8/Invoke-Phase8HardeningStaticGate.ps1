[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 8 hardening static gate failed: $Message" }
}

$targets = @(
    @{ Name = '1.20.1-forge'; Source = 'src\main\java'; Runtime = 'ForgeProbeRuntime.java' },
    @{ Name = '1.21.1-neoforge'; Source = 'src\main\java'; Runtime = 'NeoForgeProbeRuntime.java' },
    @{ Name = '26.1.2-neoforge'; Source = 'src\main\java'; Runtime = 'NeoForgeProbeRuntime.java' },
    @{ Name = '26.2-neoforge'; Source = 'src\main\java'; Runtime = 'NeoForgeProbeRuntime.java' },
    @{ Name = '26.2-fabric'; Source = 'src\client\java'; Runtime = 'FabricProbeRuntime.java' }
)

$normalizedHashes = @{}
foreach ($target in $targets) {
    $runtimeDirectory = Join-Path $root "versions\$($target.Name)\$($target.Source)\io\github\recrivenvi\minecraftprotocol\probe\runtime"
    $automation = Get-Content -LiteralPath (Join-Path $runtimeDirectory 'AutomationEngine.java') -Raw
    $protocol = Get-Content -LiteralPath (Join-Path $runtimeDirectory 'ProtocolState.java') -Raw
    $events = Get-Content -LiteralPath (Join-Path $runtimeDirectory 'EventHub.java') -Raw
    $recording = Get-Content -LiteralPath (Join-Path $runtimeDirectory 'RecordingEngine.java') -Raw
    $transport = Get-Content -LiteralPath (Join-Path $runtimeDirectory 'ProbeTransport.java') -Raw
    $conditions = Get-Content -LiteralPath (Join-Path $runtimeDirectory 'ConditionEngine.java') -Raw
    $security = Get-Content -LiteralPath (Join-Path $runtimeDirectory 'SecurityGate.java') -Raw
    $runtime = Get-Content -LiteralPath (Join-Path $runtimeDirectory $target.Runtime) -Raw

    Assert-True ($automation -match 'cancellationRequested' -and $automation -match 'pending scheduled handles|Set<ScheduledFuture') "$($target.Name) must track pipeline cancellation and schedules"
    Assert-True ($automation -match 'stopPendingWork' -and $automation -match 'checkActive') "$($target.Name) must prevent deferred post-cancel effects"
    Assert-True ($runtime -match 'if \(result\.isDone\(\)\) return;') "$($target.Name) owner-thread queue must honor cancellation"
    Assert-True ($runtime -match 'inputDispatchSequence') "$($target.Name) must expose input side-effect sequence evidence"
    Assert-True ($runtime -match 'addShutdownHook' -and $transport -match 'closed\.compareAndSet') "$($target.Name) transport shutdown/finalization must be registered and idempotent"
    Assert-True ($protocol -match 'cancelLeaseBoundOperations\("lease_expired"\)') "$($target.Name) lease expiry must cancel pipelines"
    Assert-True ($protocol -match 'waitOperation' -and $protocol -match 'state", this\.state') "$($target.Name) operation lifecycle must be typed"

    Assert-True ($events -match 'RING_CAPACITY = 1024' -and $events -match 'CLIENT_QUEUE_CAPACITY = 128') "$($target.Name) EventHub must be bounded"
    foreach ($marker in @('event.subscribe','event.ack','event.resume','event.gap','event.resync.snapshot','fullResyncRequired')) {
        Assert-True ($events.Contains($marker)) "$($target.Name) EventHub missing $marker"
    }
    Assert-True ($transport -match '/v0/events/resync' -and $transport -match 'diagnostics/events/stress') "$($target.Name) must expose resync and bounded stress probe"

    foreach ($marker in @('MAX_SHEET_WIDTH','MAX_SHEET_HEIGHT','MAX_SHEET_PIXELS','MAX_DECODED_SOURCE_BYTES','MAX_ESTIMATED_RAW_BYTES','MAX_OUTPUT_BYTES','MAX_RECORDING_BYTES','MAX_BUNDLE_SOURCE_BYTES')) {
        Assert-True ($recording.Contains($marker)) "$($target.Name) Recording missing $marker"
    }
    Assert-True ($recording -notmatch 'Files\.readAllBytes') "$($target.Name) Recording must not heap-load an Artifact"
    Assert-True ($transport -match 'ChunkedNioFile' -and $transport -match 'HttpChunkedInput') "$($target.Name) Artifact response must stream"
    foreach ($state in @('STOPPING_CAPTURE','DRAINING_ENCODERS','FINALIZING','WRITING_MANIFEST','CLOSED')) {
        Assert-True ($recording.Contains($state)) "$($target.Name) Recording lifecycle missing $state"
    }

    foreach ($condition in @('player','block','entity','menu','inventory','event','operation')) {
        Assert-True ($conditions.Contains('"' + $condition + '"')) "$($target.Name) Wait/Assert missing $condition"
    }
    Assert-True ($security -match 'TokenBucket' -and $security -match 'MAX_CONNECTIONS' -and $security -match 'category\(method, path\)') "$($target.Name) security rate budgets missing"
    Assert-True ($runtime -match 'command\.player\.execute' -and $runtime -match 'NORMAL_NETWORK') "$($target.Name) current-player command capability missing"

    foreach ($name in @('AutomationEngine.java','ProtocolState.java','EventHub.java','ConditionEngine.java','SecurityGate.java','RecordingEngine.java')) {
        $text = (Get-Content -LiteralPath (Join-Path $runtimeDirectory $name) -Raw).TrimEnd() -replace "`r`n", "`n"
        $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text)))
        if (-not $normalizedHashes.ContainsKey($name)) { $normalizedHashes[$name] = $hash }
        else { Assert-True ($normalizedHashes[$name] -eq $hash) "$name common hardening logic drifted across Targets" }
    }
}

$client = Get-Content -LiteralPath (Join-Path $root 'companion\src\runtime-client.ts') -Raw
$server = Get-Content -LiteralPath (Join-Path $root 'companion\src\server.ts') -Raw
Assert-True ($client -notmatch 'arrayBuffer\s*\(') 'Companion must not allocate the complete Runtime response first'
Assert-True ($client -match 'content-length' -and $client -match 'getReader\(' -and $client -match 'reader\.cancel') 'Companion streaming budget enforcement missing'
foreach ($tool in @('minecraft_get_operation','minecraft_wait_operation','minecraft_cancel_operation','minecraft_execute_player_command')) {
    Assert-True ($server.Contains("registerTool('$tool'")) "Companion missing $tool"
}
Assert-True ($server -match 'z\.discriminatedUnion\(' -and $server -notmatch 'steps:\s*z\.array\(objectSchema\)') 'critical MCP input schemas must be typed'
Assert-True ($server -match 'context\.signal|cancellationSignal\(context\)') 'MCP cancellation propagation missing'

$schema = Get-Content -LiteralPath (Join-Path $root 'protocol-schema\src\main\openapi\minecraft-control-v0.json') -Raw | ConvertFrom-Json
foreach ($path in @('/v0/operations/{operationId}/wait','/v0/events/resync','/v0/command/player')) {
    Assert-True ($null -ne $schema.paths.$path) "OpenAPI missing $path"
}

[pscustomobject]@{
    Result = 'PASS'
    TargetsAudited = $targets.Count
    CommonHardeningFiles = $normalizedHashes.Count
    Cancellation = 'PASS'
    EventHub = 'PASS'
    RecordingBudgets = 'PASS'
    ArtifactStreaming = 'PASS'
    Security = 'PASS'
    TypedConditions = 'PASS'
    CompanionLifecycle = 'PASS'
}
