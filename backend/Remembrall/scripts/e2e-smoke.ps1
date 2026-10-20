[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$VideoUrl,
    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$DatabaseContainer,
    [string]$DatabaseUser = 'test',
    [string]$DatabaseName = 'test'
)

if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw 'Run this script with PowerShell 7 or later (pwsh).'
}

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$steps = [System.Collections.Generic.List[object]]::new()
$headers = @{}
$sessionToken = $null
$installationId = [guid]::NewGuid().ToString()
$hasher = [System.Security.Cryptography.SHA256]::Create()
try {
    $installationHash = [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($installationId))).Replace('-', '').ToLowerInvariant()
} finally {
    $hasher.Dispose()
}
$memberFilter = "(SELECT id FROM member WHERE auth_provider = 'GUEST' AND provider_user_id = '$installationHash')"
$currentStep = 'preparation'
$lastResponse = $null
$failed = $false
$databaseReady = $false
$totalTimer = [System.Diagnostics.Stopwatch]::StartNew()
$analysisTimer = $null
$pollTimer = $null

function Show-Json($Value) {
    $json = ConvertTo-Json -InputObject $Value -Depth 30 -Compress
    if (-not [string]::IsNullOrEmpty($script:sessionToken)) {
        $json = $json.Replace($script:sessionToken, '[REDACTED]')
    }
    Write-Host $json
}

function Assert-Result([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Invoke-Api([string]$Name, [string]$Method, [string]$Path, [int[]]$Expected,
                    $Body = $null, [int]$TimeoutSeconds = 120) {
    $script:currentStep = $Name
    $script:lastResponse = $null
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    $step = [pscustomobject]@{ Step = $Name; Status = $null; Seconds = 0; Result = 'FAIL' }
    $script:steps.Add($step)
    try {
        $request = @{
            Uri = "$script:BaseUrl$Path"
            Method = $Method
            Headers = $script:headers
            TimeoutSec = $TimeoutSeconds
            SkipHttpErrorCheck = $true
            MaximumRedirection = 0
        }
        if ($null -ne $Body) {
            $request.Body = [System.Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Body -Depth 10 -Compress))
            $request.ContentType = 'application/json; charset=utf-8'
        }
        $response = Invoke-WebRequest @request
        $step.Status = [int]$response.StatusCode
        $timer.Stop()
        $step.Seconds = [math]::Round($timer.Elapsed.TotalSeconds, 3)
        Write-Host "[$Name] $Method $Path -> HTTP $($step.Status), $($step.Seconds)s"
        $content = [string]$response.Content
        if ($Expected -notcontains $step.Status) {
            Show-Json @{ response = $content }
            throw "Expected HTTP $($Expected -join '/'), received $($step.Status)."
        }
        $data = $null
        if (-not [string]::IsNullOrWhiteSpace($content)) {
            try {
                $data = ConvertFrom-Json -InputObject $content -Depth 30
            } catch {
                Show-Json @{ response = $content }
                throw 'Response is not valid JSON.'
            }
        }
        $script:lastResponse = $data
        $step.Result = 'PASS'
        return $data
    } finally {
        if ($timer.IsRunning) {
            $timer.Stop()
            $step.Seconds = [math]::Round($timer.Elapsed.TotalSeconds, 3)
            Write-Host "[$Name] HTTP $($step.Status), $($step.Seconds)s, FAIL"
        }
    }
}

function Invoke-Database([string]$Sql) {
    $output = @(& docker exec $script:DatabaseContainer psql "--username=$script:DatabaseUser" "--dbname=$script:DatabaseName" --no-psqlrc --quiet --tuples-only --no-align --set=ON_ERROR_STOP=1 "--command=$Sql" 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Read-only DB query failed: $($output -join [Environment]::NewLine)"
    }
    return ($output -join [Environment]::NewLine).Trim()
}

function Get-DatabaseReport {
    $sql = @"
SELECT json_build_object(
    'memberId', $script:memberFilter,
    'contents', COALESCE((
        SELECT json_agg(row_to_json(c)) FROM (
            SELECT c.id AS content_id, c.analysis_status,
                   c.embedding IS NOT NULL AS embedding_filled, c.embedding_model,
                   c.last_analysis_error_code, c.analysis_started_at, c.analyzed_at,
                   EXTRACT(EPOCH FROM (c.analyzed_at - c.analysis_started_at)) AS analysis_seconds
            FROM content c JOIN personal_save ps ON ps.content_id = c.id
            WHERE ps.member_id = $script:memberFilter
        ) c
    ), '[]'::json),
    'triggerCount', (SELECT count(*) FROM trigger WHERE member_id = $script:memberFilter),
    'recallExecution', (
        SELECT row_to_json(r) FROM (
            SELECT r.id, r.trigger_event_id, r.status, r.result, r.proposal_type,
                   r.decision_summary, r.agent_version, r.failure_code,
                   r.started_at, r.finished_at,
                   EXTRACT(EPOCH FROM (r.finished_at - r.started_at)) AS execution_seconds
            FROM recall_execution r JOIN trigger t ON t.id = r.trigger_id
            WHERE t.member_id = $script:memberFilter
            ORDER BY r.created_at DESC, r.id DESC LIMIT 1
        ) r
    ),
    'notification', (
        SELECT row_to_json(n) FROM (
            SELECT id, execution_id, status, failure_code
            FROM notification WHERE member_id = $script:memberFilter
            ORDER BY created_at DESC, id DESC LIMIT 1
        ) n
    )
);
"@
    return ConvertFrom-Json -InputObject (Invoke-Database $sql) -Depth 30
}

try {
    Assert-Result ([uri]::IsWellFormedUriString($VideoUrl, [UriKind]::Absolute)) 'VideoUrl must be an absolute URL.'
    Assert-Result ($BaseUrl -match '^https?://') 'BaseUrl must use HTTP or HTTPS.'
    if ([string]::IsNullOrWhiteSpace($DatabaseContainer)) {
        $containers = @(& docker ps --filter 'ancestor=pgvector/pgvector:pg16' --format '{{.ID}}')
        Assert-Result ($LASTEXITCODE -eq 0) 'Cannot list running DB containers.'
        Assert-Result ($containers.Count -eq 1) 'Specify -DatabaseContainer; expected exactly one running pgvector:pg16 container.'
        $DatabaseContainer = $containers[0]
    }
    Assert-Result ((Invoke-Database 'SELECT 1') -eq '1') 'DB connection check failed.'
    $databaseReady = $true
    Write-Host "installationId=$installationId; baseUrl=$BaseUrl"

    $login = Invoke-Api 'guest-login' POST '/api/auth/guest' @(200) @{ installationId = $installationId }
    $sessionToken = $login.sessionToken
    Assert-Result (-not [string]::IsNullOrWhiteSpace($sessionToken)) 'Guest login did not return a session token.'
    $headers.Authorization = "Bearer $sessionToken"
    Show-Json @{ sessionTokenPresent = $true; sessionExpiresAt = $login.sessionExpiresAt }

    $consents = Invoke-Api 'read-consents' GET '/api/me/consents' @(200)
    Show-Json $consents
    $locationConsent = @($consents | Where-Object { $_.consentType -eq 'LOCATION_BASED_SERVICE' })
    Assert-Result ($locationConsent.Count -eq 1) 'Location service consent was not returned.'
    $version = $locationConsent[0].currentTermsVersion
    Assert-Result (-not [string]::IsNullOrWhiteSpace($version)) 'currentTermsVersion is missing.'
    $consent = Invoke-Api 'agree-location-consent' PUT '/api/me/consents/LOCATION_BASED_SERVICE' @(200) @{ agreed = $true; termsVersion = $version }
    Show-Json $consent
    Assert-Result ($consent.agreed -and $consent.termsVersion -eq $version) 'Location service consent was not applied.'

    $analysisTimer = [System.Diagnostics.Stopwatch]::StartNew()
    $saved = Invoke-Api 'save-video' POST '/api/personal-saves' @(201) @{ url = $VideoUrl }
    Show-Json $saved
    $personalSaveId = [guid]::Parse($saved.personalSaveId).ToString()
    Assert-Result (@('PENDING', 'ANALYZING', 'SUCCESS', 'PARTIAL', 'FAILED') -contains $saved.analysisStatus) 'Unknown analysisStatus.'
    Write-Host "saveResponseSeconds=$($steps[$steps.Count - 1].Seconds); initialAnalysisStatus=$($saved.analysisStatus)"

    $pollTimer = [System.Diagnostics.Stopwatch]::StartNew()
    $attempt = 0
    while ($true) {
        $remaining = 300 - $pollTimer.Elapsed.TotalSeconds
        Assert-Result ($remaining -gt 0) 'Analysis did not finish within the 5-minute polling limit.'
        $attempt++
        $timeout = [math]::Max(1, [math]::Min(120, [math]::Floor($remaining)))
        $detail = Invoke-Api "analysis-poll-$attempt" GET "/api/personal-saves/$personalSaveId" @(200) -TimeoutSeconds $timeout
        Show-Json @{ personalSaveId = $detail.personalSaveId; analysisStatus = $detail.analysisStatus; analysisFailureCode = $detail.analysisFailureCode }
        Assert-Result (@('PENDING', 'ANALYZING', 'SUCCESS', 'PARTIAL', 'FAILED') -contains $detail.analysisStatus) 'Unknown analysisStatus.'
        if (@('SUCCESS', 'PARTIAL', 'FAILED') -contains $detail.analysisStatus) {
            break
        }
        $remaining = 300 - $pollTimer.Elapsed.TotalSeconds
        Assert-Result ($remaining -gt 0) 'Analysis did not finish within the 5-minute polling limit.'
        Start-Sleep -Milliseconds ([int][math]::Min(10000, [math]::Ceiling($remaining * 1000)))
    }
    $pollTimer.Stop()
    $analysisTimer.Stop()
    Write-Host "analysisObservedSeconds=$([math]::Round($analysisTimer.Elapsed.TotalSeconds, 3)); pollingSeconds=$([math]::Round($pollTimer.Elapsed.TotalSeconds, 3))"
    Show-Json @{ places = $detail.places }
    Assert-Result ($detail.analysisStatus -ne 'FAILED') "Analysis failed: $($detail.analysisFailureCode)"
    Assert-Result ($null -ne $detail.places -and @($detail.places).Count -gt 0) 'Analysis returned no places; stopping before geofences.'
    $place = $detail.places[0]
    $placeId = [guid]::Parse($place.placeId).ToString()
    $latitude = ([double]$place.latitude).ToString('R', [Globalization.CultureInfo]::InvariantCulture)
    $longitude = ([double]$place.longitude).ToString('R', [Globalization.CultureInfo]::InvariantCulture)

    $geofences = Invoke-Api 'geofences' GET "/api/geofences?latitude=$latitude&longitude=$longitude&platform=ANDROID" @(200)
    Show-Json $geofences
    Assert-Result (@($geofences.places | Where-Object { $_.placeId -eq $placeId }).Count -gt 0) 'The first analyzed place is missing from geofence places.'

    $triggerEventId = [guid]::NewGuid().ToString()
    $event = @{
        placeId = $placeId
        triggerEventId = $triggerEventId
        eventType = 'ENTER'
        eventOccurredAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
    Show-Json $event
    $null = Invoke-Api 'location-event' POST '/api/location-events' @(202) $event
    Write-Host "locationEventRequestSeconds=$($steps[$steps.Count - 1].Seconds)"

    $currentStep = 'recall-result'
    $lastResponse = $null
    $recallTimer = [System.Diagnostics.Stopwatch]::StartNew()
    do {
        $report = Get-DatabaseReport
        $execution = $report.recallExecution
        if ($null -ne $execution -and $execution.trigger_event_id -eq $triggerEventId -and @('COMPLETED', 'FAILED', 'TIMED_OUT') -contains $execution.status) {
            break
        }
        Assert-Result ($recallTimer.Elapsed.TotalSeconds -lt 60) 'No terminal recall execution for this event within 60 seconds.'
        Start-Sleep -Seconds 2
    } while ($true)
    $recallTimer.Stop()
    Show-Json $execution
    Assert-Result ($execution.agent_version -like 'llm*') 'agent_version does not start with llm.'
    Assert-Result ($execution.status -eq 'COMPLETED') "Recall failed: $($execution.failure_code)"
    if ($execution.result -eq 'NO_ACTION') {
        Write-Host 'NO_ACTION decision_summary:'
        Write-Host $execution.decision_summary
        Assert-Result ($null -eq $report.notification) 'NO_ACTION unexpectedly created a notification.'
        Write-Host 'notification-detail: SKIP (NO_ACTION)'
    } else {
        Assert-Result ($execution.result -eq 'PROPOSE') 'Unknown recall execution result.'
        Assert-Result ($null -ne $report.notification) 'PROPOSE did not create a notification.'
        Assert-Result ($report.notification.execution_id -eq $execution.id) 'Notification belongs to a different execution.'
        $notificationId = [guid]::Parse($report.notification.id).ToString()
        $notification = Invoke-Api 'notification-detail' GET "/api/notifications/$notificationId" @(200)
        Show-Json $notification
        Assert-Result ($notification.notificationId -eq $notificationId) 'Notification ID mismatch.'
        Assert-Result ($report.notification.status -eq 'FAILED' -and $report.notification.failure_code -eq 'NO_FCM_TOKEN') 'Expected notification FAILED / NO_FCM_TOKEN without a device token.'
    }
} catch {
    $failed = $true
    if ($steps.Count -gt 0 -and $steps[$steps.Count - 1].Step -eq $currentStep) {
        $steps[$steps.Count - 1].Result = 'FAIL'
    }
    Write-Host "FAILED at [$currentStep]: $($_.Exception.Message)"
    if ($null -ne $lastResponse) {
        Show-Json @{ response = $lastResponse }
    }
} finally {
    $totalTimer.Stop()
    Write-Host 'Step results:'
    foreach ($step in $steps) {
        Show-Json $step
    }
    if ($null -ne $analysisTimer) {
        $analysisTimer.Stop()
        Write-Host "analysisObservedSeconds=$([math]::Round($analysisTimer.Elapsed.TotalSeconds, 3))"
    }
    if ($databaseReady) {
        try {
            Write-Host 'Database report:'
            Show-Json (Get-DatabaseReport)
        } catch {
            $failed = $true
            Write-Host "FAILED at [database-report]: $($_.Exception.Message)"
        }
    }
    Write-Host "totalSeconds=$([math]::Round($totalTimer.Elapsed.TotalSeconds, 3)); failed=$failed"
}

if ($failed) {
    exit 1
}
