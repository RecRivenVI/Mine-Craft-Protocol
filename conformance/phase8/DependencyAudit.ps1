function ConvertTo-SafeAuditDiagnostic {
    param([AllowNull()][string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return '' }
    $value = ($Text -replace '[\r\n\t]+', ' ').Trim()
    if ($value.Length -gt 512) { return $value.Substring(0, 512) }
    return $value
}

function ConvertTo-DependencyAuditClassification {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][int]$ExitCode,
        [AllowEmptyString()][string]$StdOut = '',
        [AllowEmptyString()][string]$StdErr = '',
        [int]$Attempt = 1
    )

    $diagnostic = ConvertTo-SafeAuditDiagnostic $StdErr
    $serviceAvailable = -not [string]::IsNullOrWhiteSpace($StdOut)
    $responseValid = $false
    $parsed = $null
    $failureKind = ''
    $failureMessage = ''
    $status = ''
    $retryable = $false
    $counts = [ordered]@{
        Total = 0
        Low = 0
        Moderate = 0
        High = 0
        Critical = 0
    }

    if ([string]::IsNullOrWhiteSpace($StdOut)) {
        if ($ExitCode -eq 0) {
            $status = 'RETRY_INVALID_RESPONSE'
            $failureKind = 'INVALID_RESPONSE'
            $failureMessage = 'npm audit exited successfully without an audit JSON response'
        }
        else {
            $status = 'RETRY_AUDIT_UNAVAILABLE'
            $failureKind = 'AUDIT_UNAVAILABLE'
            $failureMessage = "npm audit produced no audit JSON (exit $ExitCode)"
        }
        $retryable = $true
    }
    else {
        try { $parsed = $StdOut | ConvertFrom-Json }
        catch {
            $status = 'RETRY_INVALID_RESPONSE'
            $failureKind = 'INVALID_RESPONSE'
            $failureMessage = 'npm audit response was not valid JSON'
            $retryable = $true
        }

        if ($null -ne $parsed -and [string]::IsNullOrEmpty($status)) {
            $counterObject = $parsed.metadata.vulnerabilities
            $invalidCounter = $false
            foreach ($mapping in @(
                @{ Json = 'total'; Result = 'Total' },
                @{ Json = 'low'; Result = 'Low' },
                @{ Json = 'moderate'; Result = 'Moderate' },
                @{ Json = 'high'; Result = 'High' },
                @{ Json = 'critical'; Result = 'Critical' }
            )) {
                $property = if ($null -eq $counterObject) { $null } else {
                    $counterObject.PSObject.Properties[$mapping.Json]
                }
                $number = 0L
                if ($null -eq $property -or
                    -not [long]::TryParse([string]$property.Value, [ref]$number) -or
                    $number -lt 0 -or $number -gt [int]::MaxValue) {
                    $invalidCounter = $true
                    break
                }
                $counts[$mapping.Result] = [int]$number
            }

            if ($invalidCounter) {
                $status = 'RETRY_INVALID_RESPONSE'
                $failureKind = 'INVALID_RESPONSE'
                $failureMessage = 'npm audit JSON did not contain usable vulnerability counters'
                $retryable = $true
            }
            else {
                $responseValid = $true
                $serviceAvailable = $true
                $thresholdCount = $counts.High + $counts.Critical
                if ($thresholdCount -gt 0) {
                    $status = 'FAIL_VULNERABILITIES_FOUND'
                    $failureKind = 'VULNERABILITIES_FOUND'
                    $failureMessage = "dependency audit found $($counts.High) high and $($counts.Critical) critical vulnerabilities"
                }
                elseif ($ExitCode -ne 0) {
                    $status = 'RETRY_INVALID_RESPONSE'
                    $failureKind = 'INVALID_RESPONSE'
                    $failureMessage = "npm audit returned valid zero-threshold counters but exited $ExitCode"
                    $retryable = $true
                }
                else {
                    $status = 'PASS_NO_THRESHOLD_VULNERABILITIES'
                }
            }
        }
    }

    [pscustomobject]@{
        Status = $status
        Attempt = $Attempt
        ExitCode = $ExitCode
        ServiceAvailable = $serviceAvailable
        ResponseValid = $responseValid
        VulnerabilitiesTotal = $counts.Total
        VulnerabilitiesLow = $counts.Low
        VulnerabilitiesModerate = $counts.Moderate
        VulnerabilitiesHigh = $counts.High
        VulnerabilitiesCritical = $counts.Critical
        FailureKind = $failureKind
        FailureMessage = $failureMessage
        Diagnostic = $diagnostic
        Retryable = $retryable
    }
}

function Invoke-DependencyAuditProcess {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [int]$TimeoutMilliseconds = 120000
    )

    $npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if ($null -eq $npmCommand) { $npmCommand = Get-Command npm -ErrorAction Stop }
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $npmCommand.Source
    $start.WorkingDirectory = $WorkingDirectory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    [void]$start.ArgumentList.Add('audit')
    [void]$start.ArgumentList.Add('--audit-level=high')
    [void]$start.ArgumentList.Add('--json')

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Unable to start npm audit' }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $timedOut = -not $process.WaitForExit($TimeoutMilliseconds)
    if ($timedOut) {
        try { $process.Kill($true) } catch { }
        $process.WaitForExit()
    }
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $exitCode = if ($timedOut) { -1 } else { $process.ExitCode }
    $process.Dispose()

    [pscustomobject]@{
        ExitCode = $exitCode
        StdOut = $stdout
        StdErr = $(if ($timedOut) { "npm audit timed out after $TimeoutMilliseconds ms. $stderr" } else { $stderr })
    }
}

function Invoke-DependencyAudit {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [ValidateRange(1, 3)][int]$MaxAttempts = 3,
        [ValidateRange(0, 10000)][int]$RetryDelayMilliseconds = 1000,
        [ValidateRange(1000, 600000)][int]$TimeoutMilliseconds = 120000,
        [scriptblock]$AttemptProvider
    )

    $useDefaultProvider = $null -eq $AttemptProvider

    $attempts = [Collections.Generic.List[object]]::new()
    $classification = $null
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            $processResult = if ($useDefaultProvider) {
                Invoke-DependencyAuditProcess `
                    -WorkingDirectory $WorkingDirectory `
                    -TimeoutMilliseconds $TimeoutMilliseconds
            }
            else {
                & $AttemptProvider $attempt
            }
        }
        catch {
            $processResult = [pscustomobject]@{
                ExitCode = -1
                StdOut = ''
                StdErr = $_.Exception.Message
            }
        }
        $classification = ConvertTo-DependencyAuditClassification `
            -ExitCode ([int]$processResult.ExitCode) `
            -StdOut ([string]$processResult.StdOut) `
            -StdErr ([string]$processResult.StdErr) `
            -Attempt $attempt
        $attempts.Add($classification)

        if ($classification.Status -eq 'PASS_NO_THRESHOLD_VULNERABILITIES' -or
            $classification.Status -eq 'FAIL_VULNERABILITIES_FOUND') {
            break
        }
        if ($attempt -lt $MaxAttempts -and $RetryDelayMilliseconds -gt 0) {
            Start-Sleep -Milliseconds $RetryDelayMilliseconds
        }
    }

    $finalStatus = $classification.Status
    if ($finalStatus -eq 'RETRY_AUDIT_UNAVAILABLE') { $finalStatus = 'FAIL_AUDIT_UNAVAILABLE' }
    elseif ($finalStatus -eq 'RETRY_INVALID_RESPONSE') { $finalStatus = 'FAIL_INVALID_RESPONSE' }

    [pscustomobject]@{
        Status = $finalStatus
        Attempts = $attempts.Count
        ServiceAvailable = $classification.ServiceAvailable
        ResponseValid = $classification.ResponseValid
        VulnerabilitiesTotal = $classification.VulnerabilitiesTotal
        VulnerabilitiesLow = $classification.VulnerabilitiesLow
        VulnerabilitiesModerate = $classification.VulnerabilitiesModerate
        VulnerabilitiesHigh = $classification.VulnerabilitiesHigh
        VulnerabilitiesCritical = $classification.VulnerabilitiesCritical
        FailureKind = $classification.FailureKind
        FailureMessage = $classification.FailureMessage
        AttemptResults = $attempts.ToArray()
    }
}
