param(
    [Parameter(Mandatory = $true)]
    [string]$BinaryRoot,
    [Parameter(Mandatory = $true)]
    [string]$ArtifactRoot,
    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = "Stop"

function Assert-PeExecutable([string]$Path) {
    if (-not (Test-Path $Path)) {
        throw "Expected Windows executable was not found: $Path"
    }
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -lt 2 -or $bytes[0] -ne 0x4D -or $bytes[1] -ne 0x5A) {
        throw "File is not a valid Windows PE executable: $Path"
    }
}

function Assert-AppImage([string]$Root) {
    $configFiles = @(Get-ChildItem (Join-Path $Root "app") -File -Filter "*.cfg" -ErrorAction SilentlyContinue)
    if ($configFiles.Count -ne 1) {
        throw "Expected exactly one launcher .cfg file under '$Root\app'; found $($configFiles.Count)."
    }

    $config = $configFiles[0]
    $launcher = Join-Path $Root "$($config.BaseName).exe"
    Assert-PeExecutable $launcher

    $runtimeFiles = @(
        (Join-Path $Root "runtime\lib\jvm.cfg"),
        (Join-Path $Root "runtime\lib\modules"),
        (Join-Path $Root "runtime\bin\java.dll"),
        (Join-Path $Root "runtime\bin\server\jvm.dll")
    )
    foreach ($runtimeFile in $runtimeFiles) {
        if (-not (Test-Path $runtimeFile)) {
            throw "Bundled Java runtime is incomplete; missing '$runtimeFile'."
        }
    }

    $appDirectory = Join-Path $Root "app"
    $appJars = @(Get-ChildItem $appDirectory -Recurse -File -Filter "*.jar" -ErrorAction SilentlyContinue)
    if ($appJars.Count -eq 0) {
        throw "Application image is missing its application/dependency JARs under '$appDirectory'."
    }

    $nativeAppLibrary = Get-ChildItem $appDirectory -Recurse -File -Filter "*.dll" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $nativeAppLibrary) {
        $jarExe = Join-Path $env:JAVA_HOME "bin\jar.exe"
        foreach ($appJar in $appJars) {
            $jarEntries = & $jarExe tf $appJar.FullName 2>$null
            if ($jarEntries -match '\.dll$') {
                $nativeAppLibrary = $appJar
                break
            }
        }
    }
    if (-not $nativeAppLibrary) {
        throw "Application image is missing packaged Windows native libraries (for example, Compose/Skiko DLL resources)."
    }

    $iconResource = Get-ChildItem $appDirectory -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -in @("icon.ico", "icon.png") } |
        Select-Object -First 1
    if (-not $iconResource) {
        throw "Application image is missing the configured application icon resources."
    }

    $nativeLibraries = @(Get-ChildItem (Join-Path $Root "runtime\bin") -Recurse -File -Filter "*.dll" -ErrorAction SilentlyContinue)
    if ($nativeLibraries.Count -lt 2) {
        throw "Bundled runtime is missing required native JVM libraries under '$Root\runtime\bin'."
    }

    $configText = Get-Content $config.FullName -Raw
    if ($configText -match 'JAVA_HOME|JDK_HOME') {
        throw "Launcher config references a machine-specific Java environment variable: $($config.FullName)"
    }

    Write-Output "Verified application image: $Root"
    Write-Output "Launcher: $launcher"
    Write-Output "Launcher config: $($config.FullName)"
    Write-Output "Bundled runtime: $($Root)\runtime"
    Write-Output "Native JVM libraries: $($nativeLibraries.Count) DLL files"
    Write-Output "Application JARs, native UI libraries, and icon resources are present."
}

function Test-LaunchWithoutSystemJava([string]$Launcher) {
    $originalJavaHome = $env:JAVA_HOME
    $originalJdkHome = $env:JDK_HOME
    $originalPath = $env:PATH
    try {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        Remove-Item Env:JDK_HOME -ErrorAction SilentlyContinue
        $env:PATH = "$env:SystemRoot\System32;$env:SystemRoot"

        $process = Start-Process -FilePath $Launcher -PassThru
        if ($process.WaitForExit(15000)) {
            if ($process.ExitCode -ne 0) {
                throw "Application launcher failed with exit code $($process.ExitCode) when JAVA_HOME and JDK_HOME were absent."
            }
            throw "Application launcher exited before its window became available."
        } else {
            $process.Refresh()
            if ($process.MainWindowTitle -ne "NoorConnect") {
                throw "Launcher remained open without showing the NoorConnect window; it may be blocked on a JVM-load error dialog."
            }
            Stop-Process -Id $process.Id -Force
        }
        Write-Output "Launcher started without JAVA_HOME, JDK_HOME, or Java on PATH."
    } finally {
        $env:JAVA_HOME = $originalJavaHome
        $env:JDK_HOME = $originalJdkHome
        $env:PATH = $originalPath
    }
}

$binaryRoot = (Resolve-Path $BinaryRoot).Path
$artifactRoot = [System.IO.Path]::GetFullPath($ArtifactRoot)
$installerName = "NoorConnect-$Version.exe"
$installer = Get-ChildItem $binaryRoot -Recurse -File -Filter $installerName |
    Where-Object { $_.Directory.Name -eq "exe" } |
    Select-Object -First 1
if (-not $installer -or $installer.Length -le 0) {
    throw "Expected jpackage Windows installer '$installerName' was not generated under '$binaryRoot'."
}
Assert-PeExecutable $installer.FullName

$appConfig = Get-ChildItem $binaryRoot -Recurse -File -Filter "*.cfg" |
    Where-Object { $_.Directory.Name -eq "app" } |
    Select-Object -First 1
if (-not $appConfig) {
    throw "No jpackage application image with a launcher .cfg was generated under '$binaryRoot'."
}
$appImage = $appConfig.Directory.Parent.FullName
Assert-AppImage $appImage
Test-LaunchWithoutSystemJava (Join-Path $appImage "$($appConfig.BaseName).exe")

if (Test-Path $artifactRoot) {
    Remove-Item $artifactRoot -Recurse -Force
}
$installerDirectory = Join-Path $artifactRoot "installer"
$portableDirectory = Join-Path $artifactRoot "portable\NoorConnect-$Version"
New-Item $installerDirectory -ItemType Directory -Force | Out-Null
New-Item $portableDirectory -ItemType Directory -Force | Out-Null
Copy-Item $installer.FullName (Join-Path $installerDirectory "NoorConnect-Setup-$Version.exe")
Copy-Item (Join-Path $appImage "*") $portableDirectory -Recurse -Force

Assert-AppImage $portableDirectory
Test-LaunchWithoutSystemJava (Join-Path $portableDirectory "$($appConfig.BaseName).exe")

$installedDirectory = Join-Path $env:ProgramFiles "NoorConnect"
$installerProcess = Start-Process -FilePath $installer.FullName -ArgumentList @("/quiet", "/norestart") -Wait -PassThru
if ($installerProcess.ExitCode -notin @(0, 3010)) {
    throw "jpackage installer failed with exit code $($installerProcess.ExitCode)."
}
if (-not (Test-Path $installedDirectory)) {
    throw "Installer completed but did not install the application at '$installedDirectory'."
}
Assert-AppImage $installedDirectory
Test-LaunchWithoutSystemJava (Join-Path $installedDirectory "$($appConfig.BaseName).exe")

Write-Output "Verified installer: $($installer.FullName)"
Write-Output "Verified installed image: $installedDirectory"
Write-Output "Staged artifacts under: $artifactRoot"