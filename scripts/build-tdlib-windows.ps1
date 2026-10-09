$ErrorActionPreference = "Stop"

$tdCommit = "d1085f9cebc5a62379991ae1652673954f229c1f"
$vcpkgCommit = "c3867e714dd3a51c272826eea77267876517ed99"
$workRoot = Join-Path $env:RUNNER_TEMP "noorconnect-tdlib"
$tdSource = Join-Path $workRoot "td"
$vcpkgRoot = Join-Path $workRoot "vcpkg"
$javaExample = Join-Path $tdSource "example\java"
$tdInstall = Join-Path $javaExample "td"
$tdBuild = Join-Path $tdSource "jnibuild"
$jniBuild = Join-Path $javaExample "build"
$nativeResources = Join-Path $env:GITHUB_WORKSPACE "desktop\src\main\resources\native"
$tdApiSource = Join-Path $env:GITHUB_WORKSPACE "core\tdlib\src\main\java\org\drinkless\tdlib\TdApi.java"

if (-not (Select-String -Path $tdApiSource -SimpleMatch $tdCommit -Quiet)) {
    throw "The TDLib source pin does not match the commit recorded in TdApi.java. Update tdCommit before building JNI."
}

New-Item $workRoot -ItemType Directory -Force | Out-Null
git clone https://github.com/tdlib/td.git $tdSource
if ($LASTEXITCODE -ne 0) { throw "Failed to clone TDLib source." }
git -C $tdSource checkout --detach $tdCommit
if ($LASTEXITCODE -ne 0) { throw "Failed to select the TDLib revision matching TdApi.java." }

git clone https://github.com/microsoft/vcpkg.git $vcpkgRoot
if ($LASTEXITCODE -ne 0) { throw "Failed to clone vcpkg." }
git -C $vcpkgRoot checkout --detach $vcpkgCommit
if ($LASTEXITCODE -ne 0) { throw "Failed to select the pinned vcpkg revision." }
& (Join-Path $vcpkgRoot "bootstrap-vcpkg.bat") -disableMetrics
if ($LASTEXITCODE -ne 0) { throw "Failed to bootstrap vcpkg." }

$env:VCPKG_DEFAULT_TRIPLET = "x64-windows"
& (Join-Path $vcpkgRoot "vcpkg.exe") install "gperf:x64-windows" "openssl:x64-windows" "zlib:x64-windows"
if ($LASTEXITCODE -ne 0) { throw "Failed to install TDLib's Windows x64 native dependencies." }

cmake -S $tdSource -B $tdBuild -A x64 `
    "-DCMAKE_INSTALL_PREFIX:PATH=$tdInstall" `
    "-DTD_ENABLE_JNI=ON" `
    "-DCMAKE_TOOLCHAIN_FILE:FILEPATH=$(Join-Path $vcpkgRoot 'scripts\buildsystems\vcpkg.cmake')"
if ($LASTEXITCODE -ne 0) { throw "Failed to configure TDLib JNI for Windows x64." }
cmake --build $tdBuild --config Release --target install --parallel 2
if ($LASTEXITCODE -ne 0) { throw "Failed to build and install TDLib for Windows x64." }

cmake -S $javaExample -B $jniBuild -A x64 `
    "-DTd_DIR=$(Join-Path $tdInstall 'lib\cmake\Td')" `
    "-DCMAKE_INSTALL_PREFIX:PATH=$javaExample"
if ($LASTEXITCODE -ne 0) { throw "Failed to configure the TDLib Java JNI wrapper." }
cmake --build $jniBuild --config Release --target install --parallel 2
if ($LASTEXITCODE -ne 0) { throw "Failed to build the TDLib Java JNI wrapper." }

$nativeOutput = Join-Path $javaExample "bin"
$jniLibrary = Join-Path $nativeOutput "tdjni.dll"
if (-not (Test-Path $jniLibrary)) {
    throw "TDLib build did not produce $jniLibrary."
}

New-Item $nativeResources -ItemType Directory -Force | Out-Null
$vcpkgRuntime = Join-Path $vcpkgRoot "installed\x64-windows\bin"
$vcpkgDlls = @(Get-ChildItem $vcpkgRuntime -File -Filter "*.dll" -ErrorAction SilentlyContinue)
if ($vcpkgDlls.Count -gt 0) {
    Copy-Item $vcpkgDlls.FullName $nativeOutput -Force
}
$nativeDlls = @(Get-ChildItem $nativeOutput -File -Filter "*.dll")
if ($nativeDlls.Count -eq 0) {
    throw "TDLib build produced no native dependency DLLs in $nativeOutput."
}
Copy-Item $nativeDlls.FullName $nativeResources -Force
Copy-Item (Join-Path $tdSource "LICENSE_1_0.txt") (Join-Path $nativeResources "TDLib-LICENSE.txt") -Force

Write-Output "Built TDLib JNI for Windows x64 from $tdCommit."
Write-Output "Staged $($nativeDlls.Count) native DLL files in $nativeResources."