[CmdletBinding()]
param([Parameter(Mandatory)][string]$Artifact, [ValidateSet('USER','DEBUG')][string]$Category='USER', [switch]$RequireCommandContamination,
    [switch]$RequireConfigurationContamination,[ValidateRange(1,16)][int]$ExpectedConfigurations=1)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$file=(Resolve-Path -LiteralPath $Artifact).Path
$allowed=@('conformance-world_read','migration-runtime_core')|Where-Object {$file.StartsWith((Join-Path $repo "validations/$_/instance/").Replace('/','\'),[StringComparison]::OrdinalIgnoreCase)}
if(-not$allowed){throw 'Artifact is not in an owned World validation instance'}
Add-Type -AssemblyName System.IO.Compression
$stream=[IO.File]::OpenRead($file)
$zip=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Read,$false)
$states=0
$manifest=$null
$commandAttempts=0
$commandSubmissions=0
$configurationAttempts=0
$configurationSubmissions=0
try{
    foreach($entry in $zip.Entries){
        if($entry.FullName-notmatch '\.(json|ndjson)$'){continue}
        if($entry.Length-gt8MB){throw 'Structured evidence entry exceeds validation budget'}
        $reader=[IO.StreamReader]::new($entry.Open())
        try{$text=$reader.ReadToEnd()}finally{$reader.Dispose()}
        if($entry.FullName-eq'manifest.json'){$manifest=$text|ConvertFrom-Json}
        if($entry.FullName-like'state/*'){$states++}
        if($entry.FullName-eq'timeline/timeline.ndjson'){
            foreach($line in ($text -split "`n" | Where-Object {$_.Trim()})){
                $event=$line|ConvertFrom-Json
                if($event.category-eq'evidence.contamination'-and$event.operation-eq'command.player.configuration.submission_attempt'){
                    if($event.mode-ne'DEBUG_PRIVILEGED'){throw 'Configuration must retain diagnostic authority'}
                    $configurationAttempts++
                }
                if($event.category-eq'command.player.configuration.submitted'){
                    $configurationSubmissions++;$p=$event.payload
                    $payloadType=if($p.configurationKind-like'STRUCTURE_*'){'STRUCTURE_PARAMETERS'}elseif($p.configurationKind-eq'JIGSAW_BLOCK'){'BLOCK_STATE'}else{'COMMAND'}
                    if(($p.configurationKind-notin@('COMMAND_BLOCK','COMMAND_MINECART')-or$p.PSObject.Properties.Name-contains'configurationPayloadType')-and$p.configurationPayloadType-ne$payloadType){throw 'Configuration payload type disagrees with native kind'}
                    if($p.configurationKind-like'STRUCTURE_*'-and$p.configuredCommandEffect-ne'STATE_MUTATION'){throw 'Structure parameters must retain mutation effect'}
                    if($p.mode-ne'DEBUG_PRIVILEGED'-or$p.effectClassification-ne'STATE_MUTATION'-or$p.classificationSource-ne'native_player_command_configuration'-or$p.configurationKind-notin@('COMMAND_BLOCK','COMMAND_MINECART','JIGSAW_BLOCK','STRUCTURE_UPDATE','STRUCTURE_SCAN')-or$p.configuredCommandEffect-notin@('NONE','COMMUNICATION','INFORMATION','STATE_MUTATION')-or$p.contaminationReason-ne'player_command_configuration'-or$p.evidenceContaminated-ne$true-or$p.effectConfirmed-ne$false-or$p.serverValidationObserved-ne$false-or$p.gameplayAcceptanceEligible-ne$false){throw 'Configuration receipt lost its typed, diagnostic or unconfirmed provenance'}
                }
                if($event.category-eq'evidence.contamination'-and$event.operation-eq'command.player.submission_attempt'){
                    if($RequireCommandContamination-and$event.mode-ne'DEBUG_PRIVILEGED'){throw 'State-mutation command attempt must retain diagnostic authority'}
                    $commandAttempts++
                }
                if($event.category-eq'command.player.submitted'){
                    $commandSubmissions++
                    if($RequireCommandContamination){
                        $p=$event.payload
                        if($p.mode-ne'DEBUG_PRIVILEGED'-or$p.effectClassification-ne'STATE_MUTATION'-or$p.mechanism-ne'GAME_ROUTED_RAW'-or$p.contaminationReason-ne'player_command_state_mutation'-or$p.evidenceContaminated-ne$true-or$p.effectConfirmed-ne$false-or$p.serverValidationObserved-ne$false-or$p.gameplayAcceptanceEligible-ne$false){throw 'Mutation receipt lost its native-input, diagnostic or unconfirmed provenance'}
                    }
                }
            }
        }
        if($Category-eq'USER'-and$text-match '"dataCategory"\s*:\s*"DEBUG"|"(?:uuid|serverTick|nbt|goals|inventory)"\s*:'){
            throw "Internal world field found in USER evidence: $($entry.FullName)"
        }
    }
    if(-not$manifest-or$manifest.dataCategory-ne$Category){throw 'Artifact category is absent or mismatched'}
    if($states-eq0){throw 'No structured state samples were produced'}
    if($RequireCommandContamination-and(-not$manifest.evidenceContaminated-or$commandAttempts-ne1-or$commandSubmissions-ne1)){throw 'State-mutation command must contaminate the manifest and have exactly one attempt and one submission entry'}
    if($RequireConfigurationContamination-and(-not$manifest.evidenceContaminated-or$configurationAttempts-ne$ExpectedConfigurations-or$configurationSubmissions-ne$ExpectedConfigurations)){throw 'Configurations must contaminate the manifest with exactly the expected attempt/submission events'}
}finally{$zip.Dispose();$stream.Dispose()}
@{status='PASS';category=$Category;structuredStates=$states;evidenceContaminated=[bool]$manifest.evidenceContaminated;commandAttemptEvents=$commandAttempts;commandSubmissionEvents=$commandSubmissions;configurationAttemptEvents=$configurationAttempts;configurationSubmissionEvents=$configurationSubmissions;operatorUiPixelAssertion='NOT_PERFORMED'}|ConvertTo-Json -Compress
