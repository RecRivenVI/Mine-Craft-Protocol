# Explicit test intent transitions. This helper never interprets chat consent.
function Get-AgentMode([string]$BaseUri,[hashtable]$Headers) {
    Invoke-RestMethod ($BaseUri.TrimEnd('/')+'/v0/control/mode') -Headers $Headers -TimeoutSec 10
}
function Set-AgentMode([string]$BaseUri,[hashtable]$Headers,[ValidateSet('READ','OPERATE')][string]$Mode,[string]$LeaseId) {
    $current=Get-AgentMode $BaseUri $Headers
    $requestHeaders=$Headers.Clone()
    if($LeaseId){$requestHeaders['X-MCP-Control-Lease']=$LeaseId}
    Invoke-RestMethod ($BaseUri.TrimEnd('/')+'/v0/control/mode') -Method Post -Headers $requestHeaders -ContentType application/json -TimeoutSec 15 -Body (
        @{mode=$Mode;expectedModeVersion=$current.modeVersion}|ConvertTo-Json -Depth 5 -Compress)
}
