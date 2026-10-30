<#
.SYNOPSIS
    Copies CPR Coach session traces from an Android phone and summarizes each one.

    Finds the phone, pulls /sdcard/Android/data/com.zeroflux.cprcoach/files/traces/
    into a local folder, then runs analyze_trace.py on the selected CSV traces.
    With -Frames, it also pulls the matching frame folders and makes contact sheets.

    -Latest limits processing to the newest session IDs; by default all sessions
    are included. The phone is only read. This script never installs, deletes or
    starts anything on it.

.PARAMETER Serial
    The adb serial of the phone to use. If omitted, the only attached phone is
    used. The script stops if no phone or more than one phone is attached.

.PARAMETER Out
    The local folder for the pulled traces. Defaults to a traces folder beside
    this script.

.PARAMETER Frames
    Also pull session frame pictures and generate contact sheets.

.PARAMETER Latest
    Process only the N newest sessions by session ID. Defaults to all sessions.

.EXAMPLE
    .\pull-traces.ps1

.EXAMPLE
    .\pull-traces.ps1 -Serial R58M123ABC

.EXAMPLE
    .\pull-traces.ps1 -Out D:\cpr-traces

.EXAMPLE
    .\pull-traces.ps1 -Frames -Latest 3
#>
#Requires -Version 5.1

[CmdletBinding()]
param(
    [string]$Serial,
    [string]$Out,
    [switch]$Frames,
    [ValidateRange(1, [int]::MaxValue)][int]$Latest
)

Set-StrictMode -Version Latest

# adb writes transfer progress and warnings to stderr. Windows PowerShell 5.1 can
# treat stderr from a native program as a terminating NativeCommandError, so the
# script does not set $ErrorActionPreference to Stop. Every adb step is checked
# with $LASTEXITCODE instead.
$ErrorActionPreference = 'Continue'

$PackageName = 'com.zeroflux.cprcoach'
$RemoteTracesDir = "/sdcard/Android/data/$PackageName/files/traces"
$AnalyzerName = 'analyze_trace.py'
$FrameSheetName = 'frame_sheet.py'

function Write-Head {
    param([string]$Message)
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok {
    param([string]$Message)
    Write-Host "  $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "  $Message" -ForegroundColor Yellow
}

function Stop-Script {
    param([string]$Message)
    Write-Host ''
    Write-Host "STOPPED: $Message" -ForegroundColor Red
    exit 1
}

# Runs adb, prints what is happening, and stops the script on a non-zero exit code.
# adb's own output goes straight to the console. Its stderr is left alone: merging
# stderr into stdout makes Windows PowerShell 5.1 report normal adb progress as an
# error.
function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)][string]$What,
        [Parameter(Mandatory = $true)][string[]]$AdbArguments,
        [switch]$Capture
    )

    Write-Host "  $What"

    $global:LASTEXITCODE = 0
    if ($Capture) {
        $output = @(& $script:AdbPath @AdbArguments)
    } else {
        & $script:AdbPath @AdbArguments
    }

    if ($LASTEXITCODE -ne 0) {
        Stop-Script "adb failed with exit code $LASTEXITCODE while $What"
    }

    if ($Capture) {
        return $output
    }
}

function Find-Adb {
    Write-Head 'Finding adb'

    $adbCommand = Get-Command -Name 'adb' -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $adbCommand) {
        $script:AdbPath = $adbCommand.Source
        Write-Ok "Using adb from PATH: $script:AdbPath"
        return
    }

    $sdkAdb = $null
    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $sdkAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    }
    if ($null -ne $sdkAdb -and (Test-Path -LiteralPath $sdkAdb -PathType Leaf)) {
        $script:AdbPath = $sdkAdb
        Write-Ok "Using adb from the Android SDK: $script:AdbPath"
        return
    }

    Stop-Script 'adb was not found. Add the Android platform-tools folder to PATH, or install the Android SDK so that %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe exists.'
}

function Select-Phone {
    param(
        [string]$RequestedSerial,
        [switch]$SerialSpecified
    )

    Write-Head 'Looking for the phone'
    $deviceLines = @(Invoke-Adb -What 'Asking adb which phones are attached...' -AdbArguments @('devices') -Capture)

    $devices = @()
    foreach ($line in $deviceLines) {
        $text = "$line".Trim()
        if ($text -match '^(\S+)\s+(device|offline|unauthorized|no permissions.*|recovery|sideload|bootloader)$') {
            $devices += [pscustomobject]@{
                Serial = $Matches[1]
                State  = $Matches[2]
            }
        }
    }

    $selectedDevice = $null
    if ($SerialSpecified) {
        if ([string]::IsNullOrWhiteSpace($RequestedSerial)) {
            Stop-Script 'The -Serial value is empty. Pass a serial, or omit -Serial to use the only attached phone.'
        }
        $wanted = $RequestedSerial.Trim()
        $serialMatches = @($devices | Where-Object { $_.Serial -ceq $wanted })
        if ($serialMatches.Count -eq 0) {
            Stop-Script "No phone with serial '$wanted' is attached. Run 'adb devices' to see the serials."
        }
        $selectedDevice = $serialMatches[0]
    } else {
        if ($devices.Count -eq 0) {
            Stop-Script 'No phone is attached. Connect a phone with a USB cable, turn on USB debugging, then run this script again.'
        }
        if ($devices.Count -gt 1) {
            $summary = ($devices | ForEach-Object { "$($_.Serial) ($($_.State))" }) -join ', '
            Stop-Script "Several phones are attached: $summary. Run this script again with -Serial <serial> to choose one."
        }
        $selectedDevice = $devices[0]
    }

    if ($selectedDevice.State -ne 'device') {
        Stop-Script "Phone '$($selectedDevice.Serial)' is '$($selectedDevice.State)', not ready. Unlock it, allow USB debugging, then run this script again."
    }
    Write-Ok "Using phone $($selectedDevice.Serial)."
    return $selectedDevice.Serial
}

function Find-Analyzer {
    Write-Head 'Finding the trace analyzer'

    if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
        Stop-Script 'The script folder could not be found. Run this script as a file.'
    }

    $script:AnalyzerPath = Join-Path $PSScriptRoot $AnalyzerName
    if (-not (Test-Path -LiteralPath $script:AnalyzerPath -PathType Leaf)) {
        Stop-Script "The trace analyzer was not found: $($script:AnalyzerPath)"
    }
    Write-Ok "Using analyzer: $($script:AnalyzerPath)"
}

function Find-FrameSheet {
    $script:FrameSheetPath = Join-Path $PSScriptRoot $FrameSheetName
    if (-not (Test-Path -LiteralPath $script:FrameSheetPath -PathType Leaf)) {
        Stop-Script "The frame sheet generator was not found: $($script:FrameSheetPath)"
    }
    Write-Ok "Using frame sheet generator: $($script:FrameSheetPath)"
}

function Find-Python {
    Write-Head 'Finding Python'

    $pythonCommand = Get-Command -Name 'python' -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $pythonCommand) {
        Stop-Script 'python was not found on PATH. Install Python 3 with the numpy package, then run this script again.'
    }
    $script:PythonPath = $pythonCommand.Source
    Write-Ok "Using Python: $($script:PythonPath)"
}

function Resolve-TraceFolder {
    param([string]$RequestedOut)

    Write-Head 'Preparing the local trace folder'

    if ([string]::IsNullOrWhiteSpace($RequestedOut)) {
        if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
            Stop-Script 'The script folder could not be found. Run this script as a file, or pass -Out with a folder.'
        }
        $RequestedOut = Join-Path $PSScriptRoot 'traces'
    }

    if (Test-Path -LiteralPath $RequestedOut -PathType Leaf) {
        Stop-Script "The trace folder path is a file: $RequestedOut. Pass a folder with -Out."
    }

    if (-not (Test-Path -LiteralPath $RequestedOut -PathType Container)) {
        try {
            New-Item -ItemType Directory -Path $RequestedOut -Force -ErrorAction Stop | Out-Null
        } catch {
            Stop-Script "Could not create the trace folder $RequestedOut. $($_.Exception.Message)"
        }
    }

    $resolved = Resolve-Path -LiteralPath $RequestedOut -ErrorAction SilentlyContinue
    if ($null -eq $resolved) {
        Stop-Script "The trace folder could not be read: $RequestedOut"
    }
    Write-Ok "Saving traces to $($resolved.Path)"
    return $resolved.Path
}

function Copy-Traces {
    param([string]$TargetSerial, [string]$OutDir)

    Write-Head 'Copying the traces from the phone'
    Write-Host '  This only reads from the phone. It does not install, delete or start anything.'

    # adb copies the contents of a remote folder into the destination only when
    # the destination does not exist yet. Pull into a fresh temporary folder and
    # copy its contents across, so OutDir holds the CSVs, not another traces folder.
    $stagingDir = Join-Path ([System.IO.Path]::GetTempPath()) ("cpr-coach-traces-" + [guid]::NewGuid().ToString('N'))

    Invoke-Adb -What "Copying $RemoteTracesDir to $OutDir..." -AdbArguments @('-s', $TargetSerial, 'pull', "$RemoteTracesDir/", $stagingDir)

    $copyFailure = $null
    $copyFinished = $false
    try {
        if (Test-Path -LiteralPath $stagingDir -PathType Container) {
            $pulledItems = @(Get-ChildItem -LiteralPath $stagingDir -Filter 'session-*.csv' -File -Recurse -ErrorAction Stop)
            foreach ($item in $pulledItems) {
                Copy-Item -LiteralPath $item.FullName -Destination $OutDir -Force -ErrorAction Stop
            }
        }
        $copyFinished = $true
    } catch {
        $copyFailure = "Could not copy the pulled traces into $OutDir. The pulled files are still in $stagingDir. $($_.Exception.Message)"
    }

    if ($copyFinished) {
        Remove-Item -LiteralPath $stagingDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    if ($null -ne $copyFailure) {
        Stop-Script $copyFailure
    }

    Write-Ok 'The traces are on this PC.'
}

function Get-SessionId {
    param([string]$Path)
    if ($Path -match '^session-(.+)\.csv$') {
        return $Matches[1]
    }
    return $null
}

function Get-SessionDirectories {
    param([string]$Root)
    return @(Get-ChildItem -LiteralPath $Root -Directory -Filter 'frames-*' -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^frames-(.+)$' } | Sort-Object -Property @{ Expression = { $_.Name.Substring(7) }; Descending = $true })
}

function Copy-Frames {
    param([string]$TargetSerial, [string]$OutDir, [int]$Latest)

    Write-Head 'Copying frame pictures from the phone'
    $remoteFramesDir = "$RemoteTracesDir/frames-*"
    $stagingDir = Join-Path ([System.IO.Path]::GetTempPath()) ("cpr-coach-frames-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $stagingDir -Force | Out-Null

    # Pass the remote Android path as one PowerShell argument; Git Bash rewrites /sdcard paths.
    Invoke-Adb -What "Copying frame folders from $RemoteTracesDir..." -AdbArguments @('-s', $TargetSerial, 'pull', $remoteFramesDir, $stagingDir)

    $pulledFolders = @(Get-ChildItem -LiteralPath $stagingDir -Directory -Filter 'frames-*' -ErrorAction SilentlyContinue)
    $sessions = @(Get-ChildItem -LiteralPath $OutDir -Filter 'session-*.csv' -File -ErrorAction SilentlyContinue | ForEach-Object { Get-SessionId -Path $_.Name } | Where-Object { $null -ne $_ } | Sort-Object -Unique)
    if ($Latest -gt 0) { $sessions = @($sessions | Select-Object -Last $Latest) }

    foreach ($sessionId in $sessions) {
        $frameFolderName = "frames-$sessionId"
        $sourceFolder = $pulledFolders | Where-Object { $_.Name -ceq $frameFolderName } | Select-Object -First 1
        if ($null -eq $sourceFolder) { continue }

        $destinationFolder = Join-Path $OutDir $frameFolderName
        $sourceCount = @(Get-ChildItem -LiteralPath $sourceFolder.FullName -File -Recurse -ErrorAction SilentlyContinue).Count
        $destinationCount = 0
        if (Test-Path -LiteralPath $destinationFolder -PathType Container) {
            $destinationCount = @(Get-ChildItem -LiteralPath $destinationFolder -File -Recurse -ErrorAction SilentlyContinue).Count
        }
        if ((Test-Path -LiteralPath $destinationFolder -PathType Container) -and $sourceCount -eq $destinationCount) {
            Write-Ok "Skipping $frameFolderName; it already has $destinationCount files."
        } else {
            Copy-Item -LiteralPath $sourceFolder.FullName -Destination $OutDir -Recurse -Force
            Write-Ok "Pulled $frameFolderName ($sourceCount files)."
        }
    }

    Remove-Item -LiteralPath $stagingDir -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host 'CPR Coach trace pull' -ForegroundColor Cyan

Find-Adb
$serialSpecified = $PSBoundParameters.ContainsKey('Serial')
$SelectedSerial = Select-Phone -RequestedSerial $Serial -SerialSpecified:$serialSpecified
Find-Python
Find-Analyzer
if ($Frames) { Find-FrameSheet }
$OutDir = Resolve-TraceFolder -RequestedOut $Out
Copy-Traces -TargetSerial $SelectedSerial -OutDir $OutDir

$LatestCount = 0
if ($PSBoundParameters.ContainsKey('Latest')) { $LatestCount = $Latest }
$sessionCsvs = @(Get-ChildItem -LiteralPath $OutDir -Filter 'session-*.csv' -File -Recurse -ErrorAction SilentlyContinue | Sort-Object -Property FullName)
if ($LatestCount -gt 0) {
    $sessionCsvs = @($sessionCsvs | Sort-Object -Property { Get-SessionId -Path $_.Name } | Select-Object -Last $LatestCount)
}

Write-Head 'Summarizing the traces'
$analysisFailures = 0
if ($sessionCsvs.Count -eq 0) {
    Write-Warn "No CSV traces were found under $OutDir."
    Write-Warn 'Run a Practise session on the phone, then run this script again.'
} else {
    foreach ($csv in $sessionCsvs) {
        Write-Head "Analyzing $($csv.FullName)"
        $global:LASTEXITCODE = 0
        & $script:PythonPath $script:AnalyzerPath $csv.FullName
        if ($LASTEXITCODE -ne 0) {
            $analysisFailures++
            Write-Warn "analyze_trace.py failed for $($csv.Name) with exit code $LASTEXITCODE."
        }
    }
}

$sheetFailures = 0
if ($Frames) {
    Copy-Frames -TargetSerial $SelectedSerial -OutDir $OutDir -Latest $LatestCount
    $frameFolders = @($sessionCsvs | ForEach-Object {
        $framePath = Join-Path $OutDir ("frames-" + (Get-SessionId -Path $_.Name))
        if (Test-Path -LiteralPath $framePath -PathType Container) { Get-Item -LiteralPath $framePath }
    })
    $sheetsDir = Join-Path $OutDir 'sheets'
    foreach ($frameFolder in $frameFolders) {
        $sessionId = $frameFolder.Name.Substring(7)
        Write-Head "Creating frame sheet for $sessionId"
        $global:LASTEXITCODE = 0
        & $script:PythonPath $script:FrameSheetPath $OutDir $sessionId $sheetsDir
        if ($LASTEXITCODE -ne 0) {
            $sheetFailures++
            Write-Warn "frame_sheet.py failed for $sessionId with exit code $LASTEXITCODE."
        } else {
            $sheetPaths = @(Get-ChildItem -LiteralPath $sheetsDir -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match [regex]::Escape($sessionId) } | Sort-Object -Property FullName)
            foreach ($sheet in $sheetPaths) { Write-Ok "Sheet: $($sheet.FullName)" }
        }
    }
}

Write-Host ''
if ($analysisFailures -gt 0 -or $sheetFailures -gt 0) {
    Write-Host "DONE with $analysisFailures trace(s) and $sheetFailures frame sheet(s) that could not be summarized. The files are in $OutDir." -ForegroundColor Yellow
    exit 1
}
Write-Host "DONE: $($sessionCsvs.Count) trace(s) are in $OutDir." -ForegroundColor Green
exit 0
