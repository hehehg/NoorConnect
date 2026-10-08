# Windows feature parity audit

| Feature | Android implementation | Windows implementation | Status | Notes |
|---|---|---|---|---|
| Telegram auth flow | Android TDLib + auth screens | Desktop shell + auth flow model | ADAPTED | Conceptually preserved, isolated from Android-specific runtime |
| Messaging UI | Compose screens in feature modules | Desktop Compose shell | ADAPTED | Native desktop layout used |
| Moderation rules | `IslamicContentFilter` and `BannedWordMatcher` | `NoorConnectWindowsFilter` and matching business logic | FULL | Tests cover blocked keyword and channel policy behavior |
| Settings persistence | DataStore | JSON settings storage | ADAPTED | Local JSON-based persistence in desktop module |
| Search / chat filtering | Domain use cases | Desktop moderation logic | ADAPTED | Logical parity preserved |
| Notifications | Android notifications | Desktop notification-ready settings model | PARTIAL | Platform hooks prepared, not full OS notification broker |
| Bluetooth / hardware | Android hardware integration layer | Hardware abstraction layer | PARTIAL | Not implemented against a real device protocol in this repo |
| Audio playback | Android audio APIs | Desktop app shell | PARTIAL | UI placeholder only in current port |
| Localization | Android resources | desktop-localized strings model | ADAPTED | Strings are centralized in code rather than Android XML |
| Offline behavior | Local persistence and cached flows | Local settings persistence | ADAPTED | Preserved for settings and app state |
| Installer packaging | Android APK distribution | Compose Desktop MSI/EXE packaging | FULL | Packaging is configured for Windows build workflows |
| Android-only TDLib backend | Native TDLib integration | Not present in Windows build | NOT_APPLICABLE | Android-specific runtime is preserved separately |
| Smart mat BLE protocol | Hardware-specific implementation | Windows equivalent not available | BLOCKED | Requires protocol specification and hardware drivers |

## Summary

The repository already has a real Android application with a clean domain-driven architecture. The Windows desktop version preserves the product’s moderation-first logic and deployability while avoiding unsafe attempts to recreate Android-only hardware and TDLib hooks directly on Windows.
