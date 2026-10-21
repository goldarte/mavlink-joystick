// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Copyright (c) 2026 Eugene Molotkov
// Repository: https://github.com/goldarte/mavlink-joystick

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Hook the application only: shared's pre-build is needed by the tests themselves.
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(":shared:localTests")
}

// Release signing is local by default: keystore.properties and the keystore are
// gitignored. CI can supply the same values through the RELEASE_* variables.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// A variable that is set but blank counts as "not configured": CI systems expose
// missing secrets as empty strings, and an empty storeFile would reach
// rootProject.file("") below and fail the build instead of producing an
// unsigned APK.
fun signingValue(property: String, environment: String): String? =
    (keystoreProperties.getProperty(property) ?: System.getenv(environment))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null &&
    rootProject.file(releaseStoreFile).isFile

kotlin {
    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    dependencies {
        implementation(projects.shared)
        implementation(libs.androidx.activity.compose)
        implementation(libs.androidx.core.ktx)
    }
}

android {
    namespace = "com.goldarte.mavlinkjoystick"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.goldarte.mavlinkjoystick"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Name the APKs after the app rather than after the Gradle module, so release
// downloads read mavlink-joystick-release.apk instead of androidApp-release.apk.
// Setting outputFileName replaces AGP's default name outright, including the
// "-unsigned" suffix it normally appends when no signing config is available,
// so that suffix is reproduced here to keep signed and unsigned builds apart.
androidComponents {
    onVariants { variant ->
        val unsigned = variant.buildType == "release" && !hasReleaseSigning
        val suffix = if (unsigned) "-unsigned" else ""
        variant.outputs.forEach { output ->
            output.outputFileName.set("mavlink-joystick-${variant.buildType}$suffix.apk")
        }
    }
}
