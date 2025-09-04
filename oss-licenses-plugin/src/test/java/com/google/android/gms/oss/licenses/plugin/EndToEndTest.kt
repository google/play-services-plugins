package com.google.android.gms.oss.licenses.plugin

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File

@RunWith(Parameterized::class)
class EndToEndTest(private val agpVersion: String, private val gradleVersion: String) {
    companion object {
        @get:JvmStatic
        @get:Parameters(name = "agpVersion={0},gradleVersion={1}")
        @Suppress("unused") // needed for Parameterized
        val params = listOf(
            arrayOf("8.2.0", "8.2"),
            arrayOf("8.10.0", "8.11.1"),
            arrayOf("8.12.2", "8.14"),
            arrayOf("9.0.0-alpha03", "9.0.0"),
        )
    }

    @get:Rule
    val tempDirectory: TemporaryFolder = TemporaryFolder()

    private fun isBuiltInKotlinEnabled() = agpVersion.startsWith("9.")

    @Test
    fun basic() {
        val projectDir = tempDirectory.newFolder("basic")
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
                implementation("com.google.android.gms:play-services-oss-licenses:17.0.0")
            }
        """.trimIndent()
        )
        File(projectDir, "gradle.properties").writeText(
            """
            android.useAndroidX=true
        """.trimIndent()
        )
        File(projectDir, "settings.gradle").writeText(
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
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withGradleVersion(gradleVersion)
            .withArguments("releaseOssLicensesTask", "-s")
            .build()
        Assert.assertEquals(result.task(":collectReleaseDependencies")!!.outcome, TaskOutcome.SUCCESS)
        Assert.assertEquals(result.task(":releaseOssDependencyTask")!!.outcome, TaskOutcome.SUCCESS)
        Assert.assertEquals(result.task(":releaseOssLicensesTask")!!.outcome, TaskOutcome.SUCCESS)
        val dependenciesJson = File(projectDir, "build/generated/third_party_licenses/release/dependencies.json")
        Assert.assertEquals(expectedDependenciesJson(isBuiltInKotlinEnabled()), dependenciesJson.readText())

        val metadata =
            File(projectDir, "build/generated/resources/releaseOssLicensesTask/raw/third_party_license_metadata")
        Assert.assertEquals(expectedContents(isBuiltInKotlinEnabled()), metadata.readText())
    }
}

private fun expectedDependenciesJson(builtInKotlinEnabled: Boolean) = """[
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
        "version": "2.2.0"""" else ""}
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
