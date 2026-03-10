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
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File

abstract class EndToEndTest(private val agpVersion: String, private val gradleVersion: String) {

    @get:Rule
    val tempDirectory: TemporaryFolder = TemporaryFolder()

    private fun isBuiltInKotlinEnabled() = agpVersion.startsWith("9.")

    private lateinit var projectDir: File

    private fun createRunner(vararg arguments: String): GradleRunner {
        return createRunnerWithDir(projectDir, *arguments)
    }

    private fun createRunnerWithDir(dir: File, vararg arguments: String): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(dir)
            .withGradleVersion(gradleVersion)
            .forwardOutput()
            // Isolate TestKit per AGP version subclass to allow parallel execution
            // while keeping all metadata inside the project's build directory for cleanliness.
            .withTestKitDir(File(System.getProperty("testkit_path"), this.javaClass.simpleName))
            // Enable strict configuration cache mode for all tests.
            .withArguments(*arguments, "--configuration-cache", "-Dorg.gradle.configuration-cache.problems=fail")
    }

    @Before
    fun setup() {
        projectDir = tempDirectory.newFolder("basic")
        setupProject(projectDir)
    }

    private fun setupProject(dir: File) {
        File(dir, "build.gradle").writeText(
            """
            plugins {
                id("com.android.application") version "$agpVersion"
                id("com.google.android.gms.oss-licenses-plugin") version "${System.getProperty("plugin_version")}"
            }
            repositories {
                google()
                mavenCentral()
            }
            android {
                compileSdkVersion = "android-31"
                namespace = "com.example.app"
            }
            dependencies {
                implementation("com.google.android.gms:play-services-oss-licenses:17.0.0")
            }
        """.trimIndent()
        )
        File(dir, "gradle.properties").writeText(
            """
            android.useAndroidX=true
            com.google.protobuf.use_unsafe_pre22_gencode=true
        """.trimIndent()
        )
        File(dir, "settings.gradle").writeText(
            """
            pluginManagement {
                repositories {
                    maven {
                         url = uri("${System.getProperty("repo_path")}")
                    }
                    google()
                    mavenCentral()
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun basic() {
        val result = createRunner("releaseOssLicensesTask").build()
        Assert.assertEquals(result.task(":collectReleaseDependencies")!!.outcome, TaskOutcome.SUCCESS)
        Assert.assertEquals(result.task(":releaseOssDependencyTask")!!.outcome, TaskOutcome.SUCCESS)
        Assert.assertEquals(result.task(":releaseOssLicensesTask")!!.outcome, TaskOutcome.SUCCESS)
        val dependenciesJson = File(projectDir, "build/generated/third_party_licenses/release/dependencies.json")
        Assert.assertEquals(expectedDependenciesJson(isBuiltInKotlinEnabled(), agpVersion), dependenciesJson.readText())

        val metadata =
            File(projectDir, "build/generated/res/releaseOssLicensesTask/raw/third_party_license_metadata")
        Assert.assertEquals(expectedContents(isBuiltInKotlinEnabled()), metadata.readText())
    }

    @Test
    fun testAbsentDependencyReport() {
        val result = createRunner("debugOssLicensesTask").build()
        Assert.assertEquals(result.task(":debugOssDependencyTask")!!.outcome, TaskOutcome.SUCCESS)
        Assert.assertEquals(result.task(":debugOssLicensesTask")!!.outcome, TaskOutcome.SUCCESS)

        val licenses = File(projectDir, "build/generated/res/debugOssLicensesTask/raw/third_party_licenses")
        Assert.assertEquals(LicensesTask.ABSENT_DEPENDENCY_TEXT + "\n", licenses.readText())
    }

    @Test
    fun testConfigurationCache() {
        // First run to store the configuration cache
        createRunner("releaseOssLicensesTask").build()

        // Clean to test configuration cache with a clean build
        createRunner("clean").build()

        // Second run to reuse the configuration cache
        val result = createRunner("releaseOssLicensesTask").build()

        Assert.assertTrue(
            result.output.contains("Reusing configuration cache") ||
                result.output.contains("Configuration cache entry reused")
        )
    }

    @Test
    fun testComplexDependencyGraph() {
        // Create a multi-module setup to test configuration cache with complex resolution
        val libDir = tempDirectory.newFolder("lib")
        File(libDir, "build.gradle").writeText(
            """
            plugins { id("com.android.library") }
            android {
                compileSdkVersion = "android-31"
                namespace = "com.example.lib"
            }
            dependencies {
                implementation("com.google.code.gson:gson:2.10.1")
            }
        """.trimIndent()
        )
        File(projectDir, "settings.gradle").appendText("\ninclude ':lib'\nproject(':lib').projectDir = new File('${libDir.absolutePath.replace("\\", "/")}')")

        // Rewrite the main build.gradle to include the project dependency and a forced conflict
        File(projectDir, "build.gradle").writeText(
            """
            plugins {
                id("com.android.application") version "$agpVersion"
                id("com.google.android.gms.oss-licenses-plugin") version "${System.getProperty("plugin_version")}"
            }
            repositories {
                google()
                mavenCentral()
            }
            android {
                compileSdkVersion = "android-31"
                namespace = "com.example.app"
            }
            dependencies {
                implementation(project(":lib"))
                // Version conflict: lib uses 2.10.1, we force 2.8.9
                implementation("com.google.code.gson:gson") {
                    version {
                        strictly("2.8.9")
                    }
                }
            }
        """.trimIndent()
        )

        // Run with configuration cache twice to ensure resolution is stable and cacheable
        createRunner("releaseOssLicensesTask").build()

        val result = createRunner("releaseOssLicensesTask").build()

        Assert.assertTrue(
            result.output.contains("Configuration cache entry reused") ||
                result.output.contains("Reusing configuration cache")
        )

        // Verify output exists and contains the forced version's license link
        val licensesFile = File(projectDir, "build/generated/res/releaseOssLicensesTask/raw/third_party_licenses")
        Assert.assertTrue(licensesFile.exists())
        val content = licensesFile.readText()
        // Gson 2.8.9 specifically uses the Apache 2.0 license URL.
        Assert.assertTrue(content.contains("apache.org/licenses/LICENSE-2.0"))
    }

    @Test
    fun testRelocatability() {
        val cacheDir = tempDirectory.newFolder("cache")
        val dir1 = tempDirectory.newFolder("dir1")
        val dir2 = tempDirectory.newFolder("dir2")

        // Helper to populate a directory with the test project
        fun populate(dir: File) {
            // ONLY copy the source files, NEVER the build outputs or local cache state
            projectDir.listFiles()?.forEach { file ->
                if (file.name != "build" && file.name != ".gradle") {
                    file.copyRecursively(File(dir, file.name), overwrite = true)
                }
            }

            // Update the settings.gradle to point to the correct repo path in the new location
            File(dir, "settings.gradle").writeText(
                """
                pluginManagement {
                    repositories {
                        maven {
                             url = uri("${System.getProperty("repo_path")}")
                        }
                        google()
                        mavenCentral()
                    }
                }

                buildCache {
                    local {
                        directory = '${cacheDir.absolutePath.replace("\\", "/")}'
                    }
                }
                """.trimIndent()
            )
        }
        populate(dir1)
        populate(dir2)

        // 1. Run in dir1 to prime the cache
        val result1 = createRunnerWithDir(dir1, "releaseOssLicensesTask", "--build-cache").build()
        Assert.assertEquals(TaskOutcome.SUCCESS, result1.task(":releaseOssLicensesTask")?.outcome)

        // 2. Run in dir2 (different absolute path) and expect FROM-CACHE
        val result2 = createRunnerWithDir(dir2, "releaseOssLicensesTask", "--build-cache").build()

        Assert.assertEquals(
            "LicensesTask should be relocatable",
            TaskOutcome.FROM_CACHE,
            result2.task(":releaseOssLicensesTask")?.outcome
        )
        Assert.assertEquals(
            "DependencyTask should be relocatable",
            TaskOutcome.FROM_CACHE,
            result2.task(":releaseOssDependencyTask")?.outcome
        )
    }
}

class EndToEndTest_AGP74_G75 : EndToEndTest("7.4.2", "7.5.1")
class EndToEndTest_AGP80_G80 : EndToEndTest("8.0.2", "8.0.2")
class EndToEndTest_AGP87_G89 : EndToEndTest("8.7.3", "8.9")
class EndToEndTest_AGP812_G814 : EndToEndTest("8.12.2", "8.14.1")
class EndToEndTest_AGP_STABLE_90_G90 : EndToEndTest("9.0.1", "9.1.0")
class EndToEndTest_AGP_ALPHA_92_G94 : EndToEndTest("9.2.0-alpha02", "9.4.0")

private fun expectedDependenciesJson(builtInKotlinEnabled: Boolean, agpVersion: String) = """[
    {
        "group": "androidx.annotation",
        "name": "annotation",
        "version": "1.0.0"
    },
    {
        "group": "androidx.appcompat",
        "name": "appcompat",
        "version": "1.0.0"
    },
    {
        "group": "androidx.arch.core",
        "name": "core-common",
        "version": "2.0.0"
    },
    {
        "group": "androidx.arch.core",
        "name": "core-runtime",
        "version": "2.0.0"
    },
    {
        "group": "androidx.asynclayoutinflater",
        "name": "asynclayoutinflater",
        "version": "1.0.0"
    },
    {
        "group": "androidx.collection",
        "name": "collection",
        "version": "1.0.0"
    },
    {
        "group": "androidx.coordinatorlayout",
        "name": "coordinatorlayout",
        "version": "1.0.0"
    },
    {
        "group": "androidx.core",
        "name": "core",
        "version": "1.0.0"
    },
    {
        "group": "androidx.cursoradapter",
        "name": "cursoradapter",
        "version": "1.0.0"
    },
    {
        "group": "androidx.customview",
        "name": "customview",
        "version": "1.0.0"
    },
    {
        "group": "androidx.documentfile",
        "name": "documentfile",
        "version": "1.0.0"
    },
    {
        "group": "androidx.drawerlayout",
        "name": "drawerlayout",
        "version": "1.0.0"
    },
    {
        "group": "androidx.fragment",
        "name": "fragment",
        "version": "1.0.0"
    },
    {
        "group": "androidx.interpolator",
        "name": "interpolator",
        "version": "1.0.0"
    },
    {
        "group": "androidx.legacy",
        "name": "legacy-support-core-ui",
        "version": "1.0.0"
    },
    {
        "group": "androidx.legacy",
        "name": "legacy-support-core-utils",
        "version": "1.0.0"
    },
    {
        "group": "androidx.lifecycle",
        "name": "lifecycle-common",
        "version": "2.0.0"
    },
    {
        "group": "androidx.lifecycle",
        "name": "lifecycle-livedata",
        "version": "2.0.0"
    },
    {
        "group": "androidx.lifecycle",
        "name": "lifecycle-livedata-core",
        "version": "2.0.0"
    },
    {
        "group": "androidx.lifecycle",
        "name": "lifecycle-runtime",
        "version": "2.0.0"
    },
    {
        "group": "androidx.lifecycle",
        "name": "lifecycle-viewmodel",
        "version": "2.0.0"
    },
    {
        "group": "androidx.loader",
        "name": "loader",
        "version": "1.0.0"
    },
    {
        "group": "androidx.localbroadcastmanager",
        "name": "localbroadcastmanager",
        "version": "1.0.0"
    },
    {
        "group": "androidx.print",
        "name": "print",
        "version": "1.0.0"
    },
    {
        "group": "androidx.slidingpanelayout",
        "name": "slidingpanelayout",
        "version": "1.0.0"
    },
    {
        "group": "androidx.swiperefreshlayout",
        "name": "swiperefreshlayout",
        "version": "1.0.0"
    },
    {
        "group": "androidx.vectordrawable",
        "name": "vectordrawable",
        "version": "1.0.0"
    },
    {
        "group": "androidx.vectordrawable",
        "name": "vectordrawable-animated",
        "version": "1.0.0"
    },
    {
        "group": "androidx.versionedparcelable",
        "name": "versionedparcelable",
        "version": "1.0.0"
    },
    {
        "group": "androidx.viewpager",
        "name": "viewpager",
        "version": "1.0.0"
    },
    {
        "group": "com.google.android.gms",
        "name": "play-services-base",
        "version": "17.0.0"
    },
    {
        "group": "com.google.android.gms",
        "name": "play-services-basement",
        "version": "17.0.0"
    },
    {
        "group": "com.google.android.gms",
        "name": "play-services-oss-licenses",
        "version": "17.0.0"
    },
    {
        "group": "com.google.android.gms",
        "name": "play-services-tasks",
        "version": "17.0.0"${if (builtInKotlinEnabled) """
    },
    {
        "group": "org.jetbrains",
        "name": "annotations",
        "version": "13.0"
    },
    {
        "group": "org.jetbrains.kotlin",
        "name": "kotlin-stdlib",
        "version": "${if (agpVersion.startsWith("9"))"2.2.10" else "2.2.0"}"""" else ""}
    }
]"""

private fun expectedContents(builtInKotlinEnabled: Boolean) = """0:46 Android Support Library Annotations
0:46 Android AppCompat Library v7
0:46 Android Arch-Common
0:46 Android Arch-Runtime
0:46 Android Support Library Async Layout Inflater
0:46 Android Support Library collections
0:46 Android Support Library Coordinator Layout
0:46 Android Support Library compat
0:46 Android Support Library Cursor Adapter
0:46 Android Support Library Custom View
0:46 Android Support Library Document File
0:46 Android Support Library Drawer Layout
0:46 Android Support Library fragment
0:46 Android Support Library Interpolators
0:46 Android Support Library core UI
0:46 Android Support Library core utils
0:46 Android Lifecycle-Common
0:46 Android Lifecycle LiveData
0:46 Android Lifecycle LiveData Core
0:46 Android Lifecycle Runtime
0:46 Android Lifecycle ViewModel
0:46 Android Support Library loader
0:46 Android Support Library Local Broadcast Manager
0:46 Android Support Library Print
0:46 Android Support Library Sliding Pane Layout
0:46 Android Support Library Custom View
0:46 Android Support VectorDrawable
0:46 Android Support AnimatedVectorDrawable
0:46 VersionedParcelable and friends
0:46 Android Support Library View Pager
47:47 play-services-base
95:21000 ICU4C
21096:1602 JSR 305
22699:1732 Protobuf Nano
24432:680 STL
25113:731 UTF
25845:11342 flatbuffers
37188:11358 safeparcel
47:47 play-services-basement
37188:11358 JSR 250
48547:11365 absl
47:47 play-services-oss-licenses
47:47 play-services-tasks
${if (builtInKotlinEnabled) """0:46 IntelliJ IDEA Annotations
0:46 Kotlin Stdlib
""" else ""
}"""
