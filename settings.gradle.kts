pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Kept as a fallback for quick local testing only — the prebuilt jitpack TDLib AAR
        // stopped updating in 2024. Build TDLib yourself instead (see README.md); once you do,
        // libtdjni.so + TdApi.java/Client.java live inside core/tdlib, not fetched from here.
        maven("https://jitpack.io")
    }
}

rootProject.name = "NoorConnect"

// ---- Every new capability becomes its own module, never a new package inside app/ ----
include(":app")
include(":core:common")
include(":core:tdlib")
include(":domain")
include(":data")
include(":feature:auth")
include(":feature:chats")
include(":feature:chat")
include(":feature:settings")
include(":core:designsystem")
include(":feature:onboarding")
include(":feature:moderation")
