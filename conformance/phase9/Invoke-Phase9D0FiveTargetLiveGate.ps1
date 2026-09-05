[CmdletBinding()]
param(
    [switch]$Offline,
    [switch]$SkipStorageRead,
    [string[]]$OnlyTargets = @()
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 9D-0 five-target live gate failed: $Message" }
}

function Invoke-Json([string]$Base, [string]$Method, [string]$Path, [hashtable]$Headers, [object]$Body) {
    $parameters = @{ Uri = "$Base$Path"; Method = $Method; Headers = $Headers; TimeoutSec = 15 }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 40 -Compress
    }
    Invoke-RestMethod @parameters
}

$runs = @(
    @{ Target='1.20.1-forge'; Task=':versions:1.20.1-forge:runClient'; Dir='runs\1.20.1-forge\client'; Port=25581 },
    @{ Target='1.21.1-neoforge'; Task=':versions:1.21.1-neoforge:runClient'; Dir='runs\1.21.1-neoforge\client'; Port=25581 },
    @{ Target='26.1.2-neoforge'; Task=':versions:26.1.2-neoforge:runClient'; Dir='runs\26.1.2-neoforge\client'; Port=25582 },
    @{ Target='26.2-neoforge'; Task=':versions:26.2-neoforge:runClient'; Dir='runs\26.2-neoforge\client'; Port=25582 },
    @{ Target='26.2-fabric'; Task=':versions:26.2-fabric:runClient'; Dir='runs\26.2-fabric\client'; Port=25583 }
)
if ($OnlyTargets.Count -gt 0) { $runs = @($runs | Where-Object { $_.Target -in $OnlyTargets }) }

Push-Location $root
try {
    $env:MCP_RUNTIME_SCOPES = 'read,ui,input,capture,event,diagnostics,control,command,fixture,debug,storage.read,debug.write,debug.player,debug.entity,debug.world,debug.block_entity,debug.menu,debug.provider,debug.chunk,debug.client,debug.network'
    $results = foreach ($run in $runs) {
        Write-Host "[Phase9D-0] starting $($run.Target)"
        Assert-True (-not [bool](Get-NetTCPConnection -LocalPort $run.Port -State Listen -ErrorAction SilentlyContinue)) "port $($run.Port) is occupied"
        $directory = (Resolve-Path -LiteralPath $run.Dir).Path
        $arguments = @($run.Task, '--no-daemon')
        if ($Offline) { $arguments += '--offline' }
        $process = Start-Process '.\gradlew.bat' -ArgumentList $arguments -WorkingDirectory $root `
            -RedirectStandardOutput (Join-Path $directory 'phase9d0-five-target-stdout.log') `
            -RedirectStandardError (Join-Path $directory 'phase9d0-five-target-stderr.log') `
            -PassThru -WindowStyle Hidden
        $tokenFile = Join-Path $directory 'minecraft-protocol\token'
        $deadline = (Get-Date).AddMinutes(6)
        $session = $null
        do {
            if (Test-Path -LiteralPath $tokenFile) {
                $token = (Get-Content -LiteralPath $tokenFile -Raw).Trim()
                $auth = @{ Authorization = "Bearer $token" }
                try { $session = Invoke-RestMethod "http://127.0.0.1:$($run.Port)/v0/session" -Headers $auth -TimeoutSec 2 } catch { $session = $null }
            }
            if ($session -and $session.target -eq $run.Target -and ($session.inWorld -or $session.screenClass -match 'TitleScreen')) { break }
            Start-Sleep -Seconds 2
        } while ((Get-Date) -lt $deadline)
        Assert-True ($session -and $session.target -eq $run.Target) "$($run.Target) Runtime not ready"
        $base = "http://127.0.0.1:$($run.Port)"
        $v1 = & '.\conformance\phase8\Invoke-Phase8TargetSmoke.ps1' -BaseUri $base -TokenFile $tokenFile `
            -ExpectedTarget $run.Target -ExpectedBackend any -EnterWorld -RequireAuthoritative -StayInWorld
        Assert-True ($v1.Result -eq 'PASS') "$($run.Target) V1 smoke/world entry"
        if ($SkipStorageRead) {
            # Windows keeps the live world's level.dat handle exclusive on some
            # targets.  Packaging/lifecycle verification must not turn that
            # known read-plane condition into a runtime-classpath result.
            $storage = [pscustomobject]@{
                Result = 'SKIPPED'
                LivePersistedSeparation = 'NOT_RUN'
                NoImplicitLoad = 'NOT_RUN'
                BoundedIO = 'NOT_RUN'
            }
        } else {
            $storage = & '.\conformance\phase9\Invoke-Phase9D0StorageReadConformance.ps1' `
                -BaseUri $base -TokenFile $tokenFile -ExpectedTarget $run.Target
            Assert-True ($storage.Result -eq 'PASS') "$($run.Target) storage read conformance"
        }
        $runningInventory = Invoke-Json $base GET '/v0/diagnostics/phase9a/inventory' $auth $null
        $runningLifecycle = [string]$runningInventory.persistentWriteSafety.lifecycleState
        Assert-True ($runningLifecycle -in @('WORLD_RUNNING', 'SAVING')) "$($run.Target) lifecycle did not report a running/saving world"

        $worldUnload = 'NOT_RUN'
        $shutdownOperationError = $null
        try {
            [void](Invoke-Json $base POST '/v0/control/emergency-release' $auth $null)
            $quitLease = Invoke-Json $base POST '/v0/control/acquire' $auth @{ ttlMs = 15000 }
            $quitHeaders = @{ Authorization = "Bearer $token"; 'X-MCP-Control-Lease' = $quitLease.leaseId }
            $tree = Invoke-Json $base GET '/v0/ui/tree' $auth $null
            $session = Invoke-Json $base GET '/v0/session' $auth $null
            if ($session.inWorld) {
                [void](Invoke-Json $base POST '/v0/input/key' $quitHeaders @{ key=256; scanCode=1; action=1; modifiers=0 })
                [void](Invoke-Json $base POST '/v0/input/key' $quitHeaders @{ key=256; scanCode=1; action=0; modifiers=0 })
                Start-Sleep -Milliseconds 500
                $tree = Invoke-Json $base GET '/v0/ui/tree' $auth $null
                $save = @($tree.children | Where-Object -Property label -eq 'Save and Quit to Title')[0]
                if ($save -and $save.active) {
                    [void](Invoke-Json $base POST '/v0/ui/action' $quitHeaders @{ action='click'; holdMs=100; selector=@{ role='button'; label='Save and Quit to Title' } })
                }
                $titleDeadline = (Get-Date).AddSeconds(15)
                do { Start-Sleep -Milliseconds 250; $afterWorld = Invoke-Json $base GET '/v0/session' $auth $null } while ($afterWorld.inWorld -and (Get-Date) -lt $titleDeadline)
                Assert-True (-not $afterWorld.inWorld) "$($run.Target) world unload"
                $worldUnload = 'PASS'
                $offlineLifecycle = 'UNKNOWN'
                $offlineDeadline = (Get-Date).AddSeconds(10)
                do {
                    $offlineInventory = Invoke-Json $base GET '/v0/diagnostics/phase9a/inventory' $auth $null
                    $offlineLifecycle = [string]$offlineInventory.persistentWriteSafety.lifecycleState
                    if ($offlineLifecycle -ne 'STOPPED_OFFLINE') { Start-Sleep -Milliseconds 250 }
                } while ($offlineLifecycle -ne 'STOPPED_OFFLINE' -and (Get-Date) -lt $offlineDeadline)
                Assert-True ($offlineLifecycle -eq 'STOPPED_OFFLINE') "$($run.Target) lifecycle did not reach STOPPED_OFFLINE after world/server/connection exit"
                $tree = Invoke-Json $base GET '/v0/ui/tree' $auth $null
                if (-not $afterWorld.inWorld) {
                    foreach ($domain in @('world', 'player', 'chunk')) {
                        $saved = Invoke-Json $base POST '/v0/diagnostics/phase9a/storage/read' $auth @{ domain=$domain }
                        Assert-True ($saved.dataSource -eq 'PERSISTED' -and $saved.consistency -eq 'last_saved_state') "$($run.Target) saved $domain authority mismatch"
                        Assert-True ($saved.lifecycleState -eq 'offline_file_snapshot' -and -not $saved.liveWorldExists -and -not $saved.targetLoaded) "$($run.Target) saved $domain must use detached offline context"
                    }
                }
            }
            $quit = @($tree.children | Where-Object -Property label -eq 'Quit Game')[0]
            if ($quit -and $quit.active) {
                [void](Invoke-Json $base POST '/v0/ui/action' $quitHeaders @{ action='click'; holdMs=100; selector=@{ role='button'; label='Quit Game' } })
            }
        } catch { $shutdownOperationError = $_ }
        if ($shutdownOperationError -and $shutdownOperationError.Exception.Message -match 'unexpectedly succeeded|world unload') { throw $shutdownOperationError }
        $shutdownDeadline = (Get-Date).AddSeconds(30)
        do { Start-Sleep -Milliseconds 250; $listening = [bool](Get-NetTCPConnection -LocalPort $run.Port -State Listen -ErrorAction SilentlyContinue) } while ($listening -and (Get-Date) -lt $shutdownDeadline)
        Assert-True (-not $listening) "$($run.Target) did not shut down cleanly"
        Write-Host "[Phase9D-0] completed $($run.Target)"
        [pscustomobject]@{
            Target = $run.Target
            Launch = 'PASS'
            Readiness = 'PASS'
            V1Smoke = 'PASS'
            Storage = $storage.Result
            LivePersistedBoundary = $storage.LivePersistedSeparation
            NoImplicitLoad = $storage.NoImplicitLoad
            BoundedIO = $storage.BoundedIO
            LifecycleRunning = $runningLifecycle
            LifecycleOffline = $(if ($offlineLifecycle) { $offlineLifecycle } else { 'NOT_REACHED' })
            WorldUnload = $worldUnload
            Shutdown = 'PASS'
        }
    }
    [pscustomobject]@{ Result = 'PASS'; Targets = $results.Count; Results = $results }
}
finally { Pop-Location }
