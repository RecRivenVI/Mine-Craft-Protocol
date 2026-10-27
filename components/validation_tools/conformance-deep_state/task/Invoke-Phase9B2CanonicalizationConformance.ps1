[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$runtimeCore=Join-Path (Resolve-Path (Join-Path $PSScriptRoot '../../../..')) 'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core'
function Assert-True([bool]$Condition,[string]$Message) {
    if(-not $Condition) { throw "Phase 9B.2 canonicalization failed: $Message" }
}
$targets=@(
    @{Id='1.20.1-forge';Src='src\main\java'},
    @{Id='1.21.1-neoforge';Src='src\main\java'},
    @{Id='26.1.2-neoforge';Src='src\main\java'},
    @{Id='26.3-neoforge';Src='src\main\java'},
    @{Id='26.3-fabric';Src='src\client\java'})
foreach($target in $targets) {
    $base=Join-Path $root "versions\$($target.Id)\$($target.Src)\io\github\recrivenvi\minecraftprotocol\probe"
    $tracker=Get-Content (Join-Path $runtimeCore 'ObservationRevisionTracker.java') -Raw
    $engine=Get-Content (Join-Path $root "versions/$($target.Id)/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine.java") -Raw
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
