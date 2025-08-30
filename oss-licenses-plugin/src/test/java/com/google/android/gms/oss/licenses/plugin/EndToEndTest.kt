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
        val metadata =
            File(projectDir, "build/generated/resources/releaseOssLicensesTask/raw/third_party_license_metadata")
        Assert.assertTrue(metadata.readText().contains("play-services-oss-licenses"))
    }
}
