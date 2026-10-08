import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "com.noorconnect"
val appVersion = providers.environmentVariable("APP_VERSION")
    .orElse(providers.gradleProperty("appVersion"))
    .getOrElse("1.0.0")
version = appVersion

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.noorconnect.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "NoorConnect"
            packageVersion = appVersion
            appResourcesRootDir = file("src/main/resources")
            windows {
                exePackageVersion = appVersion
                msiPackageVersion = appVersion
                packageVersion = appVersion
                iconFile.set(project.file("src/main/resources/icon.ico"))
                menu = true
                shortcut = true
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

