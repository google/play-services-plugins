/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.android.build.api.variant.HostTestBuilder
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.oss.licenses)
}

android {
    namespace = "com.google.android.gms.oss.licenses.testapp"
    compileSdk = libs.versions.compileSdk.get().toInt()
    testBuildType = "release"

    defaultConfig {
        applicationId = "com.google.android.gms.oss.licenses.testapp"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    lint {
        abortOnError = true
        checkDependencies = true
        ignoreWarnings = false
    }
}

tasks.withType<Test>().configureEach {
    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })

    // Enable parallel execution for faster Robolectric runs
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
}

androidComponents {
    beforeVariants { variantBuilder ->
        // AGP 9.0 only enables unit tests for the "tested build type" by default.
        // We explicitly enable them for all variants to ensure both Debug and Release coverage.
        variantBuilder.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}

dependencies {
    implementation(libs.play.services.oss.licenses)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation("androidx.activity:activity-compose:${libs.versions.androidx.activity.get()}")
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.material3:material3")


    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.test.espresso.contrib)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)

    // Compose Test (required for testing the V2 activity)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
