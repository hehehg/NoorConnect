plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.noorconnect.data"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:tdlib"))
    implementation(project(":domain"))
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.play.services)
    implementation(libs.datastore.preferences)
    // :domain declared this as `implementation`, which doesn't propagate here — :data uses
    // Json.encodeToString/decodeFromString directly (FolderRepositoryImpl), so it needs both
    // the runtime library AND the compiler plugin (applied above) itself, not just domain's.
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
