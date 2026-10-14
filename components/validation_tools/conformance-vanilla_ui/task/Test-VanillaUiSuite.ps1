$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Invoke-VanillaUiSuite.ps1'), [ref]$tokens, [ref]$errors) | Out-Null
if($errors.Count){throw 'UI runner syntax error'}
$runner=Join-Path $PSScriptRoot 'Invoke-VanillaUiSuite.ps1'
try { & $runner -Target '26.3-fabric' -RuntimePort 65436; throw 'Invalid Runtime or fixture port accepted' }
catch { if ($_.FullyQualifiedErrorId -notlike 'ParameterArgumentValidationError*') { throw } }
try { & $runner -All -KeepClient; throw 'Batch KeepClient accepted' }
catch { if ($_.Exception.Message -ne 'Batch testing requires automatic shutdown and existing immutable baselines') { throw } }
foreach($scenario in @('editors','hud','spectator')) {
    try { & $runner -All -Scenario $scenario; throw 'Partial scenarios accepted as full batch evidence' }
    catch { if ($_.Exception.Message -ne 'Batch evidence requires the complete scenario matrix') { throw } }
}
$configuration=Get-Content (Join-Path $repo 'validations/conformance-vanilla_ui/validation.toml') -Raw
if($configuration-notmatch 'memory-max = "4G"'-or$configuration-match 'minecraft.protocol.probe.port'){throw 'UI launch memory/port isolation contract is inconsistent'}
[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Read-VanillaUiCatalog.ps1'), [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count) { throw ($errors | Out-String) }
& node --check (Join-Path $PSScriptRoot 'run-vanilla-ui.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Native UI runner syntax failed' }
& node --check (Join-Path $PSScriptRoot 'run-web-input.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Web input runner syntax failed' }
& node --check (Join-Path $PSScriptRoot 'run-ui-category-consumers.mjs')
if ($LASTEXITCODE -ne 0) { throw 'MCP category runner syntax failed' }
& node --check (Join-Path $PSScriptRoot 'toast-consumers.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Toast consumers syntax failed' }
& node --check (Join-Path $PSScriptRoot 'editor-consumers.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Editor consumers syntax failed' }
& node --check (Join-Path $PSScriptRoot 'spectator-consumers.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Spectator consumers syntax failed' }
$catalog = Get-Content (Join-Path $repo 'validations/conformance-vanilla_ui/task/screens.json') -Raw | ConvertFrom-Json
$names = @($catalog.families | ForEach-Object { $_.name })
if ($names.Count -lt 10 -or @($names | Sort-Object -Unique).Count -ne $names.Count) { throw 'UI coverage families must be complete and unique' }
foreach ($family in $catalog.families) {
    if (-not $family.screens.Count -or -not $family.actions.Count) { throw "Missing coverage contract: $($family.name)" }
    if (@($family.screens | Sort-Object -Unique).Count -ne $family.screens.Count) { throw "Duplicate Screen: $($family.name)" }
}
Write-Output "PASS: isolated UI runner syntax and $($names.Count) coverage families"
