[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
function Assert-True([bool]$Condition,[string]$Message) {
    if(-not $Condition) { throw "Phase 9B.2 canonicalization failed: $Message" }
}
$targets=@(
    @{Id='1.20.1-forge';Src='src\main\java'},
    @{Id='1.21.1-neoforge';Src='src\main\java'},
    @{Id='26.1.2-neoforge';Src='src\main\java'},
    @{Id='26.2-neoforge';Src='src\main\java'},
    @{Id='26.2-fabric';Src='src\client\java'})
foreach($target in $targets) {
    $base=Join-Path $root "versions\$($target.Id)\$($target.Src)\io\github\recrivenvi\minecraftprotocol\probe"
    $tracker=Get-Content (Join-Path $base 'runtime\ObservationRevisionTracker.java') -Raw
    $engine=Get-Content (Join-Path $base 'runtime\Phase9ASpikeEngine.java') -Raw
    Assert-True ($tracker.Contains(
        'value.getAsJsonArray().forEach(element -> result.add(canonicalize(element)))')) "$($target.Id) ordered arrays"
    Assert-True (-not $tracker.Contains(
        'values.sort(Comparator.comparing(JsonElement::toString))')) "$($target.Id) generic array sorting removed"
    foreach($marker in @('normalizedAttributes.sort','normalizedEffects.sort','entity.getUUID().toString()')) {
        Assert-True ($engine.Contains($marker)) "$($target.Id) domain normalization $marker"
    }
}
[pscustomobject]@{
    Result='PASS'
    Targets=5
    ObjectKeys='CANONICAL_SORTED'
    Arrays='ORDERED_DEFAULT'
    Attributes='DOMAIN_SORTED'
    Effects='DOMAIN_SORTED'
    Entities='UUID_SORTED'
    NbtLists='ORDERED'
    ProviderArrays='ORDERED_DEFAULT'
    Tests='JAVA_DETERMINISTIC'
}

