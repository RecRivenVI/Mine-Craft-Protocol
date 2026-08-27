[CmdletBinding()]
param(
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$gradle = Join-Path $repositoryRoot 'gradlew.bat'

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw "Phase 3 local gate assertion failed: $Message"
    }
}

$tasks = @(
    ':protocol-schema:openApiValidate',
    ':protocol-schema:generateProtocol',
    ':versions:1.20.1-forge:build',
    ':versions:1.21.1-neoforge:build',
    ':versions:26.1.2-neoforge:build',
    ':versions:26.2-neoforge:build',
    ':versions:26.2-fabric:build'
)

Push-Location $repositoryRoot
try {
    $gradleArguments = @($tasks) + '--no-daemon'
    if ($Offline) {
        $gradleArguments += '--offline'
    }
    & $gradle @gradleArguments | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        throw "Phase 3 Gradle gate failed with exit code $LASTEXITCODE"
    }

    $schemaPath = Join-Path $repositoryRoot 'protocol-schema\src\main\openapi\minecraft-control-v0.json'
    $schema = Get-Content -LiteralPath $schemaPath -Raw | ConvertFrom-Json
    Assert-True ($schema.info.version -match '^0\.0\.1-phase([3-9]|[1-9][0-9]+)$') 'OpenAPI version must retain Phase 3 or later behavior'
    Assert-True ($null -ne $schema.paths.'/v0/control/acquire') 'Control Lease path must remain available'
    Assert-True ($null -ne $schema.paths.'/v0/operations/{operationId}') 'operation handle path must exist'
    Assert-True ($null -ne $schema.paths.'/v0/diagnostics/thread') 'thread-affinity diagnostic path must remain available'
    Assert-True ($null -ne $schema.paths.'/v0/ui/resolve') 'UI selector path must exist'
    Assert-True ($null -ne $schema.paths.'/v0/ui/action') 'UI action path must exist'
    Assert-True ($null -ne $schema.paths.'/v0/pipelines') 'pipeline path must exist'
    Assert-True ($null -ne $schema.paths.'/v0/assert') 'assert path must exist'

    foreach ($targetPath in @(
        'versions\1.20.1-forge\src\main\java\io\github\recrivenvi\minecraftprotocol\probe\runtime',
        'versions\26.2-neoforge\src\main\java\io\github\recrivenvi\minecraftprotocol\probe\runtime',
        'versions\26.2-fabric\src\client\java\io\github\recrivenvi\minecraftprotocol\probe\runtime'
    )) {
        $runtime = Join-Path $repositoryRoot $targetPath
        Assert-True (Test-Path -LiteralPath (Join-Path $runtime 'ProtocolState.java')) "$targetPath must contain ProtocolState"
        Assert-True (Test-Path -LiteralPath (Join-Path $runtime 'RuntimeToken.java')) "$targetPath must contain RuntimeToken"
        Assert-True (Test-Path -LiteralPath (Join-Path $runtime 'AutomationEngine.java')) "$targetPath must contain AutomationEngine"
    }

    $artifacts = foreach ($target in @(
        '1.20.1-forge',
        '1.21.1-neoforge',
        '26.1.2-neoforge',
        '26.2-neoforge',
        '26.2-fabric'
    )) {
        $directory = Join-Path $repositoryRoot "versions\$target\build\libs"
        $jar = Get-ChildItem -LiteralPath $directory -Filter '*.jar' -File -ErrorAction Stop |
            Where-Object { $_.Name -notmatch 'sources|dev' } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($null -eq $jar) {
            throw "No release artifact found for $target"
        }
        Assert-True ($jar.Name -match 'phase([3-9]|[1-9][0-9]+)') "$target artifact must carry Phase 3 or later version"
        [pscustomobject]@{
            Target = $target
            Artifact = $jar.Name
            Bytes = $jar.Length
        }
    }

    $javaModels = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'protocol-schema\build\generated\java') -Recurse -Filter '*.java')
    $typeScriptModels = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'protocol-schema\build\generated\typescript') -Recurse -Filter '*.ts')

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
