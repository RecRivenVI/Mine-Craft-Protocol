[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
$base=$BaseUri.TrimEnd('/')
$token=(Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth=@{Authorization="Bearer $token"}
$output=[IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $output|Out-Null
function Require([bool]$Condition,[string]$Reason){if(-not $Condition){throw "Core live correctness: $Reason"}}
function Json([string]$Method,[string]$Path,[object]$Body=$null,[hashtable]$Headers=$auth){
    $p=@{Uri="$base$Path";Method=$Method;Headers=$Headers;TimeoutSec=30}
    if($null-ne$Body){$p.ContentType='application/json';$p.Body=$Body|ConvertTo-Json -Depth 30 -Compress}
    Invoke-RestMethod @p
}
function Capture([string]$Name){$p=Join-Path $output $Name;Invoke-WebRequest "$base/v0/capture" -Headers $auth -OutFile $p -TimeoutSec 15;return $p}
function Acquire {
    $status=Json GET '/v0/control/status'
    Require ($status.controlState-ne'MANUALLY_REVOKED') 'new conversation consent is required after a manual revocation'
    if($status.leaseId){Json POST '/v0/control/release' $null (@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$status.leaseId})|Out-Null}
    $script:lease=Json POST '/v0/control/acquire' @{ttlMs=60000}
    $script:control=@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$lease.leaseId}
}
function Release {Json POST '/v0/control/release' $null $control|Out-Null}
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
function PixelSamples([Drawing.Bitmap]$Image) {
    $samples=[Collections.Generic.List[int]]::new()
    for($x=0;$x-lt$Image.Width;$x+=17){$samples.Add($Image.GetPixel($x,0).ToArgb());$samples.Add($Image.GetPixel($x,$Image.Height-1).ToArgb())}
    for($y=0;$y-lt$Image.Height;$y+=17){$samples.Add($Image.GetPixel(0,$y).ToArgb());$samples.Add($Image.GetPixel($Image.Width-1,$y).ToArgb())}
    for($y=$Image.Height-65;$y-lt$Image.Height-8;$y+=11){
        for($x=[Math]::Max(0,$Image.Width-290);$x-lt$Image.Width-8;$x+=19){$samples.Add($Image.GetPixel($x,$y).ToArgb())}
    }
    return ,$samples.ToArray()
}
function ImageSamples([string]$Path){$image=[Drawing.Bitmap]::new($Path);try{PixelSamples $image}finally{$image.Dispose()}}
function ComparePixels([int[]]$Reference,[int[]]$Actual){
    Require ($Reference.Length-eq$Actual.Length) 'capture dimensions changed during oracle comparison'
    $different=0
    for($i=0;$i-lt$Reference.Length;$i++){if($Reference[$i]-ne$Actual[$i]){$different++}}
    return $different
}
$session=Json GET '/v0/session'
Require ($session.target-eq$ExpectedTarget -and $session.inWorld) 'a dedicated test world must be open'
$clientBlock=Json GET '/v0/world/block?x=1600000&y=70&z=1600000'
Require (-not $clientBlock.available -and $clientBlock.reason-eq'chunk_not_loaded' -and -not $clientBlock.chunkLoadRequested) 'client far block must be unavailable without loading'
$serverBlock=Json GET '/v0/server/world/block?x=1600000&y=70&z=1600000'
Require (-not $serverBlock.available -and -not $serverBlock.storageAccessed) 'server far block must stay LIVE/no-load'
$storage=[ordered]@{}
foreach($domain in @('world','player','chunk')){
    try{
        $read=Json POST '/v0/diagnostics/phase9a/storage/read' @{domain=$domain}
        Require ($read.dataSource-eq'PERSISTED' -and $read.consistency-eq'last_saved_state' -and -not $read.writeImplemented) 'storage authority changed'
        $storage[$domain]=$read.readStatus
    }catch{
        if (-not $_.ErrorDetails.Message) { throw }
        $errorBody=$_.ErrorDetails.Message|ConvertFrom-Json
        Require ($errorBody.error-in@('PERSISTED_STORAGE_BUSY','PERSISTED_STORAGE_SAVE_IN_PROGRESS','PERSISTED_STORAGE_CHANGED_DURING_READ','PERSISTED_STORAGE_CHUNK_NOT_FOUND')) 'unexpected persisted read failure'
        $storage[$domain]=$errorBody.error
    }
}
Acquire
Json POST '/v0/diagnostics/ui/test-screen' $null $control|Out-Null
Json POST '/v0/wait/until' @{condition=@{type='screen';classContains='AutomationProbeScreen'};timeoutMs=5000}|Out-Null
Release
$previous=ImageSamples (Capture 'reference-0.png')
$reference=$null
for($attempt=1;$attempt-le10;$attempt++){
    $path=Capture "reference-$attempt.png"
    $candidate=ImageSamples $path
    if((ComparePixels $previous $candidate)-eq0){$reference=$candidate;break}
    $previous=$candidate
}
Require ($null-ne$reference) 'ordinary content was not stable enough for a pixel oracle'
Acquire
$recording=Json POST '/v0/recordings' @{intervalMs=75;durationMs=10000;maxSamples=128;captureFrames=$true;stateReads=@(@{providerId='minecraft:client/player'});contactSheet=@{enabled=$true;columns=4;cellWidth=160;cellHeight=90}}
$http=[Net.Http.HttpClient]::new()
$http.Timeout=[TimeSpan]::FromSeconds(15)
$http.DefaultRequestHeaders.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$token)
$paths=[Collections.Generic.List[string]]::new()
try{
    for($round=0;$round-lt3;$round++){
        # Respect the production capture bucket (burst 8, refill 4/s).
        # This is client pacing, not a substitute for any game-state assertion.
        Start-Sleep -Milliseconds 2100
        $tasks=@(1..8|ForEach-Object{$http.GetByteArrayAsync("$base/v0/capture")})
        Release
        Acquire
        for($i=0;$i-lt$tasks.Count;$i++){
            $bytes=$tasks[$i].GetAwaiter().GetResult()
            $path=Join-Path $output "concurrent-$round-$i.png"
            [IO.File]::WriteAllBytes($path,$bytes)
            $paths.Add($path)
            Require ((ComparePixels $reference (ImageSamples $path))-eq0) 'Operator Chrome leaked into a concurrent evidence capture'
        }
    }
}finally{$http.Dispose()}
Json DELETE "/v0/recordings/$($recording.recordingId)"|Out-Null
Json POST '/v0/wait/until' @{condition=@{type='recording';recordingId=$recording.recordingId;expected=@{status='completed'}};timeoutMs=15000}|Out-Null
$status=Json GET "/v0/recordings/$($recording.recordingId)"
Require ($status.writtenFrames-gt0) 'recording produced no frames'
$bundle=Join-Path $output 'bundle.zip'
Invoke-WebRequest "$base/v0/recordings/$($recording.recordingId)/artifact" -Headers $auth -OutFile $bundle -TimeoutSec 20
$zip=[IO.Compression.ZipFile]::OpenRead($bundle)
$checkedFrames=0
try{
    foreach($entry in $zip.Entries|Where-Object FullName -match '^frames/.*\.png$'){
        $stream=$entry.Open();$memory=[IO.MemoryStream]::new()
        $stream.CopyTo($memory);$memory.Position=0
        $image=[Drawing.Bitmap]::new($memory)
        try{Require ((ComparePixels $reference (PixelSamples $image))-eq0) 'Operator Chrome leaked into a recording frame';$checkedFrames++}
        finally{$image.Dispose();$memory.Dispose();$stream.Dispose()}
    }
    Require ($null-ne$zip.GetEntry('manifest.json') -and $null-ne$zip.GetEntry('checksums.json')) 'recording metadata missing'
}finally{$zip.Dispose()}
Json POST '/v0/ui/action' @{selector=@{label='Close Probe'}} $control|Out-Null
Release
[pscustomobject]@{
    Result='PASS';Target=$ExpectedTarget;ClientUnloaded='PASS';ServerNoLoad='PASS';PersistentReadRunning=$storage
    ConcurrentCaptures=$paths.Count;RecordingFramesCompared=$checkedFrames;ChromePixelDifferences=0
    RecordingId=$recording.recordingId;Gaps=$status.gaps;Bundle=$bundle;PersistentWriteInvocations=0
    EvidencePlane='diagnostic_capture_isolation';Arrange='standard_GUI_fixture'
}
