param(
    [switch]$VerifyOnly
)

$ErrorActionPreference = "Stop"

$jdkHome = $env:JAVA_HOME
if (-not $jdkHome -or -not (Test-Path (Join-Path $jdkHome "bin\java.exe"))) {
    throw "JAVA_HOME does not point to a JDK installation."
}

$jdkHome = (Resolve-Path $jdkHome).Path
$javaExe = Join-Path $jdkHome "bin\java.exe"
$javacExe = Join-Path $jdkHome "bin\javac.exe"
$jpackageExe = Join-Path $jdkHome "bin\jpackage.exe"
foreach ($executable in @($javaExe, $javacExe, $jpackageExe)) {
    if (-not (Test-Path $executable)) {
        throw "Required JDK 21 executable was not found: $executable"
    }
}

$javaVersion = (& $javaExe -version 2>&1 | Out-String).Trim()
$javacVersion = (& $javacExe -version 2>&1 | Out-String).Trim()
$jpackageVersion = (& $jpackageExe --version 2>&1 | Out-String).Trim()
Write-Output "java -version:`n$javaVersion"
Write-Output "javac -version:`n$javacVersion"
Write-Output "echo %JAVA_HOME%:"
cmd.exe /d /c "echo %JAVA_HOME%"
Write-Output "jpackage --version: $jpackageVersion"

if ($javaVersion -notmatch 'version "21(?:\.|"|\+)') {
    throw "Expected java 21 under JAVA_HOME, got: $javaVersion"
}
if ($javacVersion -notmatch '^javac 21(?:\.|$)') {
    throw "Expected javac 21 under JAVA_HOME, got: $javacVersion"
}
if ($jpackageVersion -notmatch '^21(?:\.|$)') {
    throw "Expected jpackage 21 under JAVA_HOME, got: $jpackageVersion"
}

$resolvedJava = (Get-Command java -ErrorAction Stop).Source
if (-not [string]::Equals($resolvedJava, $javaExe, [StringComparison]::OrdinalIgnoreCase)) {
    throw "PATH resolves java to '$resolvedJava' instead of JAVA_HOME '$jdkHome'."
}

$architecture = (& $javaExe -XshowSettings:properties -version 2>&1 | Out-String)
if ($architecture -notmatch 'os\.arch\s*=\s*(amd64|x86_64)') {
    throw "Windows packaging requires a Windows x64 JDK; detected architecture output: $architecture"
}

if (-not $VerifyOnly) {
    if (-not $env:GITHUB_ENV -or -not $env:GITHUB_PATH) {
        throw "GITHUB_ENV and GITHUB_PATH are required to pin JDK 21 for later workflow steps."
    }
    Add-Content -Path $env:GITHUB_ENV -Value "JAVA_HOME=$jdkHome"
    Add-Content -Path $env:GITHUB_PATH -Value (Join-Path $jdkHome "bin")
}