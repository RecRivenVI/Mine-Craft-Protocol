[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BaseUri,
    [Parameter(Mandatory = $true)][string]$TokenFile,
    [Parameter(Mandatory = $true)][string]$ExpectedTarget
)

$ErrorActionPreference = 'Stop'
$base = $BaseUri.TrimEnd('/')
$token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth = @{ Authorization = "Bearer $token" }

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 8 recording hardening failed: $Message" }
}

function Invoke-Json {
    param([ValidateSet('GET','POST','DELETE')][string]$Method, [string]$Path,
        [hashtable]$Headers = $auth, [object]$Body)
    $parameters = @{ Uri = "$base$Path"; Method = $Method; Headers = $Headers }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress
    }
    Invoke-RestMethod @parameters
}

$session = Invoke-Json GET '/v0/session'
Assert-True ($session.target -eq $ExpectedTarget) "expected $ExpectedTarget"
Start-Sleep -Seconds 3
$recording = Invoke-Json POST '/v0/recordings' -Body @{
    intervalMs = 50
    durationMs = 3200
    maxSamples = 512
    captureFrames = $true
    stateReads = @()
    contactSheet = @{
        enabled = $true
        columns = 16
        cellWidth = 1024
        cellHeight = 1024
        spacing = 32
    }
}
Assert-True ($recording.lifecycle -eq 'RUNNING') 'recording lifecycle must start at RUNNING'

$lease = Invoke-Json POST '/v0/control/acquire' -Body @{ ttlMs = 10000 }
$leaseHeaders = @{ Authorization = "Bearer $token"; 'X-MCP-Control-Lease' = $lease.leaseId }
try {
    $pipeline = Invoke-Json POST '/v0/pipelines' -Headers $leaseHeaders -Body @{
        timeoutMs = 5000
        steps = @(
            @{ type = 'mouse.move'; x = 2; y = 2 },
            @{ type = 'key.tap'; key = 340; scanCode = 42; holdMs = 25 },
            @{ type = 'mouse.move'; x = 100; y = 50 }
        )
    }
    $operation = Invoke-Json POST "/v0/operations/$($pipeline.operationId)/wait" -Body @{ timeoutMs = 5000 }
    Assert-True ($operation.state -eq 'completed') 'recording and input must proceed concurrently'
}
finally { Invoke-Json POST '/v0/control/release' -Headers $leaseHeaders | Out-Null }

$deadline = [DateTime]::UtcNow.AddSeconds(30)
do {
    Start-Sleep -Milliseconds 200
    $status = Invoke-Json GET "/v0/recordings/$($recording.recordingId)"
} while (-not [bool]$status.artifactReady -and $status.status -ne 'failed' -and [DateTime]::UtcNow -lt $deadline)
Assert-True ($status.status -eq 'completed' -and $status.lifecycle -eq 'CLOSED') 'recording must finalize through CLOSED'
Assert-True ([bool]$status.artifactReady -and [long]$status.writtenBytes -gt 0) 'Artifact must be ready within total byte budget'

$temporary = [IO.Path]::GetTempFileName()
$handler = [Net.Http.SocketsHttpHandler]::new()
$client = [Net.Http.HttpClient]::new($handler)
try {
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get,
        "$base/v0/recordings/$($recording.recordingId)/artifact")
    $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
    $response = $client.SendAsync($request, [Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    Assert-True ($response.IsSuccessStatusCode) 'Artifact streaming response must succeed'
    Assert-True ($null -ne $response.Content.Headers.ContentLength -and $response.Content.Headers.ContentLength -gt 0) 'Artifact stream must declare Content-Length'
    $file = [IO.File]::Open($temporary, [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { [void]$response.Content.CopyToAsync($file).GetAwaiter().GetResult() }
    finally { $file.Dispose() }
    Assert-True ((Get-Item -LiteralPath $temporary).Length -eq $response.Content.Headers.ContentLength) 'streamed byte count must match Content-Length'
    $archive = [IO.Compression.ZipFile]::OpenRead($temporary)
    try {
        $manifestEntry = $archive.GetEntry('manifest.json')
        Assert-True ($null -ne $manifestEntry) 'Artifact must contain manifest.json'
        $reader = [IO.StreamReader]::new($manifestEntry.Open(), [Text.Encoding]::UTF8)
        try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json }
        finally { $reader.Dispose() }
        Assert-True ($manifest.contactSheetArtifacts.sheetCount -ge 2) 'largest Contact Sheet dimensions must split into multiple safe sheets'
        Assert-True ($manifest.contactSheetArtifacts.maxSheetPixels -eq 33554432) 'manifest must record aggregate pixel budget'
        Assert-True ($manifest.contactSheetArtifacts.maxRecordingBytes -eq 536870912) 'manifest must record Session byte budget'
        $sheetEntries = @($archive.Entries | Where-Object FullName -like 'derivatives/contact-sheet*.png')
        Assert-True ($sheetEntries.Count -eq $manifest.contactSheetArtifacts.sheetCount) 'sheet manifest and Artifact entries must agree'
    }
    finally { $archive.Dispose(); $response.Dispose() }
}
finally {
    $client.Dispose()
    $handler.Dispose()
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
}

[pscustomobject]@{
    Target = $ExpectedTarget
    LargestValidConfigAccepted = 'PASS'
    SplitSheets = $manifest.contactSheetArtifacts.sheetCount
    WrittenFrames = $status.writtenFrames
    WrittenBytes = $status.writtenBytes
    ArtifactStreaming = 'PASS'
    Lifecycle = $status.lifecycle
    Result = 'PASS'
}
