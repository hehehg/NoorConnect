import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.noorconnect.core.tdlib"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        // Read from local.properties (never committed) so real credentials never touch git.
        val localProperties = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) load(file.inputStream())
        }

        buildConfigField(
            "String",
            "TELEGRAM_API_ID",
            "\"${localProperties.getProperty("TELEGRAM_API_ID", "0")}\"",
        )

        buildConfigField(
            "String",
            "TELEGRAM_API_HASH",
            "\"${localProperties.getProperty("TELEGRAM_API_HASH", "")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":domain"))

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // TDLib for Android — verified current as of this setup:
    // The old prebuilt-AAR mirrors (e.g. jitpack "com.github.tdlib:td") stopped being updated
    // in 2024, so treat them as a quick-test-only option, not something to ship. Build it
    // yourself instead — Android has its own dedicated build scripts (not the generic CMake
    // flow other platforms use):
    //   https://github.com/tdlib/td/tree/master/example/android
    //   Run in order: ./check-environment.sh, ./fetch-sdk.sh, ./build-openssl.sh, ./build-tdlib.sh
    //   Or officially via Docker from that same directory: docker build --output tdlib .
    // Either path produces (under tdlib/): libs/<abi>/libtdjni.so and java/org/drinkless/tdlib/*.java
    // See README.md "خطوات إضافة TDLib نفسها" for exactly where each goes.
    // Only THIS module ever imports org.drinkless.tdlib.* — see TdLibManager.kt.
}