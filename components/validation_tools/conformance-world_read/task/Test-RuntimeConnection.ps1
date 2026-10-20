[CmdletBinding()]param()
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$root=Join-Path $repo ('components/validation_tools/build/discovery-tests/'+[guid]::NewGuid().ToString('N'))
$game=Join-Path $root 'client'
$directory=Join-Path $game 'minecraft-protocol'
[IO.Directory]::CreateDirectory($directory)|Out-Null
$self=Get-Process -Id $PID
$script:record=@{schemaVersion=1;runtimeInstanceId=[guid]::NewGuid().ToString();target='test-target';gameDirectory=$game;pid=$PID;processStartedAt=$self.StartTime.ToUniversalTime().ToString('o');startedAt=[DateTime]::UtcNow.ToString('o');tokenFile='token';port=12345;baseUrl='http://127.0.0.1:12345';state='RUNNING'}
[IO.File]::WriteAllText((Join-Path $directory 'token'),'synthetic-discovery-token')
function Write-Record {[IO.File]::WriteAllText((Join-Path $directory 'endpoint.json'),($script:record|ConvertTo-Json))}
function Reject([scriptblock]$Action){$rejected=$false;try{& $Action|Out-Null}catch{$rejected=$true};if(-not$rejected){throw 'Unsafe Runtime connection accepted'}}
$script:requests=0
function Invoke-RestMethod {
    param($Uri,$Headers,$TimeoutSec,$MaximumRedirection,$ResponseHeadersVariable)
    if($Headers['X-MCP-Runtime-Instance']-ne$script:record.runtimeInstanceId-or$Headers.Authorization-ne'Bearer synthetic-discovery-token'){throw 'Missing authentication or instance pin'}
    if($Uri-ne$script:record.baseUrl+'/v0/session'-or$MaximumRedirection-ne0){throw 'Unexpected handshake route'}
    Set-Variable -Name $ResponseHeadersVariable -Value @{'X-MCP-Runtime-Instance'=$script:record.runtimeInstanceId} -Scope 1
    $script:requests++
    @{target='test-target';runtimeConnection=@{runtimeInstanceId=$script:record.runtimeInstanceId}}
}
Write-Record
$connection=Get-RuntimeConnection $game 'test-target' -NotBefore ([DateTime]::UtcNow.AddMinutes(-1))
if($connection.ProcessId-ne$PID-or$connection.Port-ne12345-or$requests-ne1){throw 'Current UTC process identity or handshake failed'}
$tokens=$null;$errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Invoke-WorldReadSmoke.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'World runner syntax failed'}
$ownerFunction=$ast.Find({param($node) $node-is[System.Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-eq'Test-WorldReceiptOwner'},$true)
if(-not$ownerFunction){throw 'Missing actual world result owner validation'}
. ([scriptblock]::Create($ownerFunction.Extent.Text))
$report=@{runtimeConnection=@{runtimeInstanceId=$connection.RuntimeInstanceId;pid=$connection.ProcessId;port=$connection.Port}}
if(-not(Test-WorldReceiptOwner $report $connection)){throw 'Current result identity rejected'}
if((Test-WorldReceiptOwner $report $null)-or(Test-WorldReceiptOwner @{} $connection)){throw 'Launch failure or missing result identity may not annotate old evidence'}
foreach($field in @('runtimeInstanceId','pid','port')){
    $saved=$report.runtimeConnection[$field];$report.runtimeConnection[$field]=if($field-eq'runtimeInstanceId'){[guid]::NewGuid().ToString()}else{[int]$saved+1}
    if(Test-WorldReceiptOwner $report $connection){throw "Foreign world result accepted: $field"}
    $report.runtimeConnection[$field]=$saved
}
Reject {Get-RuntimeConnection $game 'wrong-target'}
Reject {Get-RuntimeConnection $game 'test-target' -NotBefore ([DateTime]::UtcNow.AddMinutes(1))}
$original=$record.processStartedAt;$record.processStartedAt=$self.StartTime.ToUniversalTime().AddMinutes(-1).ToString('o');Write-Record
Reject {Get-RuntimeConnection $game 'test-target'}
$record.processStartedAt=$original;$record.baseUrl='http://192.0.2.1:12345';Write-Record
Reject {Get-RuntimeConnection $game 'test-target'}
$record.baseUrl='http://127.0.0.1:12345';Write-Record
if($requests-ne1){throw 'Rejected discovery performed unexpected network access'}
$runner=Enter-RuntimeRunner $game
try{Reject {Enter-RuntimeRunner $game}}finally{$runner.Dispose()}
$runner=Enter-RuntimeRunner $game;$runner.Dispose()
[IO.File]::WriteAllText((Join-Path $directory 'endpoint.json'),('x'*16385))
Reject {Get-RuntimeConnection $game 'test-target'}
Write-Output 'PASS: UTC process identity, instance/Target pin, stale/future/unsafe/oversize rejection, zero rejected dispatch and duplicate Runner lock'
