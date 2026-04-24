/*
 * Copyright 2025-2026 Google LLC
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
 * E2E test that builds the standalone testapp against multiple AGP/Gradle versions.
 */
abstract class EndToEndTest {

    // AGP and Gradle versions are defined in build.gradle.kts (single source of truth) and injected
    // as system properties keyed by class name. E.g., EndToEndTest_AGP812 reads the system
    // properties "EndToEndTest_AGP812.agpVersion" and "EndToEndTest_AGP812.gradleVersion".
    // To add a new version: add an entry to e2eVersions in build.gradle.kts and a subclass here
    // whose name matches the map key (prefixed with "EndToEndTest_").
    private val agpVersion: String = System.getProperty("${javaClass.simpleName}.agpVersion")
        ?: error("Missing ${javaClass.simpleName}.agpVersion — add to e2eVersions in build.gradle.kts")
    private val gradleVersion: String = System.getProperty("${javaClass.simpleName}.gradleVersion")
        ?: error("Missing ${javaClass.simpleName}.gradleVersion — add to e2eVersions in build.gradle.kts")

    companion object {
        private val AGP_VERSION_REGEX = Regex("""agp = ".*"""")
        private val KOTLIN_VERSION_REGEX = Regex("""kotlin = ".*"""")

        // AGP 9+ has built-in Kotlin support; AGP 8.x requires the standalone KGP with legacy config.
        private val AGP_9_KOTLIN_BLOCK = """
            kotlin {
                jvmToolchain(21)
                compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
            }
        """.trimIndent()

        private val AGP_8_KOTLIN_BLOCK = """
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                @Suppress("DEPRECATION")
                kotlinOptions {
                    jvmTarget = "21"
                }
            }
        """.trimIndent()
    }

    @get:Rule
    val tempDirectory: TemporaryFolder = TemporaryFolder()

    private lateinit var projectDir: File

    @Before
    fun setup() {
        projectDir = tempDirectory.newFolder("testapp")

        val currentDir = File(System.getProperty("user.dir")!!) // if this is missing then something is very wrong
        val testappDirPath = System.getProperty("testapp.dir")
        requireNotNull(testappDirPath) { "testapp.dir system property is missing" }

        val testAppSourceDir = File(testappDirPath)
        require(testAppSourceDir.exists()) {
            "Test app source not found at: ${testAppSourceDir.absolutePath}"
        }

        configureAndroidSdk(currentDir)
        testAppSourceDir.copyRecursively(projectDir, overwrite = true)

        // Remove the Gradle daemon JVM file if present — the JAVA_HOME injection in createRunner()
        // handles JVM selection more cleanly across all Gradle versions.
        File(projectDir, "gradle/gradle-daemon-jvm.properties").delete()

        patchVersions()
    }

    private fun configureAndroidSdk(currentDir: File) {
        val sdkDir = System.getenv("ANDROID_HOME")
            ?: File(currentDir, "local.properties").takeIf { it.exists() }
                ?.readLines()?.firstOrNull { it.startsWith("sdk.dir=") }
                ?.substringAfter("sdk.dir=")
            ?: error("Cannot find Android SDK: set ANDROID_HOME or create local.properties")
        File(projectDir, "local.properties").writeText("sdk.dir=${sdkDir.replace("\\", "\\\\")}\n")
    }

    private fun patchVersions() {
        val agpBundlesKgp = agpVersion.substringBefore('.').toIntOrNull()?.let { it >= 9 } ?: false

        // Patch AGP (and optionally Kotlin) version in the version catalog
        val tomlFile = File(projectDir, "gradle/libs.versions.toml")
        var tomlContent = tomlFile.readText()
        check(AGP_VERSION_REGEX.containsMatchIn(tomlContent)) {
            "libs.versions.toml missing expected 'agp = \"...\"' entry — has the testapp template changed?"
        }
        tomlContent = tomlContent.replace(AGP_VERSION_REGEX, "agp = \"$agpVersion\"")
        if (!agpBundlesKgp) {
            check(KOTLIN_VERSION_REGEX.containsMatchIn(tomlContent)) {
                "libs.versions.toml missing expected 'kotlin = \"...\"' entry — has the testapp template changed?"
            }
            tomlContent = tomlContent.replace(KOTLIN_VERSION_REGEX, "kotlin = \"2.1.10\"")
        }
        tomlFile.writeText(tomlContent)

        // AGP 8.x doesn't have built-in Kotlin support — replace with standalone KGP config
        if (!agpBundlesKgp) {
            val buildFile = File(projectDir, "app/build.gradle.kts")
            val original = buildFile.readText()
            val patched = original.replace(AGP_9_KOTLIN_BLOCK, AGP_8_KOTLIN_BLOCK)
            check(patched != original) {
                "Failed to patch Kotlin block in app/build.gradle.kts — has the testapp template changed?"
            }
            buildFile.writeText(patched)
        }
    }

    private fun createRunner(vararg arguments: String): GradleRunner {
        // -PusePublishedPluginFrom forces the testapp to resolve the plugin from the locally-published
        // Maven repo (see testapp/settings.gradle.kts). Without it, includeBuild("..") points at the
        // temp folder's parent, falls through silently, and Gradle resolves the "+" version from
        // Google Maven — meaning the test runs against the published plugin, not the local build.
        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withGradleVersion(gradleVersion)
            .withTestKitDir(File(System.getProperty("testkit_path"), this.javaClass.simpleName))
            .forwardOutput()
            .withArguments(
                *arguments,
                "-PusePublishedPluginFrom=${System.getProperty("repo_path")}",
                "--configuration-cache", "--parallel",
                "-Dorg.gradle.configuration-cache.problems=fail", "-s"
            )

        val javaHome = System.getProperty("java21_home")
        if (javaHome != null) {
            // Merge with the host environment — withEnvironment() replaces it entirely, so a bare
            // map of {JAVA_HOME} would strip PATH, ANDROID_HOME, HOME, etc. from the forked Gradle.
            runner.withEnvironment(System.getenv() + mapOf("JAVA_HOME" to javaHome))
        }
        return runner
    }

    @Test
    fun testBuildSucceeds() {
        val result = createRunner("build").build()
        Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":app:build")?.outcome)
    }
}

// Due to the dependency requirements of the library, we can only test with recent versions of AGP
class EndToEndTest_AGP812 : EndToEndTest()
class EndToEndTest_AGP_STABLE : EndToEndTest()
class EndToEndTest_AGP_ALPHA : EndToEndTest()
