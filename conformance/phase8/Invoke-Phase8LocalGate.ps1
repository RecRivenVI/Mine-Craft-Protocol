[CmdletBinding()]
param([switch]$Offline)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$gradle = Join-Path $repositoryRoot 'gradlew.bat'
$companion = Join-Path $repositoryRoot 'companion'

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Phase 8 local gate assertion failed: $Message" }
}

. (Join-Path $PSScriptRoot 'DependencyAudit.ps1')

$targets = '1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.2-neoforge','26.2-fabric'
$tasks = @(':protocol-schema:openApiValidate', ':protocol-schema:generateProtocol')
$tasks += $targets | ForEach-Object { ":versions:${_}:build" }

Push-Location $repositoryRoot
try {
    $arguments = @($tasks) + '--no-daemon'
    if ($Offline) { $arguments += '--offline' }
    & $gradle @arguments | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "Phase 8 Gradle gate failed with exit code $LASTEXITCODE" }

    $schema = Get-Content 'protocol-schema\src\main\openapi\minecraft-control-v0.json' -Raw | ConvertFrom-Json
    Assert-True ($schema.info.version -eq '0.0.1-control-r24') 'OpenAPI must identify the current Control Round 1 V0 contract'
    Assert-True ($null -ne $schema.paths.'/v0/diagnostics/hooks') 'Phase 7 Hook contract must remain present'
    Assert-True ($null -ne $schema.paths.'/v0/operations/{operationId}/wait') 'native operation wait contract must exist'
    Assert-True ($null -ne $schema.paths.'/v0/events/resync') 'event resync contract must exist'
    Assert-True ($null -ne $schema.paths.'/v0/command/player') 'current-player command contract must exist'

    $hardening = & '.\conformance\phase8\Invoke-Phase8HardeningStaticGate.ps1'
    Assert-True ($hardening.Result -eq 'PASS') 'Phase 8 hardening static gate must pass'
    Assert-True (Test-Path -LiteralPath '.\conformance\phase8\Invoke-Phase8RemoteParityGate.ps1') `
        'Phase 8 Remote Parity Gate must exist'
    $auditClassifierTests = & '.\conformance\phase8\Invoke-Phase8DependencyAuditClassifierTests.ps1'
    Assert-True ($auditClassifierTests.Result -eq 'PASS' -and $auditClassifierTests.Cases -eq 7) `
        'Dependency audit classifier deterministic tests must pass'

    $javaTestReports = @(Get-ChildItem 'versions\26.2-neoforge\build\test-results\test' -Filter 'TEST-*.xml' -File)
    $javaTests = 0
    $javaFailures = 0
    foreach ($reportFile in $javaTestReports) {
        [xml]$report = Get-Content -LiteralPath $reportFile.FullName
        $javaTests += [int]$report.testsuite.tests
        $javaFailures += [int]$report.testsuite.failures + [int]$report.testsuite.errors
    }
    Assert-True ($javaTests -ge 8 -and $javaFailures -eq 0) 'Java hardening unit tests must execute and pass'

    $artifacts = foreach ($target in $targets) {
        $jar = Get-ChildItem "versions\$target\build\libs" -Filter '*.jar' -File |
            Where-Object Name -notmatch 'sources|dev' |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        Assert-True ($null -ne $jar -and $jar.Name -match 'phase8') "$target must produce a Phase 8 artifact"
        [pscustomobject]@{ Target = $target; Artifact = $jar.Name; Bytes = $jar.Length }
    }

    Push-Location $companion
    try {
        & npm ci | ForEach-Object { Write-Host $_ }
        if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
        & npm test | ForEach-Object { Write-Host $_ }
        if ($LASTEXITCODE -ne 0) { throw 'Companion test suite failed' }
        $auditResult = Invoke-DependencyAudit -WorkingDirectory (Get-Location).Path -MaxAttempts 3
        foreach ($attemptResult in $auditResult.AttemptResults) {
            if ($attemptResult.Status -like 'RETRY_*') {
                Write-Warning ("Dependency audit attempt {0}: {1}; {2}; diagnostic={3}" -f `
                    $attemptResult.Attempt,
                    $attemptResult.FailureKind,
                    $attemptResult.FailureMessage,
                    $attemptResult.Diagnostic)
            }
        }
        if ($auditResult.Status -ne 'PASS_NO_THRESHOLD_VULNERABILITIES') {
            throw ("Dependency audit failed: status={0}; kind={1}; message={2}" -f `
                $auditResult.Status, $auditResult.FailureKind, $auditResult.FailureMessage)
        }
    }
    finally {
        Pop-Location
    }

    $productionFiles = @(
        'companion\src\index.ts',
        'companion\src\server.ts',
        'companion\src\config.ts',
        'companion\src\runtime-client.ts',
        'companion\src\result.ts',
        'companion\src\session-state.ts'
    )
    $production = ($productionFiles | ForEach-Object { Get-Content $_ -Raw }) -join "`n"
    Assert-True ($production -notmatch 'console\.log\s*\(') 'stdio production code must not write logs to stdout'
    Assert-True ($production -notmatch 'node:child_process|\bexecSync?\s*\(|\bspawnSync?\s*\(') 'Companion production code must not expose process execution'
    Assert-True ($production -notmatch 'registerTool\s*\([^''\"]') 'MCP Tool names must be static literals'
    Assert-True (([regex]::Matches($production, "registerTool\('")).Count -eq 24) 'Companion must expose the reviewed 24-Tool surface'
    Assert-True (([regex]::Matches($production, "registerPrompt\('")).Count -eq 1) 'Companion must expose one static Prompt'
    Assert-True ($production -match 'dataPlaneOnly:\s*true') 'Companion must propagate the data-plane trust boundary'
    Assert-True ($production -match 'validatePath\(path') 'Runtime paths must pass typed namespace validation'

    $lock = Get-Content 'companion\package-lock.json' -Raw | ConvertFrom-Json -AsHashtable
    Assert-True ($lock.packages['node_modules/@modelcontextprotocol/server'].version -eq '2.0.0') 'MCP server SDK must be pinned to reviewed v2'
    Assert-True ($lock.packages['node_modules/@modelcontextprotocol/client'].version -eq '2.0.0') 'MCP test Client must be pinned to reviewed v2'

    [pscustomobject]@{
        Result = 'PASS'
        Protocol = $schema.info.version
        Targets = $artifacts
        CompanionTools = 24
        CompanionResources = 4
        CompanionResourceTemplates = 2
        CompanionPrompts = 1
        DependencyAuditStatus = $auditResult.Status
        DependencyAuditAttempts = $auditResult.Attempts
        DependencyAuditServiceAvailable = $auditResult.ServiceAvailable
        DependencyAuditResponseValid = $auditResult.ResponseValid
        DependencyVulnerabilitiesTotal = $auditResult.VulnerabilitiesTotal
        DependencyVulnerabilitiesLow = $auditResult.VulnerabilitiesLow
        DependencyVulnerabilitiesModerate = $auditResult.VulnerabilitiesModerate
        DependencyVulnerabilitiesHigh = $auditResult.VulnerabilitiesHigh
        DependencyVulnerabilitiesCritical = $auditResult.VulnerabilitiesCritical
        DependencyAuditFailureKind = $auditResult.FailureKind
        DependencyAuditFailureMessage = $auditResult.FailureMessage
        DependencyAuditClassifierTests = $auditClassifierTests.Cases
        HardeningStatic = $hardening.Result
        JavaTests = $javaTests
        JavaTestFailures = $javaFailures
        JavaModels = @(Get-ChildItem 'protocol-schema\build\generated\java' -Recurse -Filter '*.java').Count
        TypeScriptFiles = @(Get-ChildItem 'protocol-schema\build\generated\typescript' -Recurse -Filter '*.ts').Count
    }
}
finally {
    Pop-Location
}
