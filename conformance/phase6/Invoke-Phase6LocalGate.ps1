[CmdletBinding()]
param(
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$gradle = Join-Path $repositoryRoot 'gradlew.bat'

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Phase 6 local gate assertion failed: $Message" }
}

$targets = @(
    [pscustomobject]@{ Name = '1.20.1-forge'; RuntimeSource = 'src\main\java'; PeerClientSource = 'src\main\java' },
    [pscustomobject]@{ Name = '1.21.1-neoforge'; RuntimeSource = 'src\main\java'; PeerClientSource = 'src\main\java' },
    [pscustomobject]@{ Name = '26.1.2-neoforge'; RuntimeSource = 'src\main\java'; PeerClientSource = 'src\main\java' },
    [pscustomobject]@{ Name = '26.2-neoforge'; RuntimeSource = 'src\main\java'; PeerClientSource = 'src\main\java' },
    [pscustomobject]@{ Name = '26.2-fabric'; RuntimeSource = 'src\client\java'; PeerClientSource = 'src\client\java' }
)

$tasks = @(':protocol-schema:openApiValidate', ':protocol-schema:generateProtocol')
$tasks += $targets | ForEach-Object { ":versions:$($_.Name):build" }

Push-Location $repositoryRoot
try {
    $arguments = @($tasks) + '--no-daemon'
    if ($Offline) { $arguments += '--offline' }
    & $gradle @arguments | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Phase 6 Gradle gate failed with exit code $LASTEXITCODE" }

    $schemaPath = Join-Path $repositoryRoot 'protocol-schema\src\main\openapi\minecraft-control-v0.json'
    $schema = Get-Content -LiteralPath $schemaPath -Raw | ConvertFrom-Json
    Assert-True ($schema.info.version -eq '0.0.1-phase6') 'OpenAPI version must identify Phase 6'
    Assert-True ($null -ne $schema.paths.'/v0/server/peer') 'Peer status path must exist'
    Assert-True ($null -ne $schema.paths.'/v0/server/peer/probe') 'Peer round-trip probe path must exist'
    Assert-True ($null -ne $schema.components.schemas.ServerPeerStatusResponse) 'Peer status schema must exist'
    Assert-True ($null -ne $schema.components.schemas.ServerPeerCapabilitiesResponse) 'Peer capability schema must exist'
    Assert-True (Test-Path -LiteralPath (Join-Path $repositoryRoot 'conformance\phase6\Invoke-Phase6PeerConformance.ps1')) 'Integrated Peer conformance must exist'
    Assert-True (Test-Path -LiteralPath (Join-Path $repositoryRoot 'conformance\phase6\Invoke-Phase6DedicatedPeerConformance.ps1')) 'Dedicated Peer conformance must exist'

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
        $targetBuild = Get-Content -LiteralPath (Join-Path $repositoryRoot "versions\$($target.Name)\build.gradle") -Raw
        Assert-True ($targetBuild -match 'mcpQuickPlayServer') "$($target.Name) must expose the optional dedicated conformance quick-play argument"

        $libraryDirectory = Join-Path $repositoryRoot "versions\$($target.Name)\build\libs"
        $jar = Get-ChildItem -LiteralPath $libraryDirectory -Filter '*.jar' -File |
            Where-Object { $_.Name -notmatch 'sources|dev' } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        Assert-True ($null -ne $jar) "release artifact must exist for $($target.Name)"
        Assert-True ($jar.Name -match 'phase6') "$($target.Name) artifact must carry the Phase 6 version"
        [pscustomobject]@{ Target = $target.Name; Artifact = $jar.Name; Bytes = $jar.Length }
    }

    $placeholderTargets = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'versions') -Recurse -Filter '*Target.java' -File)
    Assert-True ($placeholderTargets.Count -eq 0) 'placeholder Target entrypoints must be gone'

    $fabricBuild = Get-Content -LiteralPath (Join-Path $repositoryRoot 'versions\26.2-fabric\build.gradle') -Raw
    $fabricMetadata = Get-Content -LiteralPath (Join-Path $repositoryRoot 'versions\26.2-fabric\src\main\resources\fabric.mod.json') -Raw
    Assert-True ($fabricBuild -match 'fabric-api') 'Fabric target must compile against Fabric API networking'
    Assert-True ($fabricMetadata -match '"fabric-api"') 'Fabric metadata must declare Fabric API'

    [pscustomobject]@{
        Result = 'PASS'
        Protocol = $schema.info.version
        JavaModels = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'protocol-schema\build\generated\java') -Recurse -Filter '*.java').Count
        TypeScriptFiles = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'protocol-schema\build\generated\typescript') -Recurse -Filter '*.ts').Count
        Targets = $artifacts
    }
}
finally {
    Pop-Location
}
