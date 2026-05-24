import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
}

compose {
    resources {
        packageOfResClass = "com.eugenehammer.mavlinkjoystikkmp.shared"
        generateResClass = auto
    }
}



kotlin {
    android {
        namespace = "com.eugenehammer.mavlinkjoystikkmp.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "MavlinkJoystick"
            isStatic = true
        }
    }

    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(libs.android.mavlink)
            }
        }

        val commonMain by getting {
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.uiToolingPreview)
                implementation(libs.compose.material.icons)

                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)

                implementation(libs.koin.core)
                implementation(libs.koin.compose.viewmodel)

                implementation(libs.navigation3.ui)
                implementation(libs.navigation3.lifecycle.viewmodel)

                implementation(libs.kotlin.serialization.core)

                implementation(libs.datastore)
                implementation(libs.datastore.preferences)
            }
        }
    }
}

