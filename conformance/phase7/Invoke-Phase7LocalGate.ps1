[CmdletBinding()]
param([switch]$Offline)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$gradle = Join-Path $repositoryRoot 'gradlew.bat'

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Phase 7 local gate assertion failed: $Message" }
}

$targets = @(
    [pscustomobject]@{ Name = '1.20.1-forge'; Runtime = 'src\main\java'; Screen = 'src\main\java' },
    [pscustomobject]@{ Name = '1.21.1-neoforge'; Runtime = 'src\main\java'; Screen = 'src\main\java' },
    [pscustomobject]@{ Name = '26.1.2-neoforge'; Runtime = 'src\main\java'; Screen = 'src\main\java' },
    [pscustomobject]@{ Name = '26.2-neoforge'; Runtime = 'src\main\java'; Screen = 'src\main\java' },
    [pscustomobject]@{ Name = '26.2-fabric'; Runtime = 'src\client\java'; Screen = 'src\client\java' }
)

$tasks = @(':protocol-schema:openApiValidate', ':protocol-schema:generateProtocol')
$tasks += $targets | ForEach-Object { ":versions:$($_.Name):build" }

Push-Location $repositoryRoot
try {
    $arguments = @($tasks) + '--no-daemon'
    if ($Offline) { $arguments += '--offline' }
    & $gradle @arguments | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Phase 7 Gradle gate failed with exit code $LASTEXITCODE" }

    $schema = Get-Content -LiteralPath 'protocol-schema\src\main\openapi\minecraft-control-v0.json' -Raw | ConvertFrom-Json
    Assert-True ($schema.info.version -eq '0.0.1-phase7') 'OpenAPI version must identify Phase 7'
    Assert-True ($null -ne $schema.paths.'/v0/diagnostics/hooks') 'Hook manifest path must exist'
    Assert-True ($null -ne $schema.components.schemas.HookManifestResponse) 'Hook manifest schema must exist'
    Assert-True ($null -ne $schema.components.schemas.HookDescriptor) 'Hook descriptor schema must exist'

    $hookGate = & '.\conformance\phase7\Invoke-Phase7HookCompatibilityGate.ps1'
    Assert-True ($hookGate.Result -eq 'PASS') 'static Hook compatibility gate must pass'

    $artifacts = foreach ($target in $targets) {
        $runtimePath = Join-Path $repositoryRoot "versions\$($target.Name)\$($target.Runtime)\io\github\recrivenvi\minecraftprotocol\probe\runtime"
        $runtimeText = Get-Content -LiteralPath (Join-Path $runtimePath ($(if ($target.Name -eq '1.20.1-forge') { 'ForgeProbeRuntime.java' } elseif ($target.Name -eq '26.2-fabric') { 'FabricProbeRuntime.java' } else { 'NeoForgeProbeRuntime.java' }))) -Raw
        Assert-True ($runtimeText -match 'hookManifest\s*\(') "$($target.Name) must implement Hook manifest"
        Assert-True ($runtimeText -match 'ui\.standard_mod_gui_extended') "$($target.Name) must publish extended GUI capability"

        $screenPath = Join-Path $repositoryRoot "versions\$($target.Name)\$($target.Screen)\io\github\recrivenvi\minecraftprotocol\probe\gui\AutomationProbeScreen.java"
        $screen = Get-Content -LiteralPath $screenPath -Raw
        foreach ($marker in @('Compatibility Text', 'Disabled Action', 'Duplicate Action', 'Dynamic Control')) {
            Assert-True ($screen.Contains($marker)) "$($target.Name) compatibility Screen must contain $marker"
        }

        $libraryDirectory = Join-Path $repositoryRoot "versions\$($target.Name)\build\libs"
        $jar = Get-ChildItem -LiteralPath $libraryDirectory -Filter '*.jar' -File |
            Where-Object { $_.Name -notmatch 'sources|dev' } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        Assert-True ($null -ne $jar -and $jar.Name -match 'phase7') "$($target.Name) must produce a Phase 7 artifact"
        [pscustomobject]@{ Target = $target.Name; Artifact = $jar.Name; Bytes = $jar.Length }
    }

    [pscustomobject]@{
        Result = 'PASS'
        Protocol = $schema.info.version
        HookPolicy = $hookGate.Policy
        JavaModels = @(Get-ChildItem 'protocol-schema\build\generated\java' -Recurse -Filter '*.java').Count
        TypeScriptFiles = @(Get-ChildItem 'protocol-schema\build\generated\typescript' -Recurse -Filter '*.ts').Count
        Targets = $artifacts
    }
}
finally {
    Pop-Location
}
