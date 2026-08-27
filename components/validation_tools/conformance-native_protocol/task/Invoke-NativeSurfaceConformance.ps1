[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
function Check([bool]$Ok,[string]$Reason){if(-not$Ok){throw "Native/MCP surface: $Reason"}}
$schema=Get-Content (Join-Path $root 'components/protocol_schema/src/main/openapi/minecraft-control-v0.json') -Raw|ConvertFrom-Json
$server=Get-Content (Join-Path $root 'components/companion/src/server.ts') -Raw
$tools=[regex]::Matches($server,"registerTool\('").Count
$methods=@($schema.paths.PSObject.Properties|ForEach-Object {$_.Value.PSObject.Properties|Where-Object {$_.Value.operationId}})
$reference=Get-Content (Join-Path $root 'documents/reference/protocol-native_v0.md') -Raw
$companion=Get-Content (Join-Path $root 'documents/reference/api-companion.md') -Raw
Check ($reference.Contains($schema.info.version)-and$companion.Contains($schema.info.version)) 'Current protocol version drift'
Check ($companion.Contains("publishes $tools static Tools")-and$reference.Contains("All $($methods.Count) formal HTTP operations")) 'Current Native/MCP count drift'
$vision=Get-Content (Join-Path $root 'documents/project/scope-core.md') -Raw
$control=Get-Content (Join-Path $root 'documents/design/concept-agent_control.md') -Raw
$pack=Get-Content (Join-Path $root 'validations/compatibility-large_modpacks/validation.md') -Raw
Check ($vision.Contains('Agent-native Minecraft Autonomous Testing Platform')-and$vision.Contains('NOT FROZEN')) 'Core scope/wire boundary missing'
Check ($control.Contains('CURRENT')-and$control.Contains('NOT YET ACCEPTED')-and$control.Contains('FUTURE')) 'Control architecture/acceptance labels missing'
foreach($name in @('All the Mods 9','All the Mods 10','All the Mods 11','All the Mods 10 Aeronautics')){Check ($pack.Contains($name)) "Missing standing pack: $name"}
Check ($pack.Contains('E:\Minecraft\PrismLauncherDev')-and$pack.Contains('NOT STARTED')-and$pack.Contains('Codex Computer Use')) 'Prism planning/human boundary missing'

[pscustomobject]@{Result='PASS';HttpOperations=$methods.Count;McpTools=$tools;NativeVersion=$schema.info.version;Runtime='NOT_RUN'}
