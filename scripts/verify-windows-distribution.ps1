param(
    [Parameter(Mandatory = $true)]
    [string]$BinaryRoot,
    [Parameter(Mandatory = $true)]
    [string]$ArtifactRoot,
    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = "Stop"

$packagingResources = Join-Path $PSScriptRoot "..\desktop\src\main\resources"
foreach ($resourceName in @("icon.ico", "icon.png")) {
    $resourcePath = Join-Path $packagingResources $resourceName
    if (-not (Test-Path $resourcePath) -or (Get-Item $resourcePath).Length -eq 0) {
        throw "Required jpackage resource is missing or empty: $resourcePath"
    }
}

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

    $nativeResourceDirectory = Join-Path $appDirectory "resources\native"
    $tdlibJni = Join-Path $nativeResourceDirectory "tdjni.dll"
    if (-not (Test-Path $tdlibJni)) {
        throw "Application image is missing the Windows x64 TDLib JNI library: $tdlibJni"
    }
    $tdlibDependencies = @(Get-ChildItem $nativeResourceDirectory -File -Filter "*.dll" -ErrorAction SilentlyContinue)
    if ($tdlibDependencies.Count -lt 1) {
        throw "Application image is missing TDLib native dependency DLLs under '$nativeResourceDirectory'."
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
    Write-Output "Application JARs, UI libraries, and TDLib JNI are present ($($tdlibDependencies.Count) TDLib DLLs)."
}

function Test-LaunchWithoutSystemJava([string]$Launcher) {
    $originalJavaHome = $env:JAVA_HOME
    $originalJdkHome = $env:JDK_HOME
    $originalPath = $env:PATH
    $process = $null
    try {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        Remove-Item Env:JDK_HOME -ErrorAction SilentlyContinue
        $env:PATH = "$env:SystemRoot\System32;$env:SystemRoot"

        $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = $Launcher
        $startInfo.WorkingDirectory = Split-Path $Launcher -Parent
        $startInfo.UseShellExecute = $false
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $startInfo.ArgumentList.Add("--verbose")

        $process = [System.Diagnostics.Process]::new()
        $process.StartInfo = $startInfo
        if (-not $process.Start()) {
            throw "Failed to start application launcher: $Launcher"
        }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $exited = $process.WaitForExit(15000)
        if (-not $exited) {
            $process.Kill($true)
            $process.WaitForExit()
        }

        $launchOutput = @(
            $stdoutTask.GetAwaiter().GetResult()
            $stderrTask.GetAwaiter().GetResult()
        ) -join [Environment]::NewLine
        if ($launchOutput -match '(?i)VerifyError|Failed to launch JVM|Exception in thread') {
            throw "Application launcher reported a JVM/application startup error:`n$launchOutput"
        }
        if ($exited) {
            throw "Application launcher exited with code $($process.ExitCode) when JAVA_HOME, JDK_HOME, and Java on PATH were unavailable.`n$launchOutput"
        }
        Write-Output "Application launcher remained running without JAVA_HOME, JDK_HOME, or Java on PATH and reported no startup exception."
    } finally {
        if ($process) {
            if (-not $process.HasExited) {
                $process.Kill($true)
            }
            $process.Dispose()
        }
        $env:JAVA_HOME = $originalJavaHome
        $env:JDK_HOME = $originalJdkHome
        $env:PATH = $originalPath
    }
}

function Test-TdlibJni([string]$Root) {
    $bundledJava = Join-Path $Root "runtime\bin\java.exe"
    if (-not (Test-Path $bundledJava)) {
        $bundledJava = Join-Path $env:JAVA_HOME "bin\java.exe"
    }

    $testClasses = Join-Path $env:GITHUB_WORKSPACE "desktop\build\classes\java\test"
    $mainClasses = Join-Path $env:GITHUB_WORKSPACE "desktop\build\classes\java\main"
    $classPath = "$testClasses;$mainClasses"
    $nativePath = "$(Join-Path $Root 'app\resources\native');$(Join-Path $Root 'runtime\bin')"
    $output = & $bundledJava "-Djava.library.path=$nativePath" -cp $classPath com.noorconnect.desktop.TdLibNativeSmoke 2>&1
    if ($LASTEXITCODE -ne 0 -or ($output -join [Environment]::NewLine) -notmatch 'TDLib JNI client created successfully') {
        throw "TDLib JNI smoke test failed for '$Root':`n$($output -join [Environment]::NewLine)"
    }
    Write-Output "TDLib JNI loaded and created a client in: $Root"
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
Test-TdlibJni $appImage
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
Test-TdlibJni $portableDirectory
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
Test-TdlibJni $installedDirectory
Test-LaunchWithoutSystemJava (Join-Path $installedDirectory "$($appConfig.BaseName).exe")

Write-Output "Verified installer: $($installer.FullName)"
Write-Output "Verified installed image: $installedDirectory"
Write-Output "Verified jpackage icon inputs: $packagingResources"
Write-Output "Staged artifacts under: $artifactRoot"
