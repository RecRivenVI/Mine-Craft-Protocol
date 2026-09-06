#requires -Version 7.0
[CmdletBinding()]param()
# Local documentation checks only: no network, launcher, Runtime or world IO.
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$tracked=@(& git -C $repo ls-files --cached --others --exclude-standard | Sort-Object -Unique)
if($LASTEXITCODE-ne0){throw 'Cannot enumerate repository documents'}
$files=@($tracked|Where-Object {Test-Path -LiteralPath (Join-Path $repo $_) -PathType Leaf})
$documents=@($files|Where-Object {$_-match '\.md$'})
$failures=[Collections.Generic.List[string]]::new()
$links=[Collections.Generic.List[object]]::new()
$anchors=@{}
$external=[Collections.Generic.HashSet[string]]::new()
function Check([bool]$Ok,[string]$Reason){if(-not$Ok){$failures.Add($Reason)}}
function Current-Text([string]$Value) {
    $Value=$Value.Split(@('<!-- historical-body:begin -->'),[StringSplitOptions]::None)[0]
    $Value=[regex]::Replace($Value,'(?s)<!-- migration-inventory:begin -->.*?<!-- migration-inventory:end -->','')
    return $Value
}
function Without-Fences([string]$Value) {
    return [regex]::Replace($Value,'(?ms)^[ \t]*(\x60{3,}|~{3,})[^\r\n]*\r?\n.*?^[ \t]*\1[ \t]*(?:\r?\n|$)','')
}
function Anchors-For([string]$Relative) {
    if($anchors.ContainsKey($Relative)){return ,$anchors[$Relative]}
    $text=Without-Fences (Get-Content -LiteralPath (Join-Path $repo $Relative) -Raw)
    $set=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $occurrences=@{}
    foreach($m in [regex]::Matches($text,'(?m)^#{1,6}[ \t]+(.+?)\s*#*\s*$')) {
        $label=[regex]::Replace($m.Groups[1].Value,'\[([^\]]+)\]\([^)]+\)','$1')
        $label=[Net.WebUtility]::HtmlDecode([regex]::Replace($label,'<[^>]+>','')).ToLowerInvariant().Trim()
        $slug=[regex]::Replace($label,'[^\p{L}\p{N}_\- ]','') -replace ' ','-'
        $baseSlug=$slug
        if($occurrences.ContainsKey($baseSlug)){$occurrences[$baseSlug]++;$slug+='-'+$occurrences[$baseSlug]}else{$occurrences[$baseSlug]=0}
        [void]$set.Add($slug)
    }
    foreach($m in [regex]::Matches($text,'(?i)\b(?:id|name)=["'']([^"'']+)["'']')){[void]$set.Add($m.Groups[1].Value)}
    $anchors[$Relative]=$set
    return ,$set
}
function Check-Link([string]$From,[string]$Destination) {
    $destination=$Destination.Trim().Trim('<','>')
    if($destination-match'^[A-Za-z]:[/\\]'){$failures.Add("$From -> machine-specific absolute path: $destination");return}
    if($destination-match'^[A-Za-z][A-Za-z0-9+.-]*:'){[void]$external.Add($destination);return}
    $parts=$destination.Split('#',2)
    $path=[uri]::UnescapeDataString(($parts[0]-split'\?',2)[0])
    $fromAbsolute=Join-Path $repo $From
    if(-not$path){$absolute=$fromAbsolute}
    elseif($path.StartsWith('/')){$absolute=[IO.Path]::GetFullPath((Join-Path $repo $path.TrimStart('/')))}
    else{$absolute=[IO.Path]::GetFullPath((Join-Path (Split-Path $fromAbsolute -Parent) $path))}
    if(-not($absolute.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)-or$absolute-eq$repo)){$failures.Add("$From -> path escapes repo: $destination");return}
    if(-not(Test-Path -LiteralPath $absolute)){$failures.Add("$From -> missing: $destination");return}
    $relative=[IO.Path]::GetRelativePath($repo,$absolute).Replace('\','/')
    $links.Add(@{from=$From;to=$relative})
    if(Test-Path -LiteralPath $absolute -PathType Leaf) {
        Check ($files-ccontains$relative) "$From -> untracked or wrong-case file: $destination"
    }
    if($parts.Length-eq2-and$parts[1]-and$relative.EndsWith('.md')) {
        $fragment=[uri]::UnescapeDataString($parts[1])
        Check ((Anchors-For $relative).Contains($fragment)) "$From -> missing anchor: $destination"
    }
}
foreach($document in $documents) {
    $text=Get-Content -LiteralPath (Join-Path $repo $document) -Raw
    # Historical body stays untouched; its new archive preface must still have valid links.
    $text=$text.Split(@('<!-- historical-body:begin -->'),[StringSplitOptions]::None)[0]
    $text=Without-Fences $text
    $text=[regex]::Replace($text,'\x60+[^\x60\r\n]*\x60+','')
    $definitions=@{}
    foreach($m in [regex]::Matches($text,'(?m)^\[([^\]]+)\]:\s*(<[^>]+>|\S+)')) {
        $definitions[$m.Groups[1].Value.ToLowerInvariant()]=$m.Groups[2].Value
        Check-Link $document $m.Groups[2].Value
    }
    foreach($m in [regex]::Matches($text,'!?\[[^\]\r\n]*\]\(\s*(<[^>]+>|[^\s)]+)(?:\s+["''][^"'']*["''])?\s*\)')) {
        Check-Link $document $m.Groups[1].Value
    }
    foreach($m in [regex]::Matches($text,'\[[^\]\r\n]+\]\[([^\]\r\n]+)\]')) {
        Check ($definitions.ContainsKey($m.Groups[1].Value.ToLowerInvariant())) "$document -> undefined reference: $($m.Value)"
    }
    foreach($m in [regex]::Matches($text,'(?i)\b(?:href|src)=["'']([^"'']+)["'']')){Check-Link $document $m.Groups[1].Value}
    foreach($m in [regex]::Matches($text,'<((?:https?|mailto):[^<>\s]+)>')){Check-Link $document $m.Groups[1].Value}
}
$rootDocs=@($documents|Where-Object {$_-notmatch'/'})
Check (($rootDocs|Sort-Object)-join','-eq'AGENTS.md,README.md') 'Root Markdown must be README.md and AGENTS.md only'
foreach($document in $documents|Where-Object {$_-like'documents/*'-and$_-ne'documents/README.md'}) {
    Check (@($links|Where-Object {$_.from-eq'documents/README.md'-and$_.to-eq$document}).Count-gt0) "Document is missing from documents/README.md: $document"
}
$retired=@('PLATFORM_VISION.md','PLATFORM_EXTENSION_GOALS.md','PROJECT_EXECUTION_PLAN.md','ARCHITECTURE.md','THREAT_MODEL.md','AGENT_CONTROL_MODEL_RESEARCH.md','adr/0001-explicit-target-governance.md','adr/0002-phase0-input-render-hooks.md','adr/0003-openapi-v0-contract.md','ADR-0001-V1-LOOPBACK-RELEASE-PROFILE.md','PHASE0_PROBE.md','PHASE2_PROTOCOL_CORE.md','PHASE3_AUTOMATION.md','PHASE4_OBSERVATION.md','PHASE5_RECORDING_DEBUG.md','PHASE6_DEDICATED_SERVER_PEER.md','PHASE7_V1_ALIGNMENT.md','PHASE8_MCP_COMPANION.md','PHASE8_HARDENING_EVIDENCE.md','PHASE9_IMPLEMENTATION_PLAN.md','PROTOCOL_V0_DRAFT.md','conformance/control/SHOWCASE.md')
foreach($file in $files|Where-Object {$_-notlike'validations/results/*'-and$_-ne'validations/tests/core/repository/Invoke-DocumentationGate.ps1'-and$_-match'\.(md|ps1|mjs|ts|java|json|properties|gradle|kts|ya?ml)$'}) {
    $content=Current-Text (Get-Content -LiteralPath (Join-Path $repo $file) -Raw)
    # The renamed ADR explicitly preserves its prior identity, not an active reference.
    if($file-eq'documents/architecture/adr/0004-v1-loopback-release-profile.md'){$content=$content -replace '(?m)^- Archive identity:.*$',''}
    foreach($old in $retired) {
        $pattern=if($old.StartsWith('adr/')){'(?<![A-Za-z0-9_./\\-])'+[regex]::Escape($old)}else{[regex]::Escape($old)}
        Check ($content-notmatch$pattern) "$file -> retired current path reference: $old"
    }
}
$schema=Get-Content (Join-Path $repo 'components/protocol-schema/src/main/openapi/minecraft-control-v0.json') -Raw|ConvertFrom-Json
$server=Get-Content (Join-Path $repo 'components/companion/src/server.ts') -Raw
$tools=[regex]::Matches($server,"registerTool\('").Count
$methods=@($schema.paths.PSObject.Properties|ForEach-Object {$_.Value.PSObject.Properties|Where-Object {$_.Value.operationId}})
$reference=Get-Content (Join-Path $repo 'documents/reference/protocol-v0.md') -Raw
$companion=Get-Content (Join-Path $repo 'components/companion/README.md') -Raw
Check ($reference.Contains($schema.info.version)-and$companion.Contains($schema.info.version)) 'Current protocol version drift'
Check ($companion.Contains("publishes $tools static Tools")-and$reference.Contains("All $($methods.Count) formal HTTP operations")) 'Current Native/MCP count drift'
$vision=Get-Content (Join-Path $repo 'documents/product/vision.md') -Raw
$control=Get-Content (Join-Path $repo 'documents/architecture/agent-control.md') -Raw
$pack=Get-Content (Join-Path $repo 'documents/testing/modpack-compatibility.md') -Raw
Check ($vision.Contains('Agent-native Minecraft Autonomous Testing Platform')-and$vision.Contains('NOT FROZEN')) 'Core scope/wire boundary missing'
Check ($control.Contains('CURRENT')-and$control.Contains('NOT YET ACCEPTED')-and$control.Contains('FUTURE')) 'Control architecture/acceptance labels missing'
foreach($name in @('All the Mods 9','All the Mods 10','All the Mods 11','All the Mods 10 Aeronautics')){Check ($pack.Contains($name)) "Missing standing pack: $name"}
Check ($pack.Contains('E:\Minecraft\PrismLauncherDev')-and$pack.Contains('NOT STARTED')-and$pack.Contains('Codex Computer Use')) 'Prism planning/human boundary missing'
if($failures.Count){throw ("Documentation gate failed:"+[Environment]::NewLine+($failures-join[Environment]::NewLine))}
[pscustomobject]@{Result='PASS';MarkdownDocuments=$documents.Count;LocalLinks=$links.Count;ExternalReferences=$external.Count;ExternalNetworkChecks='NOT_PERFORMED';HistoricalBodies='PRESERVED_EXCLUDED_FROM_CURRENT_PATH_CHECK';HttpOperations=$methods.Count;McpTools=$tools;RuntimeStarted=$false}
