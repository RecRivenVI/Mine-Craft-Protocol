$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$tokens=$null;$errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Read-VanillaUiCatalog.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw ($errors|Out-String)}
foreach($name in @('Get-UiCatalogAssignment','Get-UiCatalogDisposition','Get-UiCatalogEvidence')){
    $definition=$ast.Find({param($node)$node-is[System.Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-eq$name},$true)
    if(-not$definition){throw "Missing production catalog function: $name"}
    . ([scriptblock]::Create($definition.Extent.Text))
}
$catalog=Get-Content -LiteralPath (Join-Path $repo 'validations/conformance-vanilla_ui/task/screens.json') -Raw|ConvertFrom-Json
if($catalog.families.Count-ne16){throw 'Keep the sixteen UI families; responsibility is not a new product domain'}
$realm=Get-UiCatalogAssignment $catalog 'com.mojang.realmsclient.RealmsMainScreen'
if($realm.family-ne'account_gated'-or$realm.responsibility-ne'external_service'){throw 'Realms namespace must have an explicit external-service owner'}
$realmBase=Get-UiCatalogAssignment $catalog 'net.minecraft.realms.RealmsScreen'
if($realmBase.family-ne'account_gated'){throw 'The native Realms inheritance base cannot disappear from inventory'}
$dialog=Get-UiCatalogAssignment $catalog 'net.minecraft.client.gui.screens.dialog.SimpleDialogScreen'
if($dialog.responsibility-ne'server_authored_ui'){throw 'Server-authored native dialogs must remain actionable pending UI, not be silently excluded'}
$unknown=Get-UiCatalogAssignment $catalog 'net.minecraft.client.gui.screens.FutureUnknownScreen'
if($unknown.family-ne'UNASSIGNED'){throw 'Unknown concrete classes cannot silently inherit a tested family'}
if((Get-UiCatalogDisposition 'interactive_ui' $false)-ne'PENDING'){throw 'Mapped but untested UI must stay pending'}
if((Get-UiCatalogDisposition 'interactive_ui' $true)-ne'RECORDED_LIVE_CHECKS'){throw 'Observed checks are historical evidence, never complete PASS'}
if((Get-UiCatalogDisposition 'external_service' $true)-ne'EXTERNAL_SERVICE_LIMITED'){throw 'A visited service entry is not a validated service flow'}
if((Get-UiCatalogDisposition 'non_interactive_transition' $true)-ne'NON_INTERACTIVE_TRANSITION'){throw 'Transition encounter must not imply operation acceptance'}
$family=@{evidenceChecks=@('^world selection:')}
$record=@{path='bound.json';sha256=('a'*64);report=@{screens=@('net.minecraft.client.gui.screens.worldselection.SelectWorldScreen');checks=@(@{name='world selection: actual semantic action';status='PASS'});testedAt='2026-10-20T00:00:00Z';testedCommit='bound-commit'}}
$actual=@(Get-UiCatalogEvidence @($record) $family 'net.minecraft.client.gui.screens.worldselection.SelectWorldScreen')
if($actual.Count-ne1-or$actual[0].checks.Count-ne1-or$actual[0].sourceBinding-ne'historical_recorded_run_not_current_candidate_acceptance'){throw 'Actual class plus successful matching checks must preserve the original evidence binding'}
if(@(Get-UiCatalogEvidence @($record) $family 'net.minecraft.client.gui.screens.OptionsScreen').Count){throw 'Another encountered class cannot inherit this proof'}
$record.report.checks[0].status='FAIL'
if(@(Get-UiCatalogEvidence @($record) $family 'net.minecraft.client.gui.screens.worldselection.SelectWorldScreen').Count){throw 'A failed check is not recorded operation coverage'}
$record.report.checks[0].status='PASS';$record.report.checks[0].name='unrelated: title'
if(@(Get-UiCatalogEvidence @($record) $family 'net.minecraft.client.gui.screens.worldselection.SelectWorldScreen').Count){throw 'An encountered Screen with no relevant check remains unvalidated'}
$duplicate=(@{families=@(@{name='one';screens=@('SelectWorldScreen')},@{name='two';screens=@('SelectWorldScreen')});namespaceFamilies=@();responsibilityRules=@{}}|ConvertTo-Json -Depth 6|ConvertFrom-Json)
$rejected=$false
try{Get-UiCatalogAssignment $duplicate 'net.minecraft.client.gui.screens.worldselection.SelectWorldScreen'|Out-Null}catch{if($_.Exception.Message-notlike'Screen has competing family assignments:*'){throw};$rejected=$true}
if(-not$rejected){throw 'Competing family assignments must be rejected'}
Write-Output 'PASS: native UI family/disposition and bound class/check evidence positive and negative controls; no Live acceptance inferred'
