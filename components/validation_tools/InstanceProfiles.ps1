function Get-InstanceProfileOptions {
    param([AllowEmptyString()][string]$Text,[ValidateSet('automatic','acceptance')][string]$Kind='automatic')
    $lines=[Collections.Generic.List[string]]::new()
    foreach($line in $Text.Replace("`r`n","`n").Split("`n")){
        if($line-and$line-notmatch'^soundCategory_master:'-and-not($Kind-eq'acceptance'-and$line-match'^soundCategory_music:')){$lines.Add($line)}
    }
    if(-not$Text){$lines.Add('lang:en_us');$lines.Add('onboardAccessibility:false')}
    $lines.Add($(if($Kind-eq'acceptance'){'soundCategory_master:0.5'}else{'soundCategory_master:0.0'}))
    if($Kind-eq'acceptance'){$lines.Add('soundCategory_music:0.0')}
    return ($lines-join"`n")+"`n"
}
