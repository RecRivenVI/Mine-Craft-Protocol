# Checks response data, not implementation constants or source-string matches.
function Assert-ControlCursorResponse($Capabilities,$State) {
    if($Capabilities.capabilities.'input.host_cursor_capture'-cne'blocked_during_takeover') {throw 'Contradictory/missing host cursor capability'}
    if($State.mode-cnotin@('READ','OPERATE','TAKEOVER')) {throw 'Missing/invalid mode'}
    if($State.hostCursorPolicy-cne'never_capture_or_warp_during_takeover') {throw 'Missing/wrong TAKEOVER cursor policy'}
    if($State.hostCursorCaptureGranted-isnot[bool]-or$State.hostCursorCaptureGranted) {throw 'Removed native-click grant was advertised'}
    if(($State.nativeCaptureGrants-isnot[int]-and$State.nativeCaptureGrants-isnot[long])-or$State.nativeCaptureGrants-ne0) {throw 'Removed native-click grant counter advanced or is absent'}
    if($State.mode-eq'TAKEOVER'-and($State.hostCursorCaptured-isnot[bool]-or$State.hostCursorCaptured)) {throw 'TAKEOVER capture is not confirmed free'}
    # READ/OPERATE may legitimately have Vanilla host capture; never rewrite it from policy.
}

function Measure-ControlCursorSamples([object[]]$Samples) {
    $active=@($Samples|Where-Object state -eq 'AGENT_CONTROLLED')
    $violations=@($active|Where-Object {$_.captured-or$_.grant-or$_.grants-ne0}).Count
    $click=$null;$loss=$null;$returned=$null;$previous=$null
    foreach($sample in $active) {
        if($null-ne$previous-and$sample.focused-and$sample.suppressedButtons-gt$previous.suppressedButtons-and-not$sample.captured-and-not$sample.grant){if($null-eq$click){$click=$sample}}
        if($null-ne$click-and$sample.ms-gt$click.ms-and-not$sample.focused-and-not$sample.captured){if($null-eq$loss){$loss=$sample}}
        if($null-ne$loss-and$sample.ms-gt$loss.ms-and$sample.focused-and-not$sample.captured){$returned=$sample}
        $previous=$sample
    }
    [pscustomobject]@{capturedSamples=@($active|Where-Object captured).Count;violations=$violations;nativeButtonSuppressed=($null-ne$click);focusLoss=($null-ne$loss);returnWithoutCapture=($null-ne$returned);complete=($active.Count-gt0-and$violations-eq0-and$null-ne$click-and$null-ne$loss-and$null-ne$returned)}
}
