<#
.SYNOPSIS
    Launches the Android emulator(s) used to test ArchivoSync.

.DESCRIPTION
    Boots the `api34_test` AVD (the one this project is normally tested on).
    With -Two it boots a SECOND emulator as well, so the P2P flow can be
    exercised device-to-device (SEED on one, LEECH on the other).

    Two instances of the *same* AVD can only run concurrently if each is
    started with -read-only (the AVD's disk is otherwise locked by the first
    instance). This script handles that automatically. Each emulator gets an
    explicit console port so the ADB serials are stable:
        first  -> emulator-5554
        second -> emulator-5556

.PARAMETER Avd
    AVD to boot. Default: api34_test.

.PARAMETER Second
    AVD for the second emulator (only with -Two). Defaults to -Avd, i.e. the
    same image booted a second time in read-only mode.

.PARAMETER Two
    Also boot a second emulator (for two-device / P2P testing).

.PARAMETER Headless
    Boot without a window (-no-window). Handy for CI / background runs.

.PARAMETER Wait
    Block until each emulator finishes booting (sys.boot_completed=1).

.PARAMETER ColdBoot
    Force a cold boot (-no-snapshot-load) instead of restoring a snapshot.

.EXAMPLE
    .\scripts\run-emulators.ps1
    # Boots api34_test (emulator-5554).

.EXAMPLE
    .\scripts\run-emulators.ps1 -Two -Wait
    # Boots two api34_test instances (5554 + 5556, read-only) and waits.

.EXAMPLE
    .\scripts\run-emulators.ps1 -Avd api34_test -Second Pixel_8 -Two
    # Boots two DIFFERENT AVDs.
#>
[CmdletBinding()]
param(
    [string]$Avd = 'api34_test',
    [string]$Second,
    [switch]$Two,
    [switch]$Headless,
    [switch]$Wait,
    [switch]$ColdBoot
)

$ErrorActionPreference = 'Stop'

# --- Locate the Android SDK -------------------------------------------------
function Resolve-Sdk {
    # 1) local.properties (sdk.dir), 2) ANDROID_HOME/ANDROID_SDK_ROOT, 3) default.
    $localProps = Join-Path $PSScriptRoot '..\local.properties'
    if (Test-Path $localProps) {
        $line = Get-Content $localProps | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($line) {
            $raw = ($line -replace '^\s*sdk\.dir\s*=', '').Trim()
            # Un-escape Java .properties escaping: \\ -> \, \: -> :, \= -> =
            $dir = $raw -replace '\\\\', '\' -replace '\\:', ':' -replace '\\=', '='
            if (Test-Path $dir) { return $dir }
        }
    }
    foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }
    $default = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $default) { return $default }
    throw "Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties."
}

$sdk = Resolve-Sdk
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path $emulator)) { throw "emulator.exe not found at $emulator" }
if (-not (Test-Path $adb)) { throw "adb.exe not found at $adb" }

# --- Validate AVDs exist ----------------------------------------------------
$avds = & $emulator -list-avds
function Assert-Avd([string]$name) {
    if ($avds -notcontains $name) {
        throw "AVD '$name' not found. Available: $($avds -join ', ')"
    }
}
Assert-Avd $Avd
if ($Two) {
    if (-not $Second) { $Second = $Avd }
    Assert-Avd $Second
}

# --- Boot one emulator ------------------------------------------------------
function Start-Emu {
    param([string]$Name, [int]$Port, [bool]$ReadOnly)

    $emuArgs = @('-avd', $Name, '-port', $Port)
    if ($ReadOnly) { $emuArgs += '-read-only' }   # required to run same AVD twice
    if ($Headless) { $emuArgs += @('-no-window', '-no-audio') }
    if ($ColdBoot) { $emuArgs += '-no-snapshot-load' }
    # Reasonable defaults for headless/CI stability:
    $emuArgs += @('-no-boot-anim', '-gpu', 'swiftshader_indirect')

    Write-Host "Starting '$Name' on port $Port (serial emulator-$Port)$(if($ReadOnly){' [read-only]'})..." -ForegroundColor Cyan
    Start-Process -FilePath $emulator -ArgumentList $emuArgs -WindowStyle Minimized | Out-Null
}

function Wait-Boot([int]$Port, [int]$TimeoutSec = 180) {
    $serial = "emulator-$Port"
    Write-Host "Waiting for $serial to boot..." -NoNewline
    & $adb -s $serial wait-for-device
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $booted = (& $adb -s $serial shell getprop sys.boot_completed 2>$null).Trim()
        if ($booted -eq '1') { Write-Host " ready." -ForegroundColor Green; return }
        Start-Sleep -Seconds 2
        Write-Host '.' -NoNewline
    }
    Write-Host ''
    Write-Warning "$serial did not report boot_completed within $TimeoutSec s."
}

# --- Run --------------------------------------------------------------------
# When booting the SAME AVD twice, the first may keep the disk lock, so run
# both read-only for symmetry; distinct AVDs boot normally (writable).
$sameAvd = $Two -and ($Second -eq $Avd)

Start-Emu -Name $Avd -Port 5554 -ReadOnly:$sameAvd
if ($Two) {
    Start-Sleep -Seconds 2
    Start-Emu -Name $Second -Port 5556 -ReadOnly:$sameAvd
}

if ($Wait) {
    Wait-Boot -Port 5554
    if ($Two) { Wait-Boot -Port 5556 }
}

Write-Host ''
Write-Host 'Emulator(s) launching. Check with:' -ForegroundColor Yellow
Write-Host "  & '$adb' devices"
