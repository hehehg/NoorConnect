plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}
android {
    namespace = "com.noorconnect.domain"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    // Pure Kotlin module on purpose — no Android framework, no Hilt, no TDLib, no UI.
    // This is what keeps it testable and reusable if you ever ship a desktop/companion app.
    // javax.inject alone (not Hilt) gives the use cases @Inject constructors — Hilt just
    // needs that standard annotation present to wire them up from :app, without domain
    // depending on Hilt itself.
    implementation(project(":core:common"))
    implementation(libs.coroutines.core)
    implementation(libs.javax.inject)
    // Pure Kotlin, no platform dependency — ChatFolder needs @Serializable so :data can
    // persist it as JSON without domain knowing anything about DataStore or storage at all.
    implementation(libs.kotlinx.serialization.json)
}
