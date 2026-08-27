[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$gradle = Join-Path $repositoryRoot 'gradlew.bat'

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
    & $gradle @tasks '--no-daemon' | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        throw "Phase 1 Gradle gate failed with exit code $LASTEXITCODE"
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
        JavaModels = $javaModels.Count
        TypeScriptFiles = $typeScriptModels.Count
        Targets = $artifacts
    }
}
finally {
    Pop-Location
}
