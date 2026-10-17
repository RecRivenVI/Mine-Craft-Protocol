[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
$runtimeRunnerLock=$null

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 8 sequential live gate failed: $Message" }
}

$runs = @(
    @{ Target='1.20.1-forge'; Task=':version:1.20.1-forge:runValidationRegressionV1ReleaseClient'; Directory='validations\regression-v1_release\instance\1.20.1-forge\client'; Port=$null; Backend='any'; World=$true },
    @{ Target='1.21.1-neoforge'; Task=':version:1.21.1-neoforge:runValidationRegressionV1ReleaseClient'; Directory='validations\regression-v1_release\instance\1.21.1-neoforge\client'; Port=$null; Backend='any'; World=$true },
    @{ Target='26.1.2-neoforge'; Task=':version:26.1.2-neoforge:runValidationRegressionV1ReleaseClient'; Directory='validations\regression-v1_release\instance\26.1.2-neoforge\client'; Port=$null; Backend='any'; World=$true },
    @{ Target='26.3-neoforge'; Task=':version:26.3-neoforge:runValidationRegressionV1ReleaseClient'; Directory='validations\regression-v1_release\instance\26.3-neoforge\client'; Port=$null; Backend='opengl'; World=$true },
    @{ Target='26.3-neoforge'; Task=':version:26.3-neoforge:runValidationRegressionVulkanClient'; Directory='validations\regression-vulkan\instance\26.3-neoforge\client'; Port=$null; Backend='vulkan'; World=$false },
    @{ Target='26.3-fabric'; Task=':version:26.3-fabric:runValidationRegressionV1ReleaseClient'; Directory='validations\regression-v1_release\instance\26.3-fabric\client'; Port=$null; Backend='opengl'; World=$true },
    @{ Target='26.3-fabric'; Task=':version:26.3-fabric:runValidationRegressionVulkanClient'; Directory='validations\regression-vulkan\instance\26.3-fabric\client'; Port=$null; Backend='vulkan'; World=$false }
)

Push-Location $root
try {
    $env:MCP_RUNTIME_SCOPES = 'read,ui,input,capture,event,diagnostics,control,command,fixture,debug'
    $results = foreach ($run in $runs) {
        $directory = [IO.Path]::GetFullPath((Join-Path $root $run.Directory))
        [void][IO.Directory]::CreateDirectory($directory)
        if($runtimeRunnerLock){$runtimeRunnerLock.Dispose()};$runtimeRunnerLock=Enter-RuntimeRunner $directory
        if ($run.Backend -eq 'vulkan' -and $run.Target -eq '26.3-neoforge') {
            $config = Join-Path $directory 'config'
            [void][IO.Directory]::CreateDirectory($config)
            $fml = Join-Path $config 'fml.toml'
            if (Test-Path $fml) {
                $fmlText = Get-Content $fml -Raw
                if ($fmlText -match '(?m)^earlyWindowControl\s*=') {
                    $fmlText = $fmlText -replace '(?m)^earlyWindowControl\s*=.*$', 'earlyWindowControl=false'
                } else { $fmlText += "\nearlyWindowControl=false\n" }
            } else { $fmlText = "earlyWindowControl=false\n" }
            [IO.File]::WriteAllText($fml, $fmlText.Replace('\n', [Environment]::NewLine))
        }
        $process = Start-Process -FilePath '.\gradlew.bat' `
            -ArgumentList $run.Task,'--no-daemon','--offline' `
            -WorkingDirectory $root `
            -RedirectStandardOutput (Join-Path $directory 'phase8-sequential-live-stdout.log') `
            -RedirectStandardError (Join-Path $directory 'phase8-sequential-live-stderr.log') `
            -PassThru -WindowStyle Hidden
        $tokenFile = Join-Path $directory 'minecraft-protocol\token'
        $deadline = [DateTime]::UtcNow.AddMinutes(3)
        $session = $null
        $connection=Wait-RuntimeConnection $directory $run.Target $process.StartTime.ToUniversalTime() $process
        $run.Port=$connection.Port;$token=(Get-Content $tokenFile -Raw).Trim()
        $session=Invoke-RestMethod "$($connection.BaseUri)/v0/session" -Headers $connection.Headers -TimeoutSec 3
        Assert-True ($session.target -eq $run.Target) "$($run.Target)/$($run.Backend) did not become ready"

        $parameters = @{
            BaseUri = "http://127.0.0.1:$($run.Port)"
            TokenFile = $tokenFile
            ExpectedTarget = $run.Target
            ExpectedBackend = $run.Backend
        }
        if ($run.World) {
            $parameters.EnterWorld = $true
            $parameters.RequireAuthoritative = $true
        }
        $smoke = & '.\components\validation_tools\regression-v1_release\task\Invoke-Phase8TargetSmoke.ps1' @parameters |
            Where-Object { $_.Result }
        Assert-True ($smoke.Result -eq 'PASS') "$($run.Target)/$($run.Backend) smoke failed"

        $auth = $connection.Headers
        for ($attempt = 0; $attempt -lt 3; $attempt++) {
            try {
                Invoke-RestMethod "http://127.0.0.1:$($run.Port)/v0/control/emergency-release" -Method Post -Headers $auth | Out-Null
                $lease = Invoke-RestMethod "http://127.0.0.1:$($run.Port)/v0/control/acquire" -Method Post `
                    -Headers $auth -ContentType 'application/json' -Body '{"ttlMs":10000}'
                $headers = @{ Authorization = "Bearer $token"; 'X-MCP-Control-Lease' = $lease.leaseId }
                Invoke-RestMethod "http://127.0.0.1:$($run.Port)/v0/ui/action" -Method Post `
                    -Headers $headers -ContentType 'application/json' `
                    -Body '{"action":"click","holdMs":100,"selector":{"role":"button","label":"Quit Game"}}' | Out-Null
            } catch { }
            Start-Sleep -Seconds 4
            if (-not (Get-NetTCPConnection -LocalPort $run.Port -State Listen -ErrorAction SilentlyContinue)) { break }
        }
        $cleanShutdown = -not [bool](Get-NetTCPConnection -LocalPort $run.Port -State Listen -ErrorAction SilentlyContinue)
        Assert-True $cleanShutdown "$($run.Target)/$($run.Backend) did not shut down cleanly"
        [pscustomobject]@{
            Target = $run.Target
            Backend = $smoke.Capture
            World = $smoke.World
            Build = 'PASS'
            Launch = $smoke.Launch
            Readiness = $smoke.Readiness
            UI = $smoke.UI
            Input = $smoke.Input
            Capture = 'PASS'
            WebSocket = $smoke.WebSocket
            Shutdown = 'PASS'
            Result = 'PASS'
        }
    }
    [pscustomobject]@{
        Result = 'PASS'
        Runs = $results.Count
        Targets = @($results.Target | Select-Object -Unique).Count
        WorldRuns = @($results | Where-Object World -eq 'PASS').Count
        VulkanRuns = @($results | Where-Object Backend -eq 'vulkan').Count
        Results = $results
    }
}
finally { if($runtimeRunnerLock){$runtimeRunnerLock.Dispose()};Pop-Location }
