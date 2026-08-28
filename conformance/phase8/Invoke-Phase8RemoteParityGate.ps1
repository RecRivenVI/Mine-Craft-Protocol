[CmdletBinding()]
param(
    [string]$Remote = 'origin',
    [string]$Branch = 'master',
    [string]$ScratchRoot = 'D:\Workspaces\Scratches',
    [switch]$Offline,
    [switch]$SkipBuild,
    [string]$EvidenceOutput
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$scratch = (Resolve-Path -LiteralPath $ScratchRoot).Path
$remoteRef = "$Remote/$Branch"
$sourceCommit = ''
$worktreePath = ''
$failure = $null
$localGate = $null
$initialClean = $false
$finalClean = $false
$criticalHashes = [ordered]@{}
$artifactHashes = [ordered]@{}
$gateScriptBlob = ''
$gateVersion = 'phase8.2-remote-parity-v2'
$timestamp = [DateTimeOffset]::UtcNow.ToString('O')

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 8 Remote Parity assertion failed: $Message" }
}

function Read-RemoteFile([string]$RelativePath) {
    Get-Content -LiteralPath (Join-Path $worktreePath $RelativePath) -Raw
}

try {
    Push-Location $repositoryRoot
    try {
        & git fetch $Remote --prune
        if ($LASTEXITCODE -ne 0) { throw "git fetch $Remote failed" }
        $sourceCommit = (& git rev-parse $remoteRef).Trim()
        Assert-True ($sourceCommit -match '^[0-9a-f]{40}$') "Unable to resolve $remoteRef"
        $shortCommit = $sourceCommit.Substring(0, 12)
        $worktreePath = Join-Path $scratch "Mine-Craft-Protocol-Remote-Verification-$shortCommit-$PID"
        Assert-True (-not (Test-Path -LiteralPath $worktreePath)) "Temporary worktree already exists: $worktreePath"
        $resolvedScratchWithSeparator = $scratch.TrimEnd('\') + '\'
        Assert-True ($worktreePath.StartsWith($resolvedScratchWithSeparator, [StringComparison]::OrdinalIgnoreCase)) `
            'Temporary worktree escaped ScratchRoot'
        & git worktree add --quiet --detach $worktreePath $sourceCommit | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Unable to create REMOTE_VERIFY worktree' }
    }
    finally { Pop-Location }

    $initialClean = [string]::IsNullOrEmpty((& git -C $worktreePath status --porcelain | Out-String).Trim())
    Assert-True $initialClean 'REMOTE_VERIFY is not clean before validation'
    Assert-True ((& git -C $worktreePath rev-parse HEAD).Trim() -eq $sourceCommit) `
        'REMOTE_VERIFY HEAD differs from fetched origin commit'

    $runtimeRoot = 'versions\26.2-neoforge\src\main\java\io\github\recrivenvi\minecraftprotocol\probe\runtime'
    $automation = Read-RemoteFile "$runtimeRoot\AutomationEngine.java"
    $transport = Read-RemoteFile "$runtimeRoot\ProbeTransport.java"
    $protocol = Read-RemoteFile "$runtimeRoot\ProtocolState.java"
    $events = Read-RemoteFile "$runtimeRoot\EventHub.java"
    $conditions = Read-RemoteFile "$runtimeRoot\ConditionEngine.java"
    $security = Read-RemoteFile "$runtimeRoot\SecurityGate.java"
    $recording = Read-RemoteFile "$runtimeRoot\RecordingEngine.java"
    $client = Read-RemoteFile 'companion\src\runtime-client.ts'
    $server = Read-RemoteFile 'companion\src\server.ts'
    $conditionConformance = Read-RemoteFile 'conformance\phase8\Invoke-Phase8ConditionConformance.ps1'
    $dependencyAudit = Read-RemoteFile 'conformance\phase8\DependencyAudit.ps1'
    $dependencyAuditTests = Read-RemoteFile 'conformance\phase8\Invoke-Phase8DependencyAuditClassifierTests.ps1'
    $localGateSource = Read-RemoteFile 'conformance\phase8\Invoke-Phase8LocalGate.ps1'
    $recordingShutdownTest = Read-RemoteFile `
        'versions\26.2-neoforge\src\test\java\io\github\recrivenvi\minecraftprotocol\probe\runtime\RecordingEngineTest.java'
    $evidence = Read-RemoteFile 'PHASE8_HARDENING_EVIDENCE.md'

    foreach ($status in @('PASS_NO_THRESHOLD_VULNERABILITIES','FAIL_VULNERABILITIES_FOUND',
            'FAIL_AUDIT_UNAVAILABLE','FAIL_INVALID_RESPONSE')) {
        Assert-True ($dependencyAudit.Contains($status)) "Dependency audit classification missing: $status"
    }
    Assert-True ($dependencyAudit -match 'ValidateRange\(1, 3\)' `
            -and $localGateSource -match 'Invoke-DependencyAudit.+MaxAttempts 3' `
            -and $localGateSource -match 'Invoke-Phase8DependencyAuditClassifierTests' `
            -and $localGateSource -match "Cases -eq 7" `
            -and ([regex]::Matches($dependencyAuditTests, "Add-Result '")).Count -eq 7) `
        'Remote dependency audit is not fail-closed with seven deterministic cases and three attempts'
    Assert-True ($recordingShutdownTest -match 'closeCancelsStalledCaptureAndFinalizesBundle' `
            -and $recordingShutdownTest -match 'bundle\.zip' `
            -and $recordingShutdownTest -match 'transport_close') `
        'Recording shutdown finalization regression coverage is missing'

    Assert-True ($client -notmatch 'arrayBuffer\s*\(') 'Companion production still uses arrayBuffer()'
    foreach ($marker in @('content-length','getReader(','reader.cancel')) {
        Assert-True ($client.Contains($marker)) "Companion streaming marker missing: $marker"
    }
    Assert-True ($transport -match 'eventHub\.publish' -and $transport -match 'eventHub\.register' `
            -and $transport -match 'eventHub\.accept' -and $transport -match 'eventHub\.unregister' `
            -and $transport -match 'eventHub\.channelWritable') 'EventHub is not on the production Transport path'
    Assert-True ($transport.Contains('/v0/events/resync') -and $transport.Contains('/v0/diagnostics/events/stress')) `
        'EventHub resync/stress routes are missing'
    Assert-True ($events.Contains('RING_CAPACITY = 1024') -and $events.Contains('CLIENT_QUEUE_CAPACITY = 128')) `
        'EventHub bounds are missing'

    Assert-True ($transport -match 'conditions\.waitUntil' -and $transport -match 'conditions\.assertThat') `
        'Standalone Wait/Assert does not route directly to ConditionEngine'
    foreach ($condition in @('screen','ui.exists','player','block','entity','menu','inventory','recording','event','operation','provider')) {
        Assert-True ($conditions.Contains('"' + $condition + '"')) "ConditionEngine missing: $condition"
    }
    Assert-True ($automation -notmatch 'isUiCondition' `
            -and ([regex]::Matches($automation, 'requireConditionEngine\(\)\.waitUntil')).Count -ge 2 `
            -and ([regex]::Matches($automation, 'requireConditionEngine\(\)\.assertThat')).Count -ge 2) `
        'Pipeline and standalone Automation conditions do not share ConditionEngine'
    foreach ($marker in @('Pipeline player/block waits','entity/menu asserts','Pipeline event wait','PipelineTypedConditions')) {
        Assert-True ($conditionConformance.Contains($marker)) "Pipeline typed condition conformance missing: $marker"
    }

    Assert-True ($security -match 'TokenBucket' -and $transport -match 'securityGate\.admit') `
        'SecurityGate is not on the authenticated production request path'
    Assert-True ($protocol -match 'cancelLeaseBoundOperations\("lease_expired"\)' `
            -and $protocol -match 'operations\.size\(\) >= 16' -and $protocol -match 'principalId') `
        'Operation/Lease/principal limits are not in production ProtocolState'
    Assert-True ($automation -match 'cancellationRequested' -and $automation -match 'stopPendingWork' `
            -and $automation -match 'checkActive' -and $automation -match 'Set<ScheduledFuture') `
        'Production Pipeline cancellation hardening is incomplete'

    foreach ($marker in @('MAX_SHEET_WIDTH','MAX_SHEET_HEIGHT','MAX_SHEET_PIXELS',
            'MAX_DECODED_SOURCE_BYTES','MAX_ESTIMATED_RAW_BYTES','MAX_OUTPUT_BYTES',
            'MAX_RECORDING_BYTES','MAX_BUNDLE_SOURCE_BYTES','Math.multiplyExact','Math.addExact')) {
        Assert-True ($recording.Contains($marker)) "Recording production marker missing: $marker"
    }
    Assert-True ($recording -notmatch 'Files\.readAllBytes' -and $recording -match 'CompletableFuture<Path> artifact') `
        'Recording Artifact path is not streaming-safe'
    Assert-True ($recording -match 'captureStopping' -and $recording -match 'pendingCaptureWork' `
            -and $recording -match 'stopPendingCaptureWork' -and $recording -match 'future\.cancel\(false\)') `
        'Recording shutdown cannot cancel stalled capture work'
    Assert-True ($transport -match 'ChunkedNioFile' -and $transport -match 'HttpChunkedInput') `
        'Runtime Artifact response is not streaming'

    foreach ($tool in @('minecraft_get_operation','minecraft_wait_operation','minecraft_cancel_operation','minecraft_execute_player_command')) {
        Assert-True ($server.Contains("registerTool('$tool'")) "Production MCP Tool missing: $tool"
    }
    Assert-True ($server -match 'z\.discriminatedUnion' -and $server -match 'cancellationSignal\(context\)' `
            -and $server -match "client\.json\('DELETE'" -and $server.Contains('/v0/operations/')) `
        'MCP schemas or cancellation propagation are missing'

    $staleEvidence = [regex]::Match($evidence, 'Baseline HEAD:\s*`([0-9a-f]{40})`')
    if ($staleEvidence.Success) {
        Assert-True ($staleEvidence.Groups[1].Value -eq $sourceCommit) `
            "Tracked Evidence binds $($staleEvidence.Groups[1].Value), not $sourceCommit"
    }
    Assert-True ($evidence -match 'sourceCommit' -and $evidence -match 'originCommit' `
            -and $evidence -match 'workingTreeClean' -and $evidence -match 'gateVersion' `
            -and $evidence -match 'timestamp') 'Tracked Evidence does not define commit-bound fields'

    $remoteGateRelativePath = 'conformance/phase8/Invoke-Phase8RemoteParityGate.ps1'
    Assert-True (Test-Path -LiteralPath (Join-Path $worktreePath $remoteGateRelativePath)) `
        'Remote commit does not contain the Remote Parity Gate'
    $gateScriptBlob = (& git -C $worktreePath rev-parse "$sourceCommit`:$remoteGateRelativePath").Trim()

    if (-not $SkipBuild) {
        Push-Location $worktreePath
        try {
            $arguments = @{}
            if ($Offline) { $arguments.Offline = $true }
            $localGate = & '.\conformance\phase8\Invoke-Phase8LocalGate.ps1' @arguments
            Assert-True ($localGate.Result -eq 'PASS') 'Remote Local/Static/Unit/Companion/Build gate failed'
        }
        finally { Pop-Location }
    }

    $criticalFiles = @(
        'companion/src/runtime-client.ts',
        'companion/src/server.ts',
        'protocol-schema/src/main/openapi/minecraft-control-v0.json',
        'conformance/phase8/DependencyAudit.ps1',
        'conformance/phase8/Invoke-Phase8DependencyAuditClassifierTests.ps1',
        'conformance/phase8/Invoke-Phase8LocalGate.ps1',
        'conformance/phase8/Invoke-Phase8HardeningStaticGate.ps1',
        'versions/26.2-neoforge/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/RecordingEngineTest.java',
        "$($runtimeRoot.Replace('\','/'))/AutomationEngine.java",
        "$($runtimeRoot.Replace('\','/'))/ProbeTransport.java",
        "$($runtimeRoot.Replace('\','/'))/ProtocolState.java",
        "$($runtimeRoot.Replace('\','/'))/EventHub.java",
        "$($runtimeRoot.Replace('\','/'))/ConditionEngine.java",
        "$($runtimeRoot.Replace('\','/'))/SecurityGate.java",
        "$($runtimeRoot.Replace('\','/'))/RecordingEngine.java"
    )
    foreach ($file in $criticalFiles) {
        $blob = (& git -C $worktreePath rev-parse "$sourceCommit`:$file").Trim()
        $actual = (& git -C $worktreePath hash-object $file).Trim()
        Assert-True ($blob -eq $actual) "Critical source differs from commit: $file"
        $criticalHashes[$file] = $blob
    }

    if (-not $SkipBuild) {
        foreach ($target in @('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.2-neoforge','26.2-fabric')) {
            $jar = Get-ChildItem -LiteralPath (Join-Path $worktreePath "versions\$target\build\libs") `
                -Filter '*.jar' -File | Where-Object Name -notmatch 'sources|dev' |
                Sort-Object LastWriteTime -Descending | Select-Object -First 1
            Assert-True ($null -ne $jar) "Remote build Artifact missing: $target"
            $artifactHashes[$target] = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash
        }
    }

    $finalClean = [string]::IsNullOrEmpty((& git -C $worktreePath status --porcelain | Out-String).Trim())
    Assert-True $finalClean 'REMOTE_VERIFY became dirty during gate execution'
}
catch {
    $failure = $_.Exception.Message
}
finally {
    if (-not [string]::IsNullOrEmpty($worktreePath) -and (Test-Path -LiteralPath $worktreePath)) {
        $finalClean = [string]::IsNullOrEmpty((& git -C $worktreePath status --porcelain | Out-String).Trim())
        $resolvedCandidate = (Resolve-Path -LiteralPath $worktreePath).Path
        $scratchWithSeparator = $scratch.TrimEnd('\') + '\'
        if ($resolvedCandidate.StartsWith($scratchWithSeparator, [StringComparison]::OrdinalIgnoreCase)) {
            Push-Location $repositoryRoot
            try {
                & git worktree remove --force $resolvedCandidate | Out-Null
                & git worktree prune
            }
            finally { Pop-Location }
        }
    }
}

$result = [ordered]@{
    result = $(if ($null -eq $failure) { 'PASS' } else { 'FAIL' })
    sourceCommit = $sourceCommit
    branch = $Branch
    originCommit = $sourceCommit
    workingTreeClean = $initialClean -and $finalClean
    gateVersion = $gateVersion
    timestamp = $timestamp
    remote = $Remote
    remoteRef = $remoteRef
    static = $(if ($null -ne $localGate) { $localGate.HardeningStatic } else { 'NOT_RUN' })
    javaTests = $(if ($null -ne $localGate) { $localGate.JavaTests } else { 0 })
    javaTestFailures = $(if ($null -ne $localGate) { $localGate.JavaTestFailures } else { 0 })
    companionTools = $(if ($null -ne $localGate) { $localGate.CompanionTools } else { 0 })
    dependencyAuditStatus = $(if ($null -ne $localGate) { $localGate.DependencyAuditStatus } else { 'NOT_RUN' })
    dependencyAuditAttempts = $(if ($null -ne $localGate) { $localGate.DependencyAuditAttempts } else { 0 })
    dependencyAuditServiceAvailable = $(if ($null -ne $localGate) { $localGate.DependencyAuditServiceAvailable } else { $false })
    dependencyAuditResponseValid = $(if ($null -ne $localGate) { $localGate.DependencyAuditResponseValid } else { $false })
    dependencyVulnerabilitiesHigh = $(if ($null -ne $localGate) { $localGate.DependencyVulnerabilitiesHigh } else { 0 })
    dependencyVulnerabilitiesCritical = $(if ($null -ne $localGate) { $localGate.DependencyVulnerabilitiesCritical } else { 0 })
    dependencyAuditClassifierTests = $(if ($null -ne $localGate) { $localGate.DependencyAuditClassifierTests } else { 0 })
    gateScriptBlob = $gateScriptBlob
    criticalSourceHashes = $criticalHashes
    artifactHashes = $artifactHashes
    failure = $failure
}

if (-not [string]::IsNullOrWhiteSpace($EvidenceOutput)) {
    $parent = Split-Path -Parent $EvidenceOutput
    if ($parent) { [IO.Directory]::CreateDirectory($parent) | Out-Null }
    $result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $EvidenceOutput -Encoding UTF8
}
$resultObject = [pscustomobject]$result
$resultObject
if ($resultObject.result -ne 'PASS') { exit 1 }
