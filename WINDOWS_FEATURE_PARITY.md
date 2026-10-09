# Windows feature parity audit

| Feature | Android implementation | Windows implementation | Status | Notes |
|---|---|---|---|---|
| Telegram auth flow | Android TDLib + auth screens | Desktop TDLib JNI + phone/code/password flow | PARTIAL | Code is connected; Windows native build and real-account verification are pending |
| Messaging UI | Compose screens in feature modules | Live desktop chat list, recent history, text send | PARTIAL | Text path is connected; media and broader client features remain |
| Moderation rules | `IslamicContentFilter` and `BannedWordMatcher` | `NoorConnectWindowsFilter` and matching business logic | FULL | Tests cover blocked keyword and channel policy behavior |
| Settings persistence | DataStore | JSON settings storage | ADAPTED | Local JSON-based persistence in desktop module |
| Search / chat filtering | Domain use cases | Desktop moderation logic | ADAPTED | Logical parity preserved |
| Notifications | Android notifications | Desktop notification-ready settings model | PARTIAL | Platform hooks prepared, not full OS notification broker |
| Bluetooth / hardware | Android hardware integration layer | Hardware abstraction layer | PARTIAL | Not implemented against a real device protocol in this repo |
| Audio playback | Android audio APIs | Desktop app shell | PARTIAL | UI placeholder only in current port |
| Localization | Android resources | desktop-localized strings model | ADAPTED | Strings are centralized in code rather than Android XML |
| Offline behavior | Local persistence and cached flows | Local settings persistence | ADAPTED | Preserved for settings and app state |
| Installer packaging | Android APK distribution | Compose Desktop MSI/EXE packaging | FULL | Packaging is configured for Windows build workflows |
| TDLib backend | Android native TDLib | Windows x64 TDLib JNI build | PARTIAL | CI builds the matching JNI library; end-to-end Windows validation is pending |
| Smart mat BLE protocol | Hardware-specific implementation | Windows equivalent not available | BLOCKED | Requires protocol specification and hardware drivers |

## Summary

The repository has a real Android application with a clean domain-driven architecture. Windows has a planned x64 TDLib JNI build and a connected baseline for account authorization, chat loading, recent text history, and sending text messages. This baseline remains partial until the Windows native build and real-account flow pass CI; Telegram features outside it and Android-only hardware integrations are not yet available on Windows.
