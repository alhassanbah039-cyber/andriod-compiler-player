# ==============================================================================
# Offline Android Sandbox: Test Verification Runner (PowerShell)
# ==============================================================================
# Runs the automated test suites across Modules 1, 2, 3, and 4:
# - ModuleValidationAndStorageTest
# - Module3ExecutionPipelineTest
# - SandboxViewModelTest
# ==============================================================================

[CmdletBinding()]
param(
    [switch]$Detailed,
    [string]$TargetSuite = ""
)

$ErrorActionPreference = "Continue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Resolve-Path "$ScriptDir\.."

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " [TEST VERIFICATION] Running Sandbox Test Suites" -ForegroundColor Cyan
Write-Host " Project Directory: $ProjectRoot" -ForegroundColor Gray
Write-Host "==========================================================" -ForegroundColor Cyan

Set-Location $ProjectRoot

# 1. Package Toolchain Assets First
Write-Host "--> Step 1/2: Preparing Toolchain Assets..." -ForegroundColor Yellow
& "$ScriptDir\setup-toolchain-assets.ps1" -SkipDownload

# 2. Construct Gradle Test Command
$GradleCommand = ".\gradlew.bat"
if (-not (Test-Path "$ProjectRoot\gradlew.bat")) {
    $GradleCommand = "gradle"
}

$TestArgs = @("test")
if ($Detailed) {
    $TestArgs += "--info"
}

if ($TargetSuite) {
    $TestArgs += @("--tests", "com.android.sandbox.compiler.$TargetSuite")
}

Write-Host "`n--> Step 2/2: Executing Gradle Test Suites..." -ForegroundColor Yellow
Write-Host "Command: $GradleCommand $($TestArgs -join ' ')" -ForegroundColor Gray

$StartTime = Get-Date

& $GradleCommand @TestArgs
$ExitCode = $LASTEXITCODE

$Duration = [math]::Round(((Get-Date) - $StartTime).TotalSeconds, 2)

Write-Host "`n==========================================================" -ForegroundColor Cyan
if ($ExitCode -eq 0) {
    Write-Host " [SUCCESS] All Test Suites Passed Flawlessly in $Duration seconds!" -ForegroundColor Green
} else {
    Write-Host " [FAILURE] Test execution returned exit code: $ExitCode ($Duration s)" -ForegroundColor Red
}

$ReportPath = "$ProjectRoot\app\build\reports\tests\testDebugUnitTest\index.html"
if (Test-Path $ReportPath) {
    Write-Host " Test Report HTML: file:///$($ReportPath.Replace('\', '/'))" -ForegroundColor Cyan
}

Write-Host "==========================================================" -ForegroundColor Cyan
exit $ExitCode
