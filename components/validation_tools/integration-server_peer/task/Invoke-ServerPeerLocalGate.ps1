[CmdletBinding()]
param(
    [switch]$Offline,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$gradle = Join-Path $repositoryRoot 'gradlew.bat'
. (Join-Path $PSScriptRoot 'Assert-ProductArtifact.ps1')

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Current local gate assertion failed: $Message" }
}

$targets = @(
    [pscustomobject]@{ Name = '1.20.1-forge'; RuntimeSource = 'src\main\java'; PeerClientSource = 'src\main\java' },
    [pscustomobject]@{ Name = '1.21.1-neoforge'; RuntimeSource = 'src\main\java'; PeerClientSource = 'src\main\java' },
    [pscustomobject]@{ Name = '26.1.2-neoforge'; RuntimeSource = 'src\main\java'; PeerClientSource = 'src\main\java' },
    [pscustomobject]@{ Name = '26.3-neoforge'; RuntimeSource = 'src\main\java'; PeerClientSource = 'src\main\java' },
    [pscustomobject]@{ Name = '26.3-fabric'; RuntimeSource = 'src\client\java'; PeerClientSource = 'src\client\java' }
)

$tasks = @(':protocol_schema:openApiValidate', ':protocol_schema:generateProtocol')
$tasks += $targets | ForEach-Object { ":version:$($_.Name):build" }

Push-Location $repositoryRoot
try {
    $arguments = @($tasks) + '--no-daemon'
    if ($Offline) { $arguments += '--offline' }
    if (-not $SkipBuild) {
        & $gradle @arguments | ForEach-Object { Write-Host $_ }
        if ($LASTEXITCODE -ne 0) { throw "Current Gradle gate failed with exit code $LASTEXITCODE" }
    }

    $schemaPath = Join-Path $repositoryRoot 'components\protocol_schema\src\main\openapi\minecraft-control-v0.json'
    $schema = Get-Content -LiteralPath $schemaPath -Raw | ConvertFrom-Json
    Assert-True ($schema.info.version -eq '0.0.1-control-r24') 'Schema must identify the current reviewed Native contract'
    Assert-True ($null -ne $schema.paths.'/v0/server/peer') 'Peer status path must exist'
    Assert-True ($null -ne $schema.paths.'/v0/server/peer/probe') 'Peer round-trip probe path must exist'
    Assert-True ($null -ne $schema.components.schemas.ServerPeerStatusResponse) 'Peer status schema must exist'
    Assert-True ($null -ne $schema.components.schemas.ServerPeerCapabilitiesResponse) 'Peer capability schema must exist'
    Assert-True (Test-Path -LiteralPath (Join-Path $repositoryRoot 'components\validation_tools\integration-server_peer\task\Invoke-Phase6PeerConformance.ps1')) 'Integrated Peer conformance must exist'
    Assert-True (Test-Path -LiteralPath (Join-Path $repositoryRoot 'components\validation_tools\integration-server_peer\task\Invoke-Phase6DedicatedPeerConformance.ps1')) 'Dedicated Peer conformance must exist'

    $facts = Get-Content (Join-Path $repositoryRoot 'gradle.properties') -Raw | ConvertFrom-StringData
    Assert-True (-not [string]::IsNullOrWhiteSpace($facts.mod_id) -and -not [string]::IsNullOrWhiteSpace($facts.mod_version)) 'Project artifact identity/version must be present'
    $artifacts = foreach ($target in $targets) {
        $runtime = Join-Path $repositoryRoot "versions\$($target.Name)\$($target.RuntimeSource)\io\github\recrivenvi\minecraftprotocol\probe\runtime"
        $peerClient = Join-Path $repositoryRoot "versions\$($target.Name)\$($target.PeerClientSource)\io\github\recrivenvi\minecraftprotocol\probe\runtime\DedicatedPeerClient.java"
        $peerServer = Join-Path $repositoryRoot "versions\$($target.Name)\src\main\java\io\github\recrivenvi\minecraftprotocol\probe\peer\DedicatedPeerServer.java"
        Assert-True (Test-Path -LiteralPath (Join-Path $runtime 'ProtocolState.java')) "$($target.Name) must have full ProtocolState"
        Assert-True (Test-Path -LiteralPath (Join-Path $runtime 'AutomationEngine.java')) "$($target.Name) must have full AutomationEngine"
        Assert-True (Test-Path -LiteralPath (Join-Path $runtime 'ObservationEngine.java')) "$($target.Name) must have full ObservationEngine"
        Assert-True (Test-Path -LiteralPath (Join-Path $runtime 'RecordingEngine.java')) "$($target.Name) must have full RecordingEngine"
        Assert-True (Test-Path -LiteralPath $peerClient) "$($target.Name) must have a Peer client"
        Assert-True (Test-Path -LiteralPath $peerServer) "$($target.Name) must have a Peer server"
        $targetBuild = Get-Content -LiteralPath (Join-Path $repositoryRoot "versions\$($target.Name)\build.gradle.kts") -Raw
        Assert-True ($targetBuild -match 'net\.neoforged\.moddev|net\.fabricmc\.fabric-loom') "$($target.Name) must retain its native Loader launch model; quick-play arguments are instance configuration"

        $libraryDirectory = Join-Path $repositoryRoot "versions\$($target.Name)\build\libs"
        $artifactPath = Join-Path $libraryDirectory ($facts.mod_id + '-' + $target.Name + '-' + $facts.mod_version + '.jar')
        Assert-True (Test-Path -LiteralPath $artifactPath -PathType Leaf) "current release artifact must exist for $($target.Name)"
        $jar = Get-Item -LiteralPath $artifactPath
        Assert-True ($jar.Length -gt 0) "$($target.Name) release artifact must not be empty"
        Assert-ProductArtifact $artifactPath $target.Name $facts.mod_id $facts.mod_version
        [pscustomobject]@{ Target = $target.Name; Artifact = $jar.Name; Bytes = $jar.Length }
    }

    $placeholderTargets = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'versions') -Recurse -Filter '*Target.java' -File)
    Assert-True ($placeholderTargets.Count -eq 0) 'placeholder Target entrypoints must be gone'

    $fabricBuild = Get-Content -LiteralPath (Join-Path $repositoryRoot 'versions\26.3-fabric\build.gradle.kts') -Raw
    $fabricMetadata = Get-Content -LiteralPath (Join-Path $repositoryRoot 'versions\26.3-fabric\src\main\resources\fabric.mod.json') -Raw
    Assert-True ($fabricBuild -match 'net\.fabricmc\.fabric-api:fabric-api') 'Fabric target must compile against Fabric API networking'
    Assert-True ($fabricMetadata -match '"fabric-api"') 'Fabric metadata must declare Fabric API'
    Assert-True (@($artifacts).Count -eq 5 -and @($artifacts.Target | Sort-Object -Unique).Count -eq 5) 'all five Target checks must execute'

    [pscustomobject]@{
        Result = 'PASS'
        Protocol = $schema.info.version
        JavaModels = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'components\protocol_schema\build\generated\java') -Recurse -Filter '*.java').Count
        TypeScriptFiles = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'components\protocol_schema\build\generated\typescript') -Recurse -Filter '*.ts').Count
        Targets = $artifacts
    }
}
finally {
    Pop-Location
}
