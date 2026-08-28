[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 8 sequential live gate failed: $Message" }
}

$runs = @(
    @{ Target='1.20.1-forge'; Task=':versions:1.20.1-forge:runClient'; Directory='runs\1.20.1-forge\client'; Port=25581; Backend='any'; World=$true },
    @{ Target='1.21.1-neoforge'; Task=':versions:1.21.1-neoforge:runClient'; Directory='runs\1.21.1-neoforge\client'; Port=25581; Backend='any'; World=$true },
    @{ Target='26.1.2-neoforge'; Task=':versions:26.1.2-neoforge:runClient'; Directory='runs\26.1.2-neoforge\client'; Port=25582; Backend='any'; World=$true },
    @{ Target='26.2-neoforge'; Task=':versions:26.2-neoforge:runClient'; Directory='runs\26.2-neoforge\client'; Port=25582; Backend='opengl'; World=$true },
    @{ Target='26.2-neoforge'; Task=':versions:26.2-neoforge:runClientVulkan'; Directory='runs\26.2-neoforge\client-vulkan'; Port=25582; Backend='vulkan'; World=$false },
    @{ Target='26.2-fabric'; Task=':versions:26.2-fabric:runClient'; Directory='runs\26.2-fabric\client'; Port=25583; Backend='opengl'; World=$true },
    @{ Target='26.2-fabric'; Task=':versions:26.2-fabric:runClientVulkan'; Directory='runs\26.2-fabric\client-vulkan'; Port=25583; Backend='vulkan'; World=$false }
)

Push-Location $root
try {
    $env:MCP_RUNTIME_SCOPES = 'read,ui,input,capture,event,diagnostics,control,command,fixture,debug'
    $results = foreach ($run in $runs) {
        Assert-True (-not [bool](Get-NetTCPConnection -LocalPort $run.Port -State Listen -ErrorAction SilentlyContinue)) "port $($run.Port) is already occupied"
        $directory = (Resolve-Path -LiteralPath $run.Directory).Path
        $process = Start-Process -FilePath '.\gradlew.bat' `
            -ArgumentList $run.Task,'--no-daemon','--offline' `
            -WorkingDirectory $root `
            -RedirectStandardOutput (Join-Path $directory 'phase8-sequential-live-stdout.log') `
            -RedirectStandardError (Join-Path $directory 'phase8-sequential-live-stderr.log') `
            -PassThru -WindowStyle Hidden
        $tokenFile = Join-Path $directory 'minecraft-protocol\token'
        $deadline = [DateTime]::UtcNow.AddMinutes(3)
        $session = $null
        do {
            if (Test-Path -LiteralPath $tokenFile) {
                try {
                    $token = (Get-Content -LiteralPath $tokenFile -Raw).Trim()
                    $session = Invoke-RestMethod "http://127.0.0.1:$($run.Port)/v0/session" `
                        -Headers @{ Authorization = "Bearer $token" } -TimeoutSec 2
                } catch { $session = $null }
            }
            if ($session.target -eq $run.Target) { break }
            Start-Sleep -Seconds 2
        } while ([DateTime]::UtcNow -lt $deadline)
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
        $smoke = & '.\conformance\phase8\Invoke-Phase8TargetSmoke.ps1' @parameters |
            Where-Object { $_.Result }
        Assert-True ($smoke.Result -eq 'PASS') "$($run.Target)/$($run.Backend) smoke failed"

        $auth = @{ Authorization = "Bearer $token" }
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
finally { Pop-Location }
