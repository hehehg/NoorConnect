# Windows desktop build guide

## Architecture

This repository keeps the Android project intact and adds a separate Windows desktop module under `desktop/` that is isolated from the Android Gradle build. The desktop layer is built with Kotlin + JetBrains Compose for Desktop and targets a native Windows application experience without modifying the original Android source layout.

## Technology selection

- Kotlin JVM for core logic and UI code
- JetBrains Compose for Desktop for the native Windows UI shell
- Kotlin serialization for settings persistence
- JDK 21 for the toolchain
- GitHub Actions on `windows-2022` for CI packaging

This approach is the safest fit for the current repo because the actual codebase is primarily an Android Kotlin application with domain-driven architecture. The desktop layer reuses the same product model ideas while preserving the Android app as the source of truth for the original app.

## Windows build

Use Windows x64 with JDK 21, Visual Studio C++ build tools, CMake, Git, and WiX Toolset 3.14.1 installed. From the repository root in PowerShell:

```powershell
./scripts/build-tdlib-windows.ps1
.\gradlew.bat -p desktop clean test --no-daemon --console=plain -PappVersion=1.0.0
.\gradlew.bat -p desktop createDistributable --no-daemon --console=plain -PappVersion=1.0.0
.\gradlew.bat -p desktop packageExe --no-daemon --console=plain -PappVersion=1.0.0
```

The native build script pins TDLib to the revision matching the checked-in Java API, builds `tdjni.dll` for x64 with vcpkg OpenSSL/zlib dependencies, and stages the DLLs in desktop resources. Windows CI installs 64-bit Eclipse Temurin JDK 21 and runs this build before packaging. Standard Compose packaging tasks avoid release ProGuard transformations that previously invalidated coroutine bytecode. jpackage creates an application image with a linked Java runtime; the setup executable installs that complete image, and the portable artifact contains the same launcher, `.cfg`, application resources, native libraries, and `runtime/` directory. CI verifies those files and starts the app with Java removed from `PATH` before publishing `NoorConnect-Windows-Installer` and `NoorConnect-Windows-Portable`.

## Telegram login

On first launch, enter the Telegram `api_id` and `api_hash` from [my.telegram.org/apps](https://my.telegram.org/apps), then complete phone-number, code, and optional two-step-password verification. The API credentials are stored in the current Windows user's preferences; the account session database is stored under `%USERPROFILE%\.noorconnect\telegram`. Never commit personal account credentials. The initial Windows integration supports authentication, loading chats and recent text history, and sending text messages; media, calls, secret chats, and other Telegram client features are not yet implemented on desktop.

Gradle package outputs are under `desktop/build/compose/binaries/`; CI uploads only the staged final artifacts, not that build directory.

## Installer and packaging

The desktop build config creates a Windows EXE and MSI package. The packaging metadata is defined in `desktop/build.gradle.kts`.

## GitHub Actions

The repository includes:

- `.github/workflows/windows-build.yml`
- `.github/workflows/windows-release.yml`

The build workflow targets the `Desktop-Windows-Verison` branch and runs on `windows-2022`. It configures JDK 21, installs WiX Toolset 3.14.1, runs tests, builds both Windows outputs, verifies the PE signatures and bundled runtime, then uploads separate installer and portable artifacts:

- `NoorConnect-Windows-Installer`
- `NoorConnect-Windows-Portable`

## Supported Windows versions

- Windows 10 version 1809 or later
- Windows 11

## Bluetooth and hardware note

The current repository does not contain a functional Bluetooth smart-mat implementation in the Android codebase. The desktop project therefore includes a hardware abstraction layer and settings screen that model the same moderation and desktop orchestration patterns, but Bluetooth-specific hardware integration is not available in this repository as a real device implementation.

## Known limitations

- No real Android TDLib mass migration was performed because this repo is a Telegram client and the actual mobile backend is Android-only.
- The desktop implementation is a production-oriented desktop port of the current app architecture rather than a full hardware reproduction of any missing device-specific features.
- Real Bluetooth smart mat integration would require a device-specific protocol implementation and Windows hardware drivers not present in the repo.

## Troubleshooting

- If Gradle reports a Java version issue, use 64-bit JDK 21. CI pins `JAVA_HOME`, verifies `java`, `javac`, and `jpackage`, and fails if another JDK version is selected.
- The packaged application includes its own Java runtime; end users do not need Java or a JDK installed separately.
- If the installer task cannot find WiX, install WiX Toolset 3.14.1 and ensure its `bin` directory is on `PATH`.
- If packaging is missing, ensure the Compose plugin and the `desktop` project are configured correctly.
- If tests fail, run `./gradlew -p desktop test --console=plain` and inspect the failing report.

## Adding new features

Keep the desktop module isolated from Android code, and always add new platform logic behind small interfaces and UI components. Prefer preserving the business-logic patterns already present in the repository instead of duplicating app logic across platforms.
