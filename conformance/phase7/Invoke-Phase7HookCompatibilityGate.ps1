[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Phase 7 Hook gate assertion failed: $Message" }
}

$targets = @(
    [pscustomobject]@{ Name = '1.20.1-forge'; Source = 'src\main\java'; Config = 'src\main\resources\minecraft_protocol_probe.mixins.json' },
    [pscustomobject]@{ Name = '1.21.1-neoforge'; Source = 'src\main\java'; Config = 'src\main\resources\minecraft_protocol_probe.mixins.json' },
    [pscustomobject]@{ Name = '26.1.2-neoforge'; Source = 'src\main\java'; Config = 'src\main\resources\minecraft_protocol_probe.mixins.json' },
    [pscustomobject]@{ Name = '26.2-neoforge'; Source = 'src\main\java'; Config = 'src\main\resources\minecraft_protocol_probe.mixins.json' },
    [pscustomobject]@{ Name = '26.2-fabric'; Source = 'src\client\java'; Config = 'src\client\resources\minecraft_protocol_probe.client.mixins.json' }
)

$results = foreach ($target in $targets) {
    $mixinDirectory = Join-Path $repositoryRoot "versions\$($target.Name)\$($target.Source)\io\github\recrivenvi\minecraftprotocol\probe\mixin"
    $files = @(Get-ChildItem -LiteralPath $mixinDirectory -Filter '*.java' -File)
    Assert-True ($files.Count -gt 0) "$($target.Name) must own concrete Hook sources"
    $texts = @($files | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw })
    $combined = $texts -join "`n"

    Assert-True ($combined -notmatch '@Overwrite') "$($target.Name) must not use Overwrite"
    Assert-True ($combined -notmatch 'cancellable\s*=\s*true') "$($target.Name) must not cancel injections"
    Assert-True ($combined -notmatch '\bci\.cancel\s*\(') "$($target.Name) must not call CallbackInfo.cancel"
    Assert-True ($combined -notmatch '@Redirect|@ModifyArg|@ModifyArgs|@ModifyVariable|@ModifyConstant') "$($target.Name) must avoid replacement-style Hook mechanisms in V1"
    Assert-True ($combined -notmatch '@Mixin\s*\(\s*targets\s*=') "$($target.Name) must use typed Minecraft targets"
    foreach ($file in $files) {
        $text = Get-Content -LiteralPath $file.FullName -Raw
        Assert-True ($text -match 'import net\.minecraft\.') "$($target.Name)/$($file.Name) must target Minecraft, not a third-party Mod"
    }

    $configPath = Join-Path $repositoryRoot "versions\$($target.Name)\$($target.Config)"
    $config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    Assert-True ([bool]$config.required) "$($target.Name) Mixin config must fail visibly when a required Hook cannot apply"
    Assert-True ($config.injectors.defaultRequire -eq 1) "$($target.Name) Hook injections must not silently skip"
    Assert-True (@($config.client).Count -eq $files.Count) "$($target.Name) Mixin config and owned Hook source count must agree"

    [pscustomobject]@{
        Target = $target.Name
        HookSources = $files.Count
        Injects = ([regex]::Matches($combined, '@Inject\s*\(')).Count
        Invokers = ([regex]::Matches($combined, '@Invoker\s*\(')).Count
        Accessors = ([regex]::Matches($combined, '@Accessor\s*\(')).Count
        Overwrites = 0
        Cancellable = 0
        ThirdPartyTargets = 0
        Result = 'PASS'
    }
}

[pscustomobject]@{
    Result = 'PASS'
    Policy = 'capability_fidelity_first'
    Targets = $results
}
