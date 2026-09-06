#requires -Version 7.0
[CmdletBinding()]param([switch]$Offline,[switch]$SkipGradle)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
function Require([bool]$Condition,[string]$Reason){if(-not$Condition){throw "Repository structure: $Reason"}}
Push-Location $root
try {
    foreach($old in @('conformance','Artifacts','docs','runtime-safety','protocol-schema','companion','runs','probes')){
        Require (-not(Test-Path -LiteralPath $old)) "old canonical root still exists: $old"
    }
    $settings=Get-Content settings.gradle.kts -Raw
    foreach($component in @('runtime-safety','protocol-schema')){
        Require ($settings.Contains('includeComponent("'+$component+'")')) "missing component $component"
    }
    Require (-not$settings.Contains('includeComponent("companion")')) 'Companion must stay independent Node/TypeScript'
    foreach($target in @('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.2-neoforge','26.2-fabric')){
        Require (Test-Path "versions/$target/build.gradle.kts") "$target Kotlin DSL missing"
        Require (Test-Path "versions/$target/target.properties") "$target facts missing"
        if(Test-Path "instances/$target"){
            foreach($variant in Get-ChildItem "instances/$target" -Directory){Require ($variant.Name-in@('client','client-multiplayer','server')) 'fourth instance variant'}
        }
    }
    $facts=Get-Content gradle.properties -Raw
    Require ($facts-match'(?m)^mod_environment=both$'-and$facts-notmatch'(?m)^(mod_group_id|archives_base_name)=') 'Project Fact aliases/environment'
    foreach($file in Get-ChildItem validations/tests -Recurse -Filter '*.ps1'){
        $tokens=$null;$errors=$null
        [void][Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$errors)
        Require ($errors.Count-eq0) "script parse failure: $($file.Name)"
        # Only resolve paths whose base is the script directory, not a Java/source variable.
        $source=Get-Content $file.FullName -Raw
        foreach($match in [regex]::Matches($source,'Join-Path\s+\$PSScriptRoot\s+[''"]([^''"\r\n]+\.ps1)[''"]')){
            Require (Test-Path -LiteralPath (Join-Path $file.DirectoryName $match.Groups[1].Value)) "broken script dependency: $($file.Name)"
        }
    }
    foreach($path in @('local.properties','.vscode/launch.json','instances/1.20.1-forge/client/saves/example/level.dat','instances/26.2-fabric/client/mods/test.jar','validations/.local/test-state')){
        & git check-ignore --quiet -- $path
        Require ($LASTEXITCODE-eq0) "local runtime file not ignored: $path"
    }
    Require (@(git ls-files .vscode/launch.json).Count-eq0) 'machine IDE launch is still tracked'
    Require ((Get-Content .gitignore -Raw)-notmatch'(?m)^/\.vscode/?$') 'do not ignore the entire official IDE directory'

    # Isolated Git matcher fixture, no project history/world mutation.
    $fixture=Join-Path $root ('validations/.local/ignore-contract-'+[guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $fixture|Out-Null
    & git -C $fixture init --quiet
    Require ($LASTEXITCODE-eq0) 'ignore fixture init'
    $ignore=(Get-Content .gitignore -Raw)+"`n!/instances/1.20.1-forge/client/config/approved-example.json`n"
    [IO.File]::WriteAllText((Join-Path $fixture '.gitignore'),$ignore)
    & git -C $fixture check-ignore --quiet -- 'instances/1.20.1-forge/client/config/approved-example.json'
    Require ($LASTEXITCODE-eq1) 'exact whitelist not usable'
    & git -C $fixture check-ignore --quiet -- 'instances/1.20.1-forge/client/saves/private/level.dat'
    Require ($LASTEXITCODE-eq0) 'whitelist leaked world files'
    if(-not$SkipGradle){
        $arguments=@('projects','verifyInstanceConfiguration','verifyInstanceBindings','--no-daemon')
        if($Offline){$arguments+='--offline'}
        & ./gradlew.bat @arguments | ForEach-Object {Write-Host $_}
        Require ($LASTEXITCODE-eq0) 'Gradle topology/cascade/bindings failed'
    }
    $documentation=& (Join-Path $PSScriptRoot 'Invoke-DocumentationGate.ps1')
    Require ($documentation.Result-eq'PASS') 'Documentation Gate'
    [pscustomobject]@{Result='PASS';Targets=5;Variants=3;GradleComponents=2;Companion='INDEPENDENT';IgnoreAndExactWhitelist='PASS';ScriptPaths='PASS';ProductBehavior='UNCHANGED';RuntimeStarted=$false}
} finally {Pop-Location}
