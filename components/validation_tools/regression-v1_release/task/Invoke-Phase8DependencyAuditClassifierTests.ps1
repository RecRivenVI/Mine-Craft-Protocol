[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$helper = Join-Path $PSScriptRoot 'DependencyAudit.ps1'
. $helper

function Assert-Equal($Actual, $Expected, [string]$Message) {
    if ($Actual -ne $Expected) {
        throw "Dependency audit classifier test failed: $Message (expected=$Expected actual=$Actual)"
    }
}

function New-AuditJson([int]$Low = 0, [int]$Moderate = 0, [int]$High = 0, [int]$Critical = 0) {
    @{
        auditReportVersion = 2
        vulnerabilities = @{}
        metadata = @{
            vulnerabilities = @{
                info = 0
                low = $Low
                moderate = $Moderate
                high = $High
                critical = $Critical
                total = $Low + $Moderate + $High + $Critical
            }
        }
    } | ConvertTo-Json -Depth 6 -Compress
}

$results = [Collections.Generic.List[object]]::new()

function Add-Result([string]$Case, [string]$Expected, [string]$Actual) {
    Assert-Equal $Actual $Expected $Case
    $results.Add([pscustomobject]@{
        Case = $Case
        Expected = $Expected
        Actual = $Actual
        Result = 'PASS'
    })
}

$case1 = ConvertTo-DependencyAuditClassification -ExitCode 0 -StdOut (New-AuditJson)
Add-Result 'valid zero high/critical' 'PASS_NO_THRESHOLD_VULNERABILITIES' $case1.Status
Assert-Equal $case1.ServiceAvailable $true 'successful service availability'
Assert-Equal $case1.ResponseValid $true 'successful response validity'

$case2 = ConvertTo-DependencyAuditClassification -ExitCode 1 -StdOut (New-AuditJson -High 1)
Add-Result 'valid high vulnerability' 'FAIL_VULNERABILITIES_FOUND' $case2.Status
Assert-Equal $case2.VulnerabilitiesHigh 1 'high count'
Assert-Equal $case2.FailureKind 'VULNERABILITIES_FOUND' 'high failure kind'
Assert-Equal $case2.Retryable $false 'high vulnerability must not retry'

$case3 = ConvertTo-DependencyAuditClassification -ExitCode 1 -StdOut (New-AuditJson -Critical 1)
Add-Result 'valid critical vulnerability' 'FAIL_VULNERABILITIES_FOUND' $case3.Status
Assert-Equal $case3.VulnerabilitiesCritical 1 'critical count'

$case4 = ConvertTo-DependencyAuditClassification -ExitCode 1 -StdOut '' -StdErr 'simulated network failure'
Add-Result 'network failure without JSON' 'RETRY_AUDIT_UNAVAILABLE' $case4.Status
Assert-Equal $case4.ServiceAvailable $false 'network failure service availability'
Assert-Equal $case4.ResponseValid $false 'network failure response validity'

$case5 = ConvertTo-DependencyAuditClassification -ExitCode 1 -StdOut '{"error":' -StdErr 'simulated protocol failure'
Add-Result 'malformed audit response' 'RETRY_INVALID_RESPONSE' $case5.Status
Assert-Equal $case5.ServiceAvailable $true 'malformed service response presence'
Assert-Equal $case5.ResponseValid $false 'malformed response validity'
Assert-Equal $case5.FailureKind 'INVALID_RESPONSE' 'malformed failure kind'

$recoverySequence = @(
    [pscustomobject]@{ ExitCode = 1; StdOut = ''; StdErr = 'simulated service failure 1' },
    [pscustomobject]@{ ExitCode = 1; StdOut = ''; StdErr = 'simulated service failure 2' },
    [pscustomobject]@{ ExitCode = 0; StdOut = (New-AuditJson); StdErr = '' }
)
$recoveryProvider = { param($Attempt) $recoverySequence[$Attempt - 1] }.GetNewClosure()
$case6 = Invoke-DependencyAudit `
    -WorkingDirectory $PSScriptRoot `
    -MaxAttempts 3 `
    -RetryDelayMilliseconds 0 `
    -AttemptProvider $recoveryProvider
Add-Result 'transient failures then success' 'PASS_NO_THRESHOLD_VULNERABILITIES' $case6.Status
Assert-Equal $case6.Attempts 3 'recovery attempt count'
Assert-Equal $case6.ResponseValid $true 'recovery response validity'

$failureSequence = @(
    [pscustomobject]@{ ExitCode = 1; StdOut = ''; StdErr = 'simulated service failure 1' },
    [pscustomobject]@{ ExitCode = 1; StdOut = ''; StdErr = 'simulated service failure 2' },
    [pscustomobject]@{ ExitCode = 1; StdOut = ''; StdErr = 'simulated service failure 3' }
)
$failureProvider = { param($Attempt) $failureSequence[$Attempt - 1] }.GetNewClosure()
$case7 = Invoke-DependencyAudit `
    -WorkingDirectory $PSScriptRoot `
    -MaxAttempts 3 `
    -RetryDelayMilliseconds 0 `
    -AttemptProvider $failureProvider
Add-Result 'persistent service failure' 'FAIL_AUDIT_UNAVAILABLE' $case7.Status
Assert-Equal $case7.Attempts 3 'persistent failure attempt count'
Assert-Equal $case7.FailureKind 'AUDIT_UNAVAILABLE' 'persistent failure kind'

[pscustomobject]@{
    Result = 'PASS'
    Cases = $results.Count
    Tests = $results.ToArray()
}
