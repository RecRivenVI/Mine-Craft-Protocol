[CmdletBinding()]
param(
    [switch]$Offline,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$gradle = Join-Path $repositoryRoot 'gradlew.bat'

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw "Current local gate assertion failed: $Message"
    }
}

$tasks = @(
    ':protocol_schema:openApiValidate',
    ':protocol_schema:generateProtocol',
    ':version:1.20.1-forge:build',
    ':version:1.21.1-neoforge:build',
    ':version:26.1.2-neoforge:build',
    ':version:26.3-neoforge:build',
    ':version:26.3-fabric:build'
)

Push-Location $repositoryRoot
try {
    $gradleArguments = @($tasks) + '--no-daemon'
    if ($Offline) {
        $gradleArguments += '--offline'
    }
    if (-not $SkipBuild) {
    & $gradle @gradleArguments | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        throw "Current Gradle gate failed with exit code $LASTEXITCODE"
    }
    }

    $schemaPath = Join-Path $repositoryRoot 'components\protocol_schema\src\main\openapi\minecraft-control-v0.json'
    $schema = Get-Content -LiteralPath $schemaPath -Raw | ConvertFrom-Json
    Assert-True ($schema.info.version -eq '0.0.1-control-r24') 'Schema must identify the current reviewed Native contract'
    Assert-True ($null -ne $schema.paths.'/v0/control/acquire') 'Control Lease path must remain available'
    Assert-True ($null -ne $schema.paths.'/v0/operations/{operationId}') 'operation handle path must exist'
    Assert-True ($null -ne $schema.paths.'/v0/diagnostics/thread') 'thread-affinity diagnostic path must remain available'
    Assert-True ($null -ne $schema.paths.'/v0/ui/resolve') 'UI selector path must exist'
    Assert-True ($null -ne $schema.paths.'/v0/ui/action') 'UI action path must exist'
    Assert-True ($null -ne $schema.paths.'/v0/pipelines') 'pipeline path must exist'
    Assert-True ($null -ne $schema.paths.'/v0/assert') 'assert path must exist'

    foreach ($targetPath in @(
        'versions\1.20.1-forge\src\main\java\io\github\recrivenvi\minecraftprotocol\probe\runtime',
        'versions\26.3-neoforge\src\main\java\io\github\recrivenvi\minecraftprotocol\probe\runtime',
        'versions\26.3-fabric\src\client\java\io\github\recrivenvi\minecraftprotocol\probe\runtime'
    )) {
        $runtime = Join-Path $repositoryRoot $targetPath
        Assert-True (Test-Path -LiteralPath (Join-Path $runtime 'ProtocolState.java')) "$targetPath must contain ProtocolState"
        Assert-True (Test-Path -LiteralPath (Join-Path $runtime 'RuntimeToken.java')) "$targetPath must contain RuntimeToken"
        Assert-True (Test-Path -LiteralPath (Join-Path $runtime 'AutomationEngine.java')) "$targetPath must contain AutomationEngine"
    }

    $facts = Get-Content (Join-Path $repositoryRoot 'gradle.properties') -Raw | ConvertFrom-StringData
    $artifacts = foreach ($target in @(
        '1.20.1-forge',
        '1.21.1-neoforge',
        '26.1.2-neoforge',
        '26.3-neoforge',
        '26.3-fabric'
    )) {
        $directory = Join-Path $repositoryRoot "versions\$target\build\libs"
        $jar = Get-ChildItem -LiteralPath $directory -Filter '*.jar' -File -ErrorAction Stop |
            Where-Object { $_.Name -notmatch 'sources|dev' } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($null -eq $jar) {
            throw "No release artifact found for $target"
        }
        Assert-True ($jar.BaseName -eq ($facts.mod_id + '-' + $target + '-' + $facts.mod_version)) "$target artifact must match authoritative product identity/version"
        [pscustomobject]@{
            Target = $target
            Artifact = $jar.Name
            Bytes = $jar.Length
        }
    }

    $javaModels = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'components\protocol_schema\build\generated\java') -Recurse -Filter '*.java')
    $typeScriptModels = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'components\protocol_schema\build\generated\typescript') -Recurse -Filter '*.ts')

    [pscustomobject]@{
        Result = 'PASS'
        Protocol = $schema.info.version
        JavaModels = $javaModels.Count
        TypeScriptFiles = $typeScriptModels.Count
        Targets = $artifacts
    }
}
finally {
    Pop-Location
}
