[CmdletBinding()]param([Parameter(Mandatory)][string]$CacheDirectory)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$catalog=[IO.File]::ReadAllText((Join-Path $repo 'inputs.toml'))
$localFile=Join-Path $repo 'local.toml'
$localText=if(Test-Path -LiteralPath $localFile){[IO.File]::ReadAllText($localFile).Replace("`r`n","`n")}else{''}
$entries=@()
foreach($name in @('customskinloader-bootstrap','zume')){
    $table=[regex]::Match($catalog,'(?ms)^\['+[regex]::Escape($name)+'\]\r?\n(?<body>.*?)(?=^\[|\z)')
    if(-not$table.Success){throw "Missing named acceptance input: $name"}
    $source=[regex]::Match($table.Groups['body'].Value,'(?m)^source = "https://modrinth.com/mod/[a-z0-9_-]+/version/([A-Za-z0-9]+)"\r?$')
    $hash=[regex]::Match($table.Groups['body'].Value,'(?m)^sha256 = \["([0-9a-f]{64})"\]\r?$')
    if(-not$source.Success-or-not$hash.Success){throw "Unsupported acceptance input declaration: $name"}
    $release=Invoke-RestMethod "https://api.modrinth.com/v2/version/$($source.Groups[1].Value)" -TimeoutSec 30
    $files=@($release.files|Where-Object primary)
    if($files.Count-ne1){throw "Expected one primary Modrinth artifact: $name"}
    $file=$files[0];$uri=[Uri]$file.url
    if($uri.Scheme-ne'https'-or$uri.Host-ne'cdn.modrinth.com'-or[IO.Path]::GetFileName($file.filename)-ne$file.filename){throw 'Unsafe Modrinth artifact path'}
    [IO.Directory]::CreateDirectory($CacheDirectory)|Out-Null
    $destination=Join-Path $CacheDirectory $file.filename
    if(-not(Test-Path -LiteralPath $destination)){
        $temporary=$destination+'.download-'+[Guid]::NewGuid().ToString('N')
        Invoke-WebRequest $uri -OutFile $temporary -TimeoutSec 90
        if((Get-FileHash -LiteralPath $temporary -Algorithm SHA512).Hash.ToLowerInvariant()-ne$file.hashes.sha512-or(Get-FileHash -LiteralPath $temporary).Hash.ToLowerInvariant()-ne$hash.Groups[1].Value){throw "Checksum mismatch; retained $temporary"}
        [IO.File]::Move($temporary,$destination)
    }
    if((Get-FileHash -LiteralPath $destination).Hash.ToLowerInvariant()-ne$hash.Groups[1].Value){throw "Unknown cache content retained: $destination"}
    $entries+=@{name=$name;path=[IO.Path]::GetFullPath($destination).Replace('\','/')}
}
$section=[regex]::Match($localText,'(?ms)^\[inputs\]\n(?<body>.*?)(?=^\[|\z)')
$body=if($section.Success){$section.Groups['body'].Value}else{''}
foreach($entry in $entries){
    # Preserve existing user paths; the native verifyInputs task validates their content.
    if($body-notmatch('(?m)^'+[regex]::Escape($entry.name)+'\s*=')){$body=$body.TrimEnd("`n")+"`n"+$entry.name+' = '+($entry.path|ConvertTo-Json -Compress)+"`n"}
}
if($section.Success){$localText=$localText.Substring(0,$section.Groups['body'].Index)+$body+ $localText.Substring($section.Groups['body'].Index+$section.Groups['body'].Length)}
else{$localText=$localText.TrimEnd("`n")+"`n`n[inputs]`n"+$body.TrimStart("`n")}
[IO.File]::WriteAllText($localFile,$localText.TrimStart("`n").TrimEnd("`n")+"`n",[Text.UTF8Encoding]::new($false))
$entries|ForEach-Object {[pscustomobject]@{Input=$_.name;Status='MODRINTH_CHECKSUM_VERIFIED'}}
