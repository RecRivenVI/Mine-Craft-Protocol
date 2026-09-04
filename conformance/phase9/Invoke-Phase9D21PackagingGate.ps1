[CmdletBinding()]
param(
    [switch]$Offline,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 9D-2.1 packaging gate failed: $Message" }
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$targets = @(
    @{ Id = '1.20.1-forge'; BuildFile = 'versions/1.20.1-forge/build.gradle'; Package = 'jar'; NestedPrefix = 'META-INF/jarjar/'; DevRuntime = $true },
    @{ Id = '1.21.1-neoforge'; BuildFile = 'versions/1.21.1-neoforge/build.gradle'; Package = 'jar'; NestedPrefix = 'META-INF/jarjar/'; DevRuntime = $true },
    @{ Id = '26.1.2-neoforge'; BuildFile = 'versions/26.1.2-neoforge/build.gradle'; Package = 'jar'; NestedPrefix = 'META-INF/jarjar/'; DevRuntime = $false },
    @{ Id = '26.2-neoforge'; BuildFile = 'versions/26.2-neoforge/build.gradle'; Package = 'jar'; NestedPrefix = 'META-INF/jarjar/'; DevRuntime = $false },
    @{ Id = '26.2-fabric'; BuildFile = 'versions/26.2-fabric/build.gradle'; Package = 'build'; NestedPrefix = 'META-INF/jars/'; DevRuntime = $false }
)
$sharedClass = 'io/github/recrivenvi/minecraftprotocol/safety/PersistentWriteSafetyFoundation.class'

Push-Location $root
try {
    $foundationPath = Join-Path $root 'runtime-safety/src/main/java/io/github/recrivenvi/minecraftprotocol/safety/PersistentWriteSafetyFoundation.java'
    Assert-True (Test-Path -LiteralPath $foundationPath) 'shared runtime-safety source is missing'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $root 'runtime-safety/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/PersistentWriteSafetyFoundation.java')) ) `
        'shared safety source must not remain in the target runtime package'

    foreach ($target in $targets) {
        $buildPath = Join-Path $root $target.BuildFile
        $build = Get-Content -LiteralPath $buildPath -Raw
        Assert-True ($build.Contains("implementation project(':runtime-safety')")) "$($target.Id) compile dependency missing"
        if ($target.Id -eq '26.2-fabric') {
            Assert-True ($build.Contains("include project(':runtime-safety')")) "$($target.Id) Loom include missing"
        } else {
            Assert-True ($build.Contains("jarJar project(':runtime-safety')")) "$($target.Id) Jar-in-Jar dependency missing"
            if ($target.DevRuntime) {
                Assert-True ($build.Contains("additionalRuntimeClasspath project(':runtime-safety')")) "$($target.Id) development runtime classpath missing"
            } else {
                Assert-True (-not $build.Contains("additionalRuntimeClasspath project(':runtime-safety')")) "$($target.Id) must use ModDev's normal runtime classpath"
            }
        }
    }

    if (-not $SkipBuild) {
        $tasks = @(
            ':versions:1.20.1-forge:jar',
            ':versions:1.21.1-neoforge:jar',
            ':versions:26.1.2-neoforge:jar',
            ':versions:26.2-neoforge:jar',
            ':versions:26.2-fabric:build',
            '--no-daemon'
        )
        if ($Offline) { $tasks += '--offline' }
        & (Join-Path $root 'gradlew.bat') @tasks
        Assert-True ($LASTEXITCODE -eq 0) 'five-target packaging build failed'
    }

    $hashes = [ordered]@{}
    foreach ($target in $targets) {
        $libs = Join-Path $root "versions/$($target.Id)/build/libs"
        $artifact = Get-ChildItem -LiteralPath $libs -File -Filter '*.jar' |
            Where-Object { $_.Name -notmatch '-sources|javadoc' } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        Assert-True ($null -ne $artifact) "$($target.Id) final artifact is missing"

        $outer = [IO.Compression.ZipFile]::OpenRead($artifact.FullName)
        try {
            $outerEntries = @($outer.Entries | ForEach-Object FullName)
            Assert-True (-not ($outerEntries -contains $sharedClass)) "$($target.Id) duplicated shared class at outer artifact root"
            $nested = @($outer.Entries | Where-Object {
                $_.FullName.StartsWith($target.NestedPrefix) -and
                $_.FullName -match '(?i)runtime[-.]safety.*\.jar$'
            })
            Assert-True ($nested.Count -eq 1) "$($target.Id) must contain exactly one embedded runtime-safety jar"
            $nestedStream = $nested[0].Open()
            $memory = [IO.MemoryStream]::new()
            try {
                $nestedStream.CopyTo($memory)
                $memory.Position = 0
                $inner = [IO.Compression.ZipArchive]::new($memory, [IO.Compression.ZipArchiveMode]::Read, $true)
                try {
                    Assert-True ($null -ne $inner.GetEntry($sharedClass)) "$($target.Id) embedded jar does not contain the shared safety class"
                } finally { $inner.Dispose() }
            } finally {
                $memory.Dispose()
                $nestedStream.Dispose()
            }
        } finally { $outer.Dispose() }
        $hashes[$target.Id] = (Get-FileHash -LiteralPath $artifact.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }

    [pscustomobject]@{
        Result = 'PASS'
        Targets = $targets.Count
        DevelopmentPackaging = 'PASS'
        FinalArtifactEmbedding = 'PASS'
        DuplicateSharedClass = 'NONE'
        ArtifactSha256 = [pscustomobject]$hashes
        PersistentWrites = 0
    }
}
finally { Pop-Location }
