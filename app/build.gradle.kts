plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services") // must stay last — Google's own recommendation
}
android {
    namespace = "com.noorconnect.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.noorconnect.app" // change once you've picked a final name
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Package only arm64-v8a for now — that's what virtually every real phone (including
        // the one you tested on) actually uses. libtdjni.so is large per-ABI; packaging all
        // three (arm64-v8a + armeabi-v7a + x86_64) into one APK roughly triples native-lib size
        // for zero benefit on a single test device. Add armeabi-v7a back only if you need to
        // support older 32-bit devices, and x86_64 only if you test on an x86_64 emulator.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            // Shrinks + obfuscates your Kotlin/Java code and drops unused resources. Doesn't
            // touch libtdjni.so at all (see README.md's size-reduction note for that — it's a
            // separate, bigger win: an unstripped native build can easily be 5-10x the size of
            // a properly stripped one).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // TEMPORARY: signs the release build with the debug key so you can build and
            // measure the real size reduction locally right now. Replace with a real release
            // signingConfig before this ever reaches a user's device outside your own testing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
dependencies {
    // app depends on every feature module — feature modules never depend on app or each other.
    // That one rule is what lets you add a new feature module later without touching old ones.
    implementation(project(":core:common"))
    implementation(project(":core:tdlib"))
    implementation(project(":core:designsystem"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:chats"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:moderation"))
    implementation(project(":feature:settings"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)
    implementation(libs.core.splashscreen)
    // BOM must be imported here too, not just in :data — Gradle only applies a platform's
    // version constraints within the module that declares it as `implementation`. :data's own
    // BOM import doesn't propagate to :app's classpath resolution, even though the actual
    // firebase-firestore-ktx artifact DOES flow through transitively from :data. Without this,
    // that artifact resolves with no version at all and the build fails.
    implementation(platform(libs.firebase.bom))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
