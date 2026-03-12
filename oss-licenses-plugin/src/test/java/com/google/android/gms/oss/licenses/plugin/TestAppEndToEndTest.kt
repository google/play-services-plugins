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

package com.google.android.gms.oss.licenses.plugin

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Robust E2E test that executes the standalone testapp.
 */
abstract class TestAppEndToEndTest(private val agpVersion: String, private val gradleVersion: String) {

    @get:Rule
    val tempDirectory: TemporaryFolder = TemporaryFolder()

    private lateinit var projectDir: File
    private lateinit var testAppSourceDir: File

    @Before
    fun setup() {
        projectDir = tempDirectory.newFolder("testapp")

        // Find the source testapp directory
        val currentDir = File(System.getProperty("user.dir"))
        testAppSourceDir = File(currentDir, "testapp")

        if (!testAppSourceDir.exists()) {
            throw IllegalStateException("Test app source not found at: ${testAppSourceDir.absolutePath}")
        }

        // Point the testapp at the Android SDK. Prefer ANDROID_HOME, fall back to the plugin
        // project's local.properties (which IntelliJ/Android Studio generates automatically).
        val sdkDir = System.getenv("ANDROID_HOME")
            ?: File(currentDir, "local.properties").takeIf { it.exists() }
                ?.readLines()?.firstOrNull { it.startsWith("sdk.dir=") }
                ?.substringAfter("sdk.dir=")
            ?: throw IllegalStateException("Cannot find Android SDK: set ANDROID_HOME or create local.properties")
        File(projectDir, "local.properties").writeText("sdk.dir=${sdkDir.replace("\\", "\\\\")}\n")

        // Copy testapp to the temporary directory using an allow-list to ensure a clean build environment
        val allowList = listOf("app", "gradle", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat")
        testAppSourceDir.listFiles()?.filter { it.name in allowList }?.forEach { source ->
            if (source.isDirectory) {
                // Copy directories but skip internal build/cache dirs
                source.walk().onEnter { file ->
                    file.name != "build" && file.name != ".gradle" && file.name != ".idea"
                }.filter { it.isFile }.forEach { file ->
                    val relativePath = file.relativeTo(testAppSourceDir).path
                    val target = projectDir.resolve(relativePath)
                    target.parentFile.mkdirs()
                    file.copyTo(target, overwrite = true)
                }
            } else {
                source.copyTo(projectDir.resolve(source.name), overwrite = true)
            }
        }

        // The testapp template uses AGP 9.0+ and modern Kotlin.
        // For older compatible AGP versions, we dynamically override the versions.
        val isModernKotlin = agpVersion.startsWith("9.")

        val tomlFile = File(projectDir, "gradle/libs.versions.toml")
        var tomlContent = tomlFile.readText()
        tomlContent = tomlContent.replace(Regex("agp = \".*\""), "agp = \"$agpVersion\"")

        // If testing an older AGP, we dynamically override the kotlin version
        // since older AGPs didn't have built-in Kotlin support and rely on the older KGP.
        if (!isModernKotlin) {
            tomlContent = tomlContent.replace(Regex("kotlin = \".*\""), "kotlin = \"2.1.10\"")
        }
        tomlFile.writeText(tomlContent)

        // Update build.gradle.kts to handle older Kotlin compiler configs vs modern built-in Kotlin
        val buildFile = File(projectDir, "app/build.gradle.kts")
        var buildContent = buildFile.readText()
        if (!isModernKotlin) {
            buildContent = buildContent.replace(
                """
                kotlin {
                    jvmToolchain(21)
                    compilerOptions {
                        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                    }
                }
                """.trimIndent(),
                """
                tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                    @Suppress("DEPRECATION")
                    kotlinOptions {
                        jvmTarget = "21"
                        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
                    }
                }
                """.trimIndent()
            )
        }
        buildFile.writeText(buildContent)
    }

    private fun createRunner(vararg arguments: String): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withGradleVersion(gradleVersion)
            .withTestKitDir(File(System.getProperty("testkit_path"), this.javaClass.simpleName))
            .forwardOutput()
            .withArguments(*arguments, "--configuration-cache", "--parallel", "-Dorg.gradle.configuration-cache.problems=fail", "-s")
    }

    @Test
    fun testTestAppTestsPass() {
        val result = createRunner("build").build()
        Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":app:build")?.outcome)
    }
}

// Due to the dependency requirements of the library, we can only test with recent versions of AGP
class TestAppEndToEndTest_AGP810_G811 : TestAppEndToEndTest("8.10.0", "8.11.1")
class TestAppEndToEndTest_AGP812_G814 : TestAppEndToEndTest("8.12.2", "8.14")
class TestAppEndToEndTest_AGP90_G90 : TestAppEndToEndTest("9.0.0-alpha03", "9.0.0")
class TestAppEndToEndTest_AGP91_G931 : TestAppEndToEndTest("9.1.0-alpha05", "9.3.1")
