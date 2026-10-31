<#
.SYNOPSIS
    The merge gate. Proves Practise is unchanged from ee682a1. Run from anywhere in a checkout. Exit 0 means pass.
.DESCRIPTION
    See docs/PLAN_0810.md. Fails on: an edited protected file, changed CoachPolicy defaults, an edited Practise
    test, edited recordings or locked data, any failing unit test, or any difference in the Practise replay.
#>
$ErrorActionPreference = 'Stop'
$Base = 'ee682a1'
$BaselineHash = '5C5DDB9F746E7A7B0803E542F077611B54A48D7D70CD2FCBEC31347BAF44CBA0'
$root = Split-Path -Parent $PSScriptRoot
$bad = New-Object System.Collections.ArrayList
function Fail($why) { [void]$bad.Add($why); Write-Host "FAIL  $why" }

Push-Location $root
try {
    # Uncommitted files count too: this compares the working folder with the baseline.
    $engine = 'app/src/main/java/com/zeroflux/cprcoach/engine'
    $protected = 'PractiseSession','LiveAnalyzer','RateEstimator','PoseEngine','PostureJudge','ElbowGate',
        'LandmarkGuard','PushDetector','SessionMetrics','OneEuroFilter','LoadGovernor' | ForEach-Object { "$engine/$_.kt" }
    $changed = @(git diff --name-only $Base -- @protected)
    if ($changed.Count) { Fail "protected files changed: $($changed -join ', ')" } else { Write-Host 'ok    protected files are the same as ee682a1' }

    $coach = Get-Content "$engine/CoachPolicy.kt" -Raw
    $header = [regex]::Match($coach, '(?s)class CoachPolicy\((.*?)\n\) \{').Groups[1].Value
    foreach ($default in 'steadyMs: Long = 3_000','postureSteadyMs: Long = 2_000','cooldownMs: Long = 6_000',
        'praiseEveryMs: Long = 20_000','elbowSteadyMs: Long = 1_000') {
        if (-not $header.Contains($default)) { Fail "CoachPolicy default changed: $default" }
    }
    $noDefault = @($header -split "`n" | Where-Object { $_ -match '\bva[lr]\b' -and $_ -notmatch '=' })
    if ($noDefault.Count) { Fail "CoachPolicy parameter without a default: $($noDefault -join ';')" }
    if ($coach -notmatch 'class CoachPolicy') { Fail 'CoachPolicy not found' } else { Write-Host 'ok    CoachPolicy defaults' }

    # Only Emergency tests may change. Mixed tests may change if EMERGENCY_TALK.md names them with a reason.
    $emergencyTests = 'EmergencyCoachTest','EmergencySequenceTest','EmergencyTimelineTest','EmergencyWordReplayTest'
    $mixedTests = 'PauseVoiceReplayTest','CoachReplayTest'
    $talk = if (Test-Path docs/EMERGENCY_TALK.md) { Get-Content docs/EMERGENCY_TALK.md -Raw } else { '' }
    $tests = git diff --name-status $Base -- app/src/test tools/testdata/rate_golden | Where-Object { $_ -notmatch '^A' }
    $editedPractise = @()
    foreach ($row in $tests) {
        $name = [IO.Path]::GetFileNameWithoutExtension(($row -split "`t")[-1])
        if ($emergencyTests -contains $name) { continue }
        if ($mixedTests -contains $name -and $talk.Contains($name)) { Write-Host "note  $name changed; check its Emergency-only reason in EMERGENCY_TALK.md"; continue }
        $editedPractise += $row
    }
    if ($editedPractise.Count) { Fail "tests or locked data edited: $($editedPractise -join ', ')" } else { Write-Host 'ok    no Practise test edited' }
    $recordings = @(git diff --name-only $Base -- tools/testdata/real-cph2467-*.csv)
    if ($recordings.Count) { Fail "recordings changed: $($recordings -join ', ')" }

    $files = @(Get-ChildItem tools/testdata/practise_baseline/*.txt | Sort-Object Name)
    $joined = ($files | ForEach-Object { (Get-FileHash $_.FullName -Algorithm SHA256).Hash }) -join ''
    $sum = [BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::ASCII.GetBytes($joined))).Replace('-', '')
    if ($files.Count -ne 7 -or $sum -ne $BaselineHash) { Fail 'the replay baseline files were changed' } else { Write-Host 'ok    replay baseline files untouched' }

    foreach ($name in 'CPR_WRITE_BASELINE','CPR_WRITE_GOLDEN','CPR_WRITE_SAMPLES','CPR_TRACE') { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
    if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }
    if (-not (Test-Path local.properties)) { "sdk.dir=$($env:ANDROID_HOME -replace '\\','/')" | Set-Content local.properties }
    $ErrorActionPreference = 'Continue'
    .\gradlew.bat cleanTestDebugUnitTest testDebugUnitTest --continue -q *>&1 | Select-Object -Last 12 | ForEach-Object { $_.ToString() }
    $ErrorActionPreference = 'Stop'
    $xml = @(Get-ChildItem app/build/test-results/testDebugUnitTest/*.xml | ForEach-Object { [xml](Get-Content $_.FullName) })
    $total = ($xml.testsuite.tests | Measure-Object -Sum).Sum
    $failed = ($xml.testsuite.failures | Measure-Object -Sum).Sum + ($xml.testsuite.errors | Measure-Object -Sum).Sum
    $skipped = ($xml.testsuite.skipped | Measure-Object -Sum).Sum
    Write-Host "tests $total, failed $failed, skipped $skipped"
    if ($failed -gt 0) {
        $xml | ForEach-Object { $_.testsuite.testcase } | Where-Object { $_.failure } | Select-Object -First 8 |
            ForEach-Object { Fail "$($_.classname) $($_.name)" }
    }
    $replay = @($xml | ForEach-Object { $_.testsuite.testcase } | Where-Object { $_.classname -like '*PractiseBaselineTest' })
    $replayOk = @($replay | Where-Object { -not $_.failure -and -not $_.skipped })
    if ($replayOk.Count -ne 7) { Fail "Practise replay: only $($replayOk.Count) of 7 recordings identical" } else { Write-Host 'ok    Practise replay of recordings 10-16 is byte identical to ee682a1' }
} finally {
    Pop-Location
}

if ($bad.Count) { Write-Host "GATE FAILED: $($bad.Count) problem(s)"; exit 1 }
Write-Host 'GATE PASSED: Practise is unchanged'
exit 0