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

abstract class IntegrationTest {

    // AGP and Gradle versions are defined in build.gradle.kts (single source of truth) and injected
    // as system properties keyed by class name. E.g., IntegrationTest_AGP74 reads the system
    // properties "IntegrationTest_AGP74.agpVersion" and "IntegrationTest_AGP74.gradleVersion".
    // To add a new version: add an entry to the version map in build.gradle.kts and a subclass here
    // whose name matches the map key (prefixed with "IntegrationTest_").
    private val agpVersion: String = System.getProperty("${javaClass.simpleName}.agpVersion")
        ?: error(
            "Missing ${javaClass.simpleName}.agpVersion — run this test via Gradle so version " +
                "properties are injected, or set the system property manually. If this is a new " +
                "test variant, add it to integrationVersions in build.gradle.kts"
        )
    private val gradleVersion: String = System.getProperty("${javaClass.simpleName}.gradleVersion")
        ?: error(
            "Missing ${javaClass.simpleName}.gradleVersion — run this test via Gradle so version " +
                "properties are injected, or set the system property manually. If this is a new " +
                "test variant, add it to integrationVersions in build.gradle.kts"
        )

    @get:Rule
    val tempDirectory: TemporaryFolder = TemporaryFolder()

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
            # AGP 9 auto-adds kotlin-stdlib via built-in Kotlin; opt out so the license
            # report is identical across the AGP 8 / AGP 9 matrix. Harmless on AGP 8.
            android.builtInKotlin=false
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
        Assert.assertEquals(expectedDependenciesJson(), dependenciesJson.readText())

        val metadata =
            File(projectDir, "build/generated/res/releaseOssLicensesTask/raw/third_party_license_metadata")
        Assert.assertEquals(expectedContents(), metadata.readText())
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
        val firstRun = createRunner("releaseOssLicensesTask").build()
        Assert.assertFalse(
            "Configurations should not be resolved during configuration time. Wrap resolution in a Provider.",
            firstRun.output.contains("resolved during configuration time")
        )

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
    fun testSnapshotChangeTriggersExecution() {
        val localRepo = tempDirectory.newFolder("localRepo")
        val group = "com.example.snapshot"
        val name = "test-lib"
        val version = "1.0.0-SNAPSHOT"

        // 1. Publish version 1
        publishSnapshot(localRepo, group, name, version, "License content v1")

        // 2. Setup project with local repo and snapshot dependency
        File(projectDir, "build.gradle").writeText(
            """
            plugins {
                id("com.android.application") version "$agpVersion"
                id("com.google.android.gms.oss-licenses-plugin") version "${System.getProperty("plugin_version")}"
            }
            repositories {
                maven { url = uri("${localRepo.absolutePath.replace("\\", "/")}") }
                google()
                mavenCentral()
            }
            android {
                compileSdkVersion = "android-31"
                namespace = "com.example.app"
            }
            dependencies {
                implementation("$group:$name:$version")
            }
        """.trimIndent()
        )

        // 3. First build - both tasks should succeed
        val firstResult = createRunner("releaseOssLicensesTask").build()
        Assert.assertEquals(TaskOutcome.SUCCESS, firstResult.task(":releaseOssDependencyTask")!!.outcome)
        Assert.assertEquals(TaskOutcome.SUCCESS, firstResult.task(":releaseOssLicensesTask")!!.outcome)

        // 4. Second build - both tasks should be UP-TO-DATE
        val secondResult = createRunner("releaseOssLicensesTask").build()
        Assert.assertEquals(TaskOutcome.UP_TO_DATE, secondResult.task(":releaseOssDependencyTask")!!.outcome)
        Assert.assertEquals(TaskOutcome.UP_TO_DATE, secondResult.task(":releaseOssLicensesTask")!!.outcome)

        // 5. Update snapshot - Publish version 2
        publishSnapshot(localRepo, group, name, version, "License content v2")

        // 6. Third build - DependencyTask must re-execute because the snapshot hash changed,
        // which in turn causes LicensesTask to re-execute.
        // --refresh-dependencies ensures Gradle re-downloads the snapshot from the local repo.
        val thirdResult = createRunner("releaseOssLicensesTask", "--refresh-dependencies").build()
        Assert.assertEquals(
            "DependencyTask should re-execute when snapshot content changes",
            TaskOutcome.SUCCESS, thirdResult.task(":releaseOssDependencyTask")!!.outcome
        )
        Assert.assertEquals(
            "LicensesTask should re-execute after DependencyTask produces new output",
            TaskOutcome.SUCCESS, thirdResult.task(":releaseOssLicensesTask")!!.outcome
        )
    }

    private fun publishSnapshot(repo: File, group: String, name: String, version: String, licenseText: String) {
        val groupPath = group.replace(".", "/")
        val artifactDir = File(repo, "$groupPath/$name/$version")
        artifactDir.mkdirs()

        // Write a simple POM with a license URL
        File(artifactDir, "$name-$version.pom").writeText(
            """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>$group</groupId>
              <artifactId>$name</artifactId>
              <version>$version</version>
              <licenses>
                <license>
                  <name>Test License</name>
                  <url>https://example.com/license</url>
                </license>
              </licenses>
            </project>
        """.trimIndent()
        )

        // Write maven-metadata.xml so Gradle can discover the SNAPSHOT version
        File(repo, "$groupPath/$name/maven-metadata.xml").writeText(
            """
            <metadata>
              <groupId>$group</groupId>
              <artifactId>$name</artifactId>
              <versioning>
                <versions>
                  <version>$version</version>
                </versions>
              </versioning>
            </metadata>
        """.trimIndent()
        )

        // Write a JAR file. Changing licenseText changes the file's hash.
        File(artifactDir, "$name-$version.jar").writeText("Random content to change hash: $licenseText")
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

class IntegrationTest_AGP74 : IntegrationTest()
class IntegrationTest_AGP87 : IntegrationTest()
class IntegrationTest_AGP812 : IntegrationTest()
class IntegrationTest_AGP_STABLE : IntegrationTest()
class IntegrationTest_AGP_ALPHA : IntegrationTest()

private fun expectedDependenciesJson() = """[
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
        "version": "17.0.0"
    }
]"""

private fun expectedContents() = """0:46 Android Support Library Annotations
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
"""
