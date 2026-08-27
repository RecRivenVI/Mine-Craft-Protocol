$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'ControlCursorContract.ps1')
$caps='{"capabilities":{"input.host_cursor_capture":"blocked_during_takeover"}}'|ConvertFrom-Json
$state='{"mode":"READ","hostCursorPolicy":"never_capture_or_warp_during_takeover","hostCursorCaptureGranted":false,"nativeCaptureGrants":0,"hostCursorCaptured":true}'|ConvertFrom-Json
Assert-ControlCursorResponse $caps $state
function Reject($Capability,$State){$failed=$false;try{Assert-ControlCursorResponse $Capability $State}catch{$failed=$true};if(-not$failed){throw 'Contradictory response accepted'}}
$old='{"capabilities":{"input.host_cursor_capture":"agent_gated_native_click"}}'|ConvertFrom-Json
Reject $old $state
Reject (@{}|ConvertTo-Json|ConvertFrom-Json) $state
$state.mode='TAKEOVER';Reject $caps $state
$state.hostCursorCaptured=$false;Assert-ControlCursorResponse $caps $state
$state.hostCursorCaptureGranted=$true;Reject $caps $state
$state.hostCursorCaptureGranted=$false;$state.nativeCaptureGrants=1;Reject $caps $state
$state.nativeCaptureGrants=0;$state.PSObject.Properties.Remove('hostCursorCaptureGranted');Reject $caps $state
$samples=@(
    [pscustomobject]@{ms=0;state='AGENT_CONTROLLED';focused=$true;captured=$false;grant=$false;grants=0;suppressedButtons=0},
    [pscustomobject]@{ms=1;state='AGENT_CONTROLLED';focused=$true;captured=$false;grant=$false;grants=0;suppressedButtons=2},
    [pscustomobject]@{ms=2;state='AGENT_CONTROLLED';focused=$false;captured=$false;grant=$false;grants=0;suppressedButtons=2},
    [pscustomobject]@{ms=3;state='AGENT_CONTROLLED';focused=$true;captured=$false;grant=$false;grants=0;suppressedButtons=2})
if(-not(Measure-ControlCursorSamples $samples).complete){throw 'Suppressed click/focus sequence not recognized'}
$samples[1].captured=$true;$samples[1].grant=$true
if((Measure-ControlCursorSamples $samples).complete){throw 'Old human-click capture incorrectly passed'}
if((Measure-ControlCursorSamples @()).complete){throw 'Empty observation passed'}
[pscustomobject]@{Result='PASS';Checks=11;ProductionResponseCoverage='SEPARATE_NATIVE_CONFORMANCE';HumanEvents='SYNTHETIC_CLASSIFIER_ONLY_NOT_HUMAN_ACCEPTANCE'}
