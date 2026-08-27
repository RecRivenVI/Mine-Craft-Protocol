[CmdletBinding()]
param([switch]$Offline,
    [switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$gradle = Join-Path $repositoryRoot 'gradlew.bat'
. (Join-Path $PSScriptRoot '../../integration-server_peer/task/Assert-ProductArtifact.ps1')

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Current local gate assertion failed: $Message" }
}

$targets = @(
    [pscustomobject]@{ Name = '1.20.1-forge'; Runtime = 'src\main\java'; Screen = 'src\main\java' },
    [pscustomobject]@{ Name = '1.21.1-neoforge'; Runtime = 'src\main\java'; Screen = 'src\main\java' },
    [pscustomobject]@{ Name = '26.1.2-neoforge'; Runtime = 'src\main\java'; Screen = 'src\main\java' },
    [pscustomobject]@{ Name = '26.3-neoforge'; Runtime = 'src\main\java'; Screen = 'src\main\java' },
    [pscustomobject]@{ Name = '26.3-fabric'; Runtime = 'src\client\java'; Screen = 'src\client\java' }
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

    $schema = Get-Content -LiteralPath 'components\protocol_schema\src\main\openapi\minecraft-control-v0.json' -Raw | ConvertFrom-Json
    Assert-True ($schema.info.version -eq '0.0.1-control-r24') 'Schema must identify the current reviewed Native contract'
    Assert-True ($null -ne $schema.paths.'/v0/diagnostics/hooks') 'Hook manifest path must exist'
    Assert-True ($null -ne $schema.components.schemas.HookManifestResponse) 'Hook manifest schema must exist'
    Assert-True ($null -ne $schema.components.schemas.HookDescriptor) 'Hook descriptor schema must exist'

    $hookGate = & '.\components\validation_tools\compatibility-standard_gui\task\Invoke-Phase7HookCompatibilityGate.ps1'
    Assert-True ($hookGate.Result -eq 'PASS') 'static Hook compatibility gate must pass'

    $facts = Get-Content (Join-Path $repositoryRoot 'gradle.properties') -Raw | ConvertFrom-StringData
    Assert-True (-not [string]::IsNullOrWhiteSpace($facts.mod_id) -and -not [string]::IsNullOrWhiteSpace($facts.mod_version)) 'Project artifact identity/version must be present'
    $artifacts = foreach ($target in $targets) {
        $runtimePath = Join-Path $repositoryRoot "versions\$($target.Name)\$($target.Runtime)\io\github\recrivenvi\minecraftprotocol\probe\runtime"
        $runtimeText = Get-Content -LiteralPath (Join-Path $runtimePath ($(if ($target.Name -eq '1.20.1-forge') { 'ForgeProbeRuntime.java' } elseif ($target.Name -eq '26.3-fabric') { 'FabricProbeRuntime.java' } else { 'NeoForgeProbeRuntime.java' }))) -Raw
        Assert-True ($runtimeText -match 'hookManifest\s*\(') "$($target.Name) must implement Hook manifest"
        Assert-True ($runtimeText -match 'ui\.standard_mod_gui_extended') "$($target.Name) must publish extended GUI capability"

        $screenPath = Join-Path $repositoryRoot "versions\$($target.Name)\$($target.Screen)\io\github\recrivenvi\minecraftprotocol\probe\gui\AutomationProbeScreen.java"
        $screen = Get-Content -LiteralPath $screenPath -Raw
        foreach ($marker in @('Compatibility Text', 'Disabled Action', 'Duplicate Action', 'Dynamic Control')) {
            Assert-True ($screen.Contains($marker)) "$($target.Name) compatibility Screen must contain $marker"
        }

        $libraryDirectory = Join-Path $repositoryRoot "versions\$($target.Name)\build\libs"
        $artifactPath = Join-Path $libraryDirectory ($facts.mod_id + '-' + $target.Name + '-' + $facts.mod_version + '.jar')
        Assert-True (Test-Path -LiteralPath $artifactPath -PathType Leaf) "$($target.Name) current release artifact must exist"
        $jar = Get-Item -LiteralPath $artifactPath
        Assert-True ($jar.Length -gt 0) "$($target.Name) release artifact must not be empty"
        Assert-ProductArtifact $artifactPath $target.Name $facts.mod_id $facts.mod_version
        [pscustomobject]@{ Target = $target.Name; Artifact = $jar.Name; Bytes = $jar.Length }
    }
    Assert-True (@($artifacts).Count -eq 5 -and @($artifacts.Target | Sort-Object -Unique).Count -eq 5) 'all five Target checks must execute'

    [pscustomobject]@{
        Result = 'PASS'
        Protocol = $schema.info.version
        HookPolicy = $hookGate.Policy
        JavaModels = @(Get-ChildItem 'components\protocol_schema\build\generated\java' -Recurse -Filter '*.java').Count
        TypeScriptFiles = @(Get-ChildItem 'components\protocol_schema\build\generated\typescript' -Recurse -Filter '*.ts').Count
        Targets = $artifacts
    }
}
finally {
    Pop-Location
}
