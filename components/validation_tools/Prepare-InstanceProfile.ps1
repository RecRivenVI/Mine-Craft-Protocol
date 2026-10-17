[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$Directory,
    [Parameter(Mandatory)][ValidateSet('automatic','acceptance')][string]$Kind
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $PSScriptRoot 'RuntimeConnection.ps1')
. (Join-Path $PSScriptRoot 'InstanceProfiles.ps1')
$resolved=[IO.Path]::GetFullPath($Directory)
$relative=[IO.Path]::GetRelativePath($repo,$resolved).Replace('\','/')
if($relative-notmatch'^(instances|validations/[a-z0-9_-]+/instance)/(1\.20\.1-forge|1\.21\.1-neoforge|26\.1\.2-neoforge|26\.3-neoforge|26\.3-fabric)/(client|client-multiplayer)$'){throw 'Only canonical project client instances may receive a profile'}
$cursor=$resolved
while($cursor-ne$repo){
    if((Test-Path -LiteralPath $cursor)-and((Get-Item -LiteralPath $cursor -Force).Attributes-band[IO.FileAttributes]::ReparsePoint)){throw 'Instance profile refuses linked paths'}
    $cursor=[IO.Path]::GetDirectoryName($cursor)
}
Assert-RuntimeInstanceStopped $resolved
[IO.Directory]::CreateDirectory($resolved)|Out-Null
$options=Join-Path $resolved 'options.txt'
$text=if(Test-Path -LiteralPath $options){[IO.File]::ReadAllText($options)}else{''}
[IO.File]::WriteAllText($options,(Get-InstanceProfileOptions $text $Kind),[Text.UTF8Encoding]::new($false))
