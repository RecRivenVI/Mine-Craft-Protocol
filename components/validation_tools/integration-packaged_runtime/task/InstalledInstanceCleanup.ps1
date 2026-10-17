function Retire-InstalledRuntimeInstance {
    [CmdletBinding()]
    param([string]$Root,[string]$ExpectedParent,[string]$Target,[string]$ShortParent,[scriptblock]$RecycleAction)
    $resolved=[IO.Path]::GetFullPath($Root)
    $parent=[IO.Path]::GetFullPath($ExpectedParent).TrimEnd('\','/')
    if([IO.Path]::GetDirectoryName($resolved)-ine$parent-or[IO.Path]::GetFileName($resolved)-cne$Target){throw 'Refusing cleanup outside owned installed Target'}
    $ownerFile=Join-Path $resolved 'owner.json'
    if(-not(Test-Path -LiteralPath $ownerFile)){throw 'Unknown installed instance retained'}
    $owner=Get-Content -LiteralPath $ownerFile -Raw|ConvertFrom-Json
    if($owner.owner-ne'packaged-runtime-clean-install'-or$owner.target-ne$Target){throw 'Installed instance owner or Target mismatch'}
    Assert-RuntimeInstanceStopped (Join-Path $resolved 'client')
    foreach($item in @((Get-Item -LiteralPath $resolved -Force)) + @(Get-ChildItem -LiteralPath $resolved -Recurse -Force)){
        if(($item.Attributes-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'Reparse point in installed instance; retained'}
    }
    $shortParentPath=(Resolve-Path -LiteralPath $ShortParent).Path.TrimEnd('\','/')
    if([IO.Path]::GetPathRoot($shortParentPath)-ine[IO.Path]::GetPathRoot($resolved)){throw 'Retirement staging must remain on the same volume'}
    $staged=[IO.Path]::GetFullPath((Join-Path $shortParentPath ('.installed-runtime-'+[guid]::NewGuid().ToString('N').Substring(0,12))))
    if([IO.Path]::GetDirectoryName($staged)-ine$shortParentPath-or(Test-Path -LiteralPath $staged)){throw 'Unsafe or existing retirement staging path'}
    # Shell recycling rejects long descendant paths; rename the owned tree on the same volume first.
    [IO.Directory]::Move($resolved,$staged)
    try{
        if($RecycleAction){& $RecycleAction $staged|Out-Null}
        else{
            Add-Type -AssemblyName Microsoft.VisualBasic
            [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($staged,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)
        }
        if(Test-Path -LiteralPath $staged){throw 'Recycle operation left its source in place'}
    }catch{
        Write-Warning "Old owned installation retained, never permanently deleted: $staged : $($_.Exception.Message)"
        return $staged
    }
    return $null
}
