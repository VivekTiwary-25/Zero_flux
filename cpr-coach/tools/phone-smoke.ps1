<#
.SYNOPSIS
    Checks CPR Coach startup with permissions granted, then revoked.
.DESCRIPTION
    Run only with the phone owner's permission and while the phone is available.
    Requires adb on PATH and an already installed com.zeroflux.cprcoach.
    Clears device logcat, changes camera/microphone/notification permissions,
    launches only CPR Coach's launcher activity, and captures the entire screen
    and device logcat. Review captures for private information before sharing.
    Camera, microphone and notification permissions are granted again when the
    run ends, even after a failure, so the app is not left unable to use them.
    The exact original permission state and the cleared logs are not restored.
    No dialer or UI input is used.

    Evidence is saved under a timestamped folder in the PC's temporary directory.
    Any adb failure, missing process/capture, or crash evidence fails the run.
    FATAL EXCEPTION, SIGSEGV and SIGABRT anywhere in logcat are conservatively
    treated as failures, even if another app produced them. Package-named native
    aborts are also failures. Inspect the saved logs to attribute crash reports.
    POST_NOTIFICATIONS grant/revoke failures on older Android versions are not
    silently skipped. A PASS covers passive startup only, not in-app features.
.PARAMETER Serial
    Attached adb device serial. If omitted, exactly one attached device is
    required, including offline or unauthorized devices in the count.
.EXAMPLE
    .\phone-smoke.ps1
.EXAMPLE
    .\phone-smoke.ps1 -Serial <device-serial>
#>
#Requires -Version 5.1
[CmdletBinding()]
param([string]$Serial)

Set-StrictMode -Version Latest
# Native adb progress goes to stderr; do not turn it into a terminating error
# or merge it into stdout on Windows PowerShell 5.1. Check every exit code.
$ErrorActionPreference = 'Continue'
$script:Failed = $false
# Set once this run starts changing permissions, so the restore step knows
# whether the phone needs fixing up on the way out.
$script:PermissionsChanged = $false
$PackageName = 'com.zeroflux.cprcoach'
$Permissions = @(
    'android.permission.CAMERA',
    'android.permission.RECORD_AUDIO',
    'android.permission.POST_NOTIFICATIONS'
)

function Write-Result {
    param([bool]$Passed, [string]$Step, [string]$Detail = '')
    if ($Passed) {
        Write-Host "PASS: $Step" -ForegroundColor Green
    } else {
        $script:Failed = $true
        Write-Host "FAIL: $Step $Detail" -ForegroundColor Red
    }
}

function Invoke-Adb {
    param([string]$Step, [string[]]$AdbArguments)
    try {
        $global:LASTEXITCODE = 0
        $lines = @(& $script:AdbPath @AdbArguments)
        $exitCode = $LASTEXITCODE
    } catch {
        Write-Result $false $Step $_.Exception.Message
        return [pscustomobject]@{ Success = $false; Lines = @() }
    }
    $success = $exitCode -eq 0
    Write-Result $success $Step "(adb exit $exitCode)"
    if (-not $success) {
        foreach ($line in $lines) { Write-Host $line }
    }
    return [pscustomobject]@{ Success = $success; Lines = $lines }
}

function Set-PermissionsGranted {
    # Runs on the way out, including after a failure, and clears the flag so a
    # second call cannot repeat the grants. Each pm grant exit code is checked.
    if (-not $script:PermissionsChanged) { return }
    $script:PermissionsChanged = $false
    Write-Host 'Granting camera, microphone and notification permissions again'
    foreach ($permission in $Permissions) {
        $null = Invoke-Adb "restore grant $permission" ($script:DeviceArgs + @(
            'shell', 'pm', 'grant', $PackageName, $permission
        ))
    }
}

function Test-Startup {
    param([string]$Phase)

    $start = Invoke-Adb "$Phase launch command" ($script:DeviceArgs + @(
        'shell', 'am', 'start', '-W', '-n', "$PackageName/.MainActivity"
    ))
    # Activity Manager can report an error in stdout while adb itself exits zero.
    if ($start.Success) {
        $launchText = $start.Lines -join "`n"
        $launched = ($launchText -match '(?m)^Status:\s*ok\s*$') -and
            ($launchText -notmatch '(?im)^\s*(?:Error|Exception|SecurityException)\b')
        Write-Result $launched "$Phase activity started" $launchText
    }

    # Passive observation window, not a measured performance claim.
    Start-Sleep -Seconds 5
    Write-Result $true "$Phase startup wait"

    $process = Invoke-Adb "$Phase pidof command" ($script:DeviceArgs + @(
        'shell', 'pidof', $PackageName
    ))
    if ($process.Success) {
        $processIds = ($process.Lines -join ' ').Trim()
        Write-Result ($processIds -match '^\d+(?:\s+\d+)*$') "$Phase process alive" $processIds
    }

    # Pull a file rather than redirecting binary stdout through PowerShell 5.1.
    $remoteCapture = "/data/local/tmp/cpr-coach-smoke-$script:RunStamp-$Phase.png"
    $localCapture = Join-Path $script:EvidenceDir "$Phase.png"
    $capture = Invoke-Adb "$Phase screencap" ($script:DeviceArgs + @(
        'shell', 'screencap', '-p', $remoteCapture
    ))
    if ($capture.Success) {
        $pull = Invoke-Adb "$Phase pull screenshot" ($script:DeviceArgs + @(
            'pull', $remoteCapture, $localCapture
        ))
        if ($pull.Success) {
            $hasCapture = (Test-Path -LiteralPath $localCapture -PathType Leaf) -and
                ((Get-Item -LiteralPath $localCapture -ErrorAction Stop).Length -gt 0)
            Write-Result $hasCapture "$Phase screenshot saved" $localCapture
        }
    }
    # Delete only this run's temporary screenshot, never shared phone files.
    $null = Invoke-Adb "$Phase remove temporary screenshot" ($script:DeviceArgs + @(
        'shell', 'rm', '-f', $remoteCapture
    ))

    $logs = Invoke-Adb "$Phase logcat dump" ($script:DeviceArgs + @('logcat', '-d'))
    if ($logs.Success) {
        $logPath = Join-Path $script:EvidenceDir "$Phase-logcat.txt"
        $logs.Lines | Set-Content -LiteralPath $logPath -Encoding UTF8 -ErrorAction Stop
        $hasLogs = ($logs.Lines.Count -gt 0) -and
            (-not [string]::IsNullOrWhiteSpace(($logs.Lines -join "`n")))
        Write-Result $hasLogs "$Phase logcat saved" $logPath
        if ($hasLogs) {
            $packagePattern = [regex]::Escape($PackageName)
            $text = $logs.Lines -join "`n"
            $namedTombstone = $text -match "(?im)(?:>>>\s*$packagePattern(?::[^\s<>]+)?\s*<<<|Cmdline:\s*$packagePattern(?::\S+)?(?:\s|$))"
            $crashLines = @($logs.Lines | Where-Object {
                ($_ -match '(?i)FATAL EXCEPTION|SIGSEGV|SIGABRT') -or
                ($_ -match "(?i)(?:\babort\w*\b.*$packagePattern|$packagePattern.*\babort\w*\b|Fatal signal.*$packagePattern)") -or
                ($namedTombstone -and ($_ -match '(?i)Abort message:|\babort\(\)'))
            })
            Write-Result ($crashLines.Count -eq 0) "$Phase crash scan" ($crashLines -join "`n")
        }
    }
}

try {
    $adb = Get-Command adb -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $adb) {
        Write-Result $false 'locate adb' 'Put Android platform-tools on PATH.'
        exit 1
    }
    $script:AdbPath = $adb.Source
    Write-Result $true 'locate adb'

    $discovery = Invoke-Adb 'list attached devices' @('devices')
    if (-not $discovery.Success) { exit 1 }
    $devices = @(
        foreach ($line in $discovery.Lines) {
            if ([string]::IsNullOrWhiteSpace($line) -or
                $line -match '^\s*List of devices attached\s*$') { continue }
            if ($line -notmatch '^\s*(\S+)\s+(.+?)\s*$') {
                Write-Result $false 'parse attached devices' "Unrecognized adb output: $line"
                exit 1
            }
            [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2] }
        }
    )
    if ($PSBoundParameters.ContainsKey('Serial')) {
        if ([string]::IsNullOrWhiteSpace($Serial)) {
            Write-Result $false 'select device' 'Serial must not be blank.'
            exit 1
        }
        $selected = @($devices | Where-Object { $_.Serial -ceq $Serial })
        if ($selected.Count -ne 1) {
            Write-Result $false 'select device' 'The requested serial is not uniquely attached.'
            exit 1
        }
    } else {
        if ($devices.Count -ne 1) {
            Write-Result $false 'select device' "Found $($devices.Count) devices; attach exactly one or pass -Serial."
            exit 1
        }
        $selected = @($devices[0])
        $Serial = $selected[0].Serial
    }
    if ($selected[0].State -ne 'device') {
        Write-Result $false 'device ready' "Device $Serial is $($selected[0].State)."
        exit 1
    }
    Write-Result $true "select ready device $Serial"
    $script:DeviceArgs = @('-s', $Serial)

    $installed = Invoke-Adb 'query installed package' ($script:DeviceArgs + @(
        'shell', 'pm', 'path', $PackageName
    ))
    if (-not $installed.Success) { exit 1 }
    if (($installed.Lines -join "`n") -notmatch '(?m)^package:.+') {
        Write-Result $false 'confirm app installed' $PackageName
        exit 1
    }
    Write-Result $true 'confirm app installed'

    $script:RunStamp = Get-Date -Format 'yyyyMMdd-HHmmss-fffffff'
    $script:EvidenceDir = Join-Path ([IO.Path]::GetTempPath()) "cpr-coach-phone-smoke\$script:RunStamp"
    $null = New-Item -ItemType Directory -Path $script:EvidenceDir -ErrorAction Stop
    Write-Result $true "create evidence folder $script:EvidenceDir"

    $null = Invoke-Adb 'granted clear logcat' ($script:DeviceArgs + @('logcat', '-c'))
    $script:PermissionsChanged = $true
    foreach ($permission in $Permissions) {
        $null = Invoke-Adb "grant $permission" ($script:DeviceArgs + @(
            'shell', 'pm', 'grant', $PackageName, $permission
        ))
    }
    Test-Startup 'granted'

    $null = Invoke-Adb 'force-stop before denied launch' ($script:DeviceArgs + @(
        'shell', 'am', 'force-stop', $PackageName
    ))
    $null = Invoke-Adb 'denied clear logcat' ($script:DeviceArgs + @('logcat', '-c'))
    foreach ($permission in $Permissions) {
        $null = Invoke-Adb "revoke $permission" ($script:DeviceArgs + @(
            'shell', 'pm', 'revoke', $PackageName, $permission
        ))
    }
    Test-Startup 'denied'
    Write-Host "Evidence: $script:EvidenceDir"
} catch {
    Write-Result $false 'smoke script' $_.Exception.Message
}

# Leave the phone able to use the app, even when a step above failed.
Set-PermissionsGranted

if ($script:Failed) {
    Write-Host 'FAIL: phone smoke test' -ForegroundColor Red
    exit 1
}
Write-Host 'PASS: phone smoke test' -ForegroundColor Green
exit 0
