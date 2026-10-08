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

Use Windows with JDK 21 and WiX Toolset 3.14.1 installed. From the repository root in PowerShell:

```powershell
.\gradlew.bat -p desktop clean test --no-daemon --console=plain -PappVersion=1.0.0
.\gradlew.bat -p desktop packageReleaseExe --no-daemon --console=plain -PappVersion=1.0.0
.\gradlew.bat -p desktop packageReleaseDistributionForCurrentOS --no-daemon --console=plain -PappVersion=1.0.0
```

`packageReleaseExe` creates the Windows setup executable. `packageReleaseDistributionForCurrentOS` creates a portable app image containing the launcher and bundled Java runtime. GitHub Actions stages these as `NoorConnect-Setup-1.0.0.exe` and a versioned portable launcher with its complete runtime directory.

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

- Windows 10
- Windows 11

## Bluetooth and hardware note

The current repository does not contain a functional Bluetooth smart-mat implementation in the Android codebase. The desktop project therefore includes a hardware abstraction layer and settings screen that model the same moderation and desktop orchestration patterns, but Bluetooth-specific hardware integration is not available in this repository as a real device implementation.

## Known limitations

- No real Android TDLib mass migration was performed because this repo is a Telegram client and the actual mobile backend is Android-only.
- The desktop implementation is a production-oriented desktop port of the current app architecture rather than a full hardware reproduction of any missing device-specific features.
- Real Bluetooth smart mat integration would require a device-specific protocol implementation and Windows hardware drivers not present in the repo.

## Troubleshooting

- If Gradle reports a Java version issue, install JDK 21 and ensure `java` and `jpackage` resolve from that JDK.
- If the installer task cannot find WiX, install WiX Toolset 3.14.1 and ensure its `bin` directory is on `PATH`.
- If packaging is missing, ensure the Compose plugin and the `desktop` project are configured correctly.
- If tests fail, run `./gradlew -p desktop test --console=plain` and inspect the failing report.

## Adding new features

Keep the desktop module isolated from Android code, and always add new platform logic behind small interfaces and UI components. Prefer preserving the business-logic patterns already present in the repository instead of duplicating app logic across platforms.
