$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'InstanceProfiles.ps1')
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$checks=0
function Check([bool]$Value,[string]$Message){if(-not$Value){throw $Message};$script:checks++}
foreach($kind in @('automatic','acceptance')){
    $text="lang:zh_cn`r`nsoundCategory_master:1.0`r`nsoundCategory_master:0.9`r`nsoundCategory_music:0.7`r`nother:preserved`r`n"
    $out=Get-InstanceProfileOptions $text $kind
    Check ($out-eq(Get-InstanceProfileOptions $out $kind)) "$kind must be idempotent"
    Check ($out.Contains('lang:zh_cn')-and$out.Contains('other:preserved')) "$kind must preserve other settings"
    Check (-not$out.Contains("`r")-and$out.EndsWith("`n")-and-not$out.EndsWith("`n`n")) "$kind must use normalized text"
    Check (([regex]::Matches($out,'(?m)^soundCategory_master:')).Count-eq1) "$kind duplicate master volume"
    Check ($out.Contains($(if($kind-eq'automatic'){'soundCategory_master:0.0'}else{'soundCategory_master:0.5'}))) "$kind wrong master volume"
    if($kind-eq'acceptance'){Check ($out.Contains('soundCategory_music:0.0')) 'Acceptance music must be muted'}
}
Check ((Get-InstanceProfileOptions '' automatic).Contains('soundCategory_master:0.0')) 'Empty automatic options'
Check ((Get-InstanceProfileOptions '' acceptance).Contains('soundCategory_music:0.0')) 'Empty acceptance options'
foreach($file in Get-ChildItem (Join-Path $repo 'validations') -Filter validation.toml -Recurse){
    if($file.FullName-match'[/\\](instance|result)[/\\]'){continue}
    $text=[IO.File]::ReadAllText($file.FullName)
    foreach($role in [regex]::Matches($text,'(?ms)^\[instances\.(client(?:-multiplayer)?)\]\r?\n(?<body>.*?)(?=^\[|\z)')){
        $manual=$file.Directory.Name-eq'feature-control_ui_showcase'-and$role.Groups[1].Value-eq'client'
        $width=if($manual){2560}else{1920};$height=if($manual){1440}else{1080}
        Check ($role.Groups['body'].Value-match"(?m)^width = $width`r?`$") "$($file.Directory.Name) role width"
        Check ($role.Groups['body'].Value-match"(?m)^height = $height`r?`$") "$($file.Directory.Name) role height"
    }
}
Write-Output "PASS: $checks instance profile checks; audio idempotency, unknown settings, and all declared client dimensions"
