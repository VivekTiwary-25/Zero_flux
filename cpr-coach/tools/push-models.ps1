<#
.SYNOPSIS
    Puts the CPR Coach speech models on an Android phone.

.DESCRIPTION
    Checks the IndicConformer model folder, finds the phone, checks that the app
    is installed and that the phone has room, then copies the models into the
    app's private files folder.

    With -CopyWhisper it only copies the English Whisper files from iTantra into
    this repository's app assets. Use that before a build.

.PARAMETER Serial
    The adb serial of the phone to use. If omitted, the only attached phone is
    used. The script stops if no phone or more than one phone is attached.

.PARAMETER ModelDir
    The IndicConformer model folder on this PC. It must contain
    shared\encoder.weights.bin, kn.onnx and languages\kn\tokens.txt.

.PARAMETER CopyWhisper
    Copy tiny.en-encoder.int8.onnx, tiny.en-decoder.int8.onnx and
    tiny.en-tokens.txt from iTantra into app\src\main\assets, then stop.

.EXAMPLE
    .\push-models.ps1

.EXAMPLE
    .\push-models.ps1 -Serial R58M123ABC

.EXAMPLE
    .\push-models.ps1 -CopyWhisper
#>

[CmdletBinding()]
param(
    [string]$Serial,
    [string]$ModelDir = 'D:\projects\sih\iTantra\model-staging\indicconformer',
    [switch]$CopyWhisper
)

Set-StrictMode -Version Latest

# adb writes transfer progress and warnings to stderr. Windows PowerShell 5.1 can
# treat stderr from a native program as a terminating NativeCommandError, so the
# script does not set $ErrorActionPreference to Stop. Every adb step is checked
# with $LASTEXITCODE instead.
$ErrorActionPreference = 'Continue'

$PackageName = 'com.zeroflux.cprcoach'
$RemoteStagingDir = '/data/local/tmp/indicconformer'
$RemoteAppDir = 'files/indicconformer'
$WhisperSourceDir = 'D:\projects\sih\iTantra\app\src\main\assets'
$WhisperFiles = @(
    'tiny.en-encoder.int8.onnx',
    'tiny.en-decoder.int8.onnx',
    'tiny.en-tokens.txt'
)
# 1.5 GB in the 1K blocks that "adb shell df -k" reports.
$RequiredFreeKb = [int64](1.5 * 1GB / 1KB)

function Write-Head {
    param([string]$Message)
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok {
    param([string]$Message)
    Write-Host "  $Message" -ForegroundColor Green
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

function Copy-WhisperFiles {
    Write-Head 'Copying the English Whisper files into the app'

    if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
        Stop-Script 'The script folder could not be found. Run this script as a file.'
    }
    $repoRoot = Split-Path -Parent $PSScriptRoot
    $assetsDir = Join-Path $repoRoot 'app\src\main\assets'

    if (-not (Test-Path -LiteralPath $WhisperSourceDir -PathType Container)) {
        Stop-Script "The iTantra assets folder was not found: $WhisperSourceDir"
    }
    if (-not (Test-Path -LiteralPath $assetsDir -PathType Container)) {
        Stop-Script "The app assets folder was not found: $assetsDir. Run this script from the cpr-coach checkout."
    }

    foreach ($name in $WhisperFiles) {
        $source = Join-Path $WhisperSourceDir $name
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            Stop-Script "The Whisper file was not found: $source"
        }
    }

    foreach ($name in $WhisperFiles) {
        $source = Join-Path $WhisperSourceDir $name
        $destination = Join-Path $assetsDir $name
        Write-Host "  $name"
        try {
            Copy-Item -LiteralPath $source -Destination $destination -Force -ErrorAction Stop
        } catch {
            Stop-Script "Could not copy $source to $destination. $($_.Exception.Message)"
        }

        if (-not (Test-Path -LiteralPath $destination -PathType Leaf)) {
            Stop-Script "The copy did not create $destination"
        }
        $sourceSize = (Get-Item -LiteralPath $source -ErrorAction Stop).Length
        $destinationSize = (Get-Item -LiteralPath $destination -ErrorAction Stop).Length
        if ($sourceSize -ne $destinationSize) {
            Stop-Script "The copied file has the wrong size: $destination"
        }
        Write-Host ('    {0:N1} MB' -f ($destinationSize / 1MB))
    }

    Write-Ok 'Whisper files copied. The next build will include them.'
}

function Select-Phone {
    param([string]$RequestedSerial)

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

    if (-not [string]::IsNullOrWhiteSpace($RequestedSerial)) {
        $wanted = $RequestedSerial.Trim()
        $selectedDevice = @($devices | Where-Object { $_.Serial -eq $wanted })
        if ($selectedDevice.Count -eq 0) {
            Stop-Script "No phone with serial '$wanted' is attached. Run 'adb devices' to see the serials."
        }
        if ($selectedDevice[0].State -ne 'device') {
            Stop-Script "Phone '$wanted' is '$($selectedDevice[0].State)', not ready. Unlock it, allow USB debugging, then run this script again."
        }
        Write-Ok "Using phone $wanted."
        return $wanted
    }

    $ready = @($devices | Where-Object { $_.State -eq 'device' })
    if ($ready.Count -eq 0) {
        if ($devices.Count -gt 0) {
            $summary = ($devices | ForEach-Object { "$($_.Serial) ($($_.State))" }) -join ', '
            Stop-Script "No phone is ready. adb sees: $summary. Unlock the phone and allow USB debugging, then run this script again."
        }
        Stop-Script 'No phone is attached. Connect a phone with a USB cable, turn on USB debugging, then run this script again.'
    }
    if ($ready.Count -gt 1) {
        $serials = ($ready | ForEach-Object { $_.Serial }) -join ', '
        Stop-Script "Several phones are attached: $serials. Run this script again with -Serial <serial> to choose one."
    }

    Write-Ok "Using phone $($ready[0].Serial)."
    return $ready[0].Serial
}

function Test-CprCoachInstalled {
    param([string]$TargetSerial)

    Write-Head 'Checking that CPR Coach is installed'
    $packageLines = @(Invoke-Adb -What 'Asking the phone for its installed packages...' -AdbArguments @('-s', $TargetSerial, 'shell', 'pm', 'list', 'packages') -Capture)

    foreach ($line in $packageLines) {
        if ("$line".Trim() -eq "package:$PackageName") {
            Write-Ok 'CPR Coach is installed.'
            return
        }
    }

    Stop-Script "CPR Coach ($PackageName) is not installed on phone $TargetSerial. Install the app first, then run this script again."
}

function Test-FreeSpace {
    param([string]$TargetSerial)

    Write-Head 'Checking free space on the phone'
    $dfLines = @(Invoke-Adb -What 'Asking the phone how much space is left in /data/local/tmp...' -AdbArguments @('-s', $TargetSerial, 'shell', 'df', '-k', '/data/local/tmp') -Capture)

    # Android's df prints "Filesystem 1K-blocks Used Available Use% Mounted on".
    # The fourth number is the free space in 1K blocks.
    $availableKb = [int64](-1)
    foreach ($line in $dfLines) {
        $text = "$line".Trim()
        if ($text -match '^(\S+)\s+(\d+)\s+(\d+)\s+(\d+)\s+\d+%\s+(\S+)$') {
            $mount = $Matches[5]
            $freeKb = [int64]$Matches[4]
            if ($availableKb -lt 0 -or $mount -eq '/data' -or $mount -eq $RemoteStagingDir) {
                $availableKb = $freeKb
            }
            if ($mount -eq '/data' -or $mount -eq $RemoteStagingDir) {
                break
            }
        }
    }

    if ($availableKb -lt 0) {
        Write-Host '  The phone did not return a free-space report:' -ForegroundColor Yellow
        foreach ($line in $dfLines) {
            Write-Host "    $line"
        }
        Stop-Script "Could not read the free space for /data/local/tmp on phone $TargetSerial."
    }

    $availableGb = $availableKb * 1KB / 1GB
    if ($availableKb -lt $RequiredFreeKb) {
        Stop-Script ('Not enough free space on phone {0}. /data/local/tmp has {1:N2} GB free; the models need about 1.5 GB. Free some space, then run this script again.' -f $TargetSerial, $availableGb)
    }
    Write-Ok ('{0:N2} GB free in /data/local/tmp (need about 1.5 GB).' -f $availableGb)
}

function Push-Models {
    param([string]$TargetSerial, [string]$SourceDir)

    Write-Head 'Pushing the models to the phone'
    Write-Host '  This copies the whole model folder. It can take a while.'

    # adb copies a directory into the destination as a child when the destination
    # already exists. Clear any old copy first, so the files cannot end up nested
    # in /data/local/tmp/indicconformer/indicconformer.
    Invoke-Adb -What "Clearing any old copy in $RemoteStagingDir..." -AdbArguments @('-s', $TargetSerial, 'shell', "rm -rf $RemoteStagingDir")

    Invoke-Adb -What "Copying $SourceDir to $RemoteStagingDir..." -AdbArguments @('-s', $TargetSerial, 'push', $SourceDir, $RemoteStagingDir)

    $stageLines = @(Invoke-Adb -What 'Checking that the files landed in the right place...' -AdbArguments @('-s', $TargetSerial, 'shell', "ls $RemoteStagingDir") -Capture)
    $hasModel = $false
    foreach ($line in $stageLines) {
        if ("$line".Trim() -eq 'kn.onnx') {
            $hasModel = $true
            break
        }
    }
    if (-not $hasModel) {
        Write-Host '  The phone holds:' -ForegroundColor Yellow
        foreach ($line in $stageLines) {
            Write-Host "    $line"
        }
        Stop-Script "The push did not put kn.onnx directly in $RemoteStagingDir. Clear that folder on the phone and run this script again."
    }
    Write-Ok 'The models are in the temporary folder on the phone.'

    $copyCommand = "run-as $PackageName sh -c 'mkdir -p files && rm -rf $RemoteAppDir && cp -r $RemoteStagingDir $RemoteAppDir'"
    Invoke-Adb -What 'Copying the models into the app private folder...' -AdbArguments @('-s', $TargetSerial, 'shell', $copyCommand)

    Invoke-Adb -What 'Clearing the temporary files from the phone...' -AdbArguments @('-s', $TargetSerial, 'shell', "rm -rf $RemoteStagingDir")

    $appLines = @(Invoke-Adb -What 'Listing the files in the app private folder...' -AdbArguments @('-s', $TargetSerial, 'shell', "run-as $PackageName ls -lR $RemoteAppDir") -Capture)
    foreach ($line in $appLines) {
        Write-Host "    $line"
    }

    $hasAppModel = $false
    foreach ($line in $appLines) {
        if ("$line" -match 'kn\.onnx') {
            $hasAppModel = $true
            break
        }
    }
    if (-not $hasAppModel) {
        Stop-Script 'The app private folder does not contain kn.onnx after the copy.'
    }
    Write-Ok 'The models are in the app private folder.'
}

Write-Host ''
Write-Host 'CPR Coach model setup' -ForegroundColor Cyan

if ($CopyWhisper) {
    Copy-WhisperFiles
    Write-Host ''
    Write-Host 'DONE: Whisper files are in app\src\main\assets. Rebuild the app to include them.' -ForegroundColor Green
    exit 0
}

Write-Head 'Checking the model folder'
if (-not (Test-Path -LiteralPath $ModelDir -PathType Container)) {
    Stop-Script "The model folder was not found: $ModelDir`nPass the correct folder with -ModelDir."
}
$resolvedModelDir = Resolve-Path -LiteralPath $ModelDir -ErrorAction SilentlyContinue
if ($null -eq $resolvedModelDir) {
    Stop-Script "The model folder could not be read: $ModelDir"
}
$ModelDir = $resolvedModelDir.Path

$requiredFiles = @(
    'shared\encoder.weights.bin',
    'kn.onnx',
    'languages\kn\tokens.txt'
)
$missingFiles = @()
foreach ($relativePath in $requiredFiles) {
    $fullPath = Join-Path $ModelDir $relativePath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        $missingFiles += $fullPath
    }
}
if ($missingFiles.Count -gt 0) {
    Write-Host '  The model folder is incomplete. These files are missing:' -ForegroundColor Red
    foreach ($fullPath in $missingFiles) {
        Write-Host "    $fullPath" -ForegroundColor Red
    }
    Stop-Script 'Use a complete IndicConformer model folder and run this script again.'
}
Write-Ok "Model folder is complete: $ModelDir"

Write-Head 'Finding adb'
$adbCommand = Get-Command -Name 'adb' -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -ne $adbCommand) {
    $script:AdbPath = $adbCommand.Source
    Write-Ok "Using adb from PATH: $script:AdbPath"
} else {
    $sdkAdb = $null
    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $sdkAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    }
    if ($null -ne $sdkAdb -and (Test-Path -LiteralPath $sdkAdb -PathType Leaf)) {
        $script:AdbPath = $sdkAdb
        Write-Ok "Using adb from the Android SDK: $script:AdbPath"
    } else {
        Stop-Script 'adb was not found. Add the Android platform-tools folder to PATH, or install the Android SDK so that %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe exists.'
    }
}

$SelectedSerial = Select-Phone -RequestedSerial $Serial
Test-CprCoachInstalled -TargetSerial $SelectedSerial
Test-FreeSpace -TargetSerial $SelectedSerial
Push-Models -TargetSerial $SelectedSerial -SourceDir $ModelDir

Write-Host ''
Write-Host "DONE: CPR Coach models are on phone $SelectedSerial." -ForegroundColor Green
exit 0
