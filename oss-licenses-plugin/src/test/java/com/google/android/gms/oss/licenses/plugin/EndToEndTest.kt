package com.google.android.gms.oss.licenses.plugin

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File

class EndToEndTest {
    @get:Rule
    val tempDirectory: TemporaryFolder = TemporaryFolder()

    @Test
    fun basic() {
        val projectDir = tempDirectory.newFolder("basic")
        File(projectDir, "build.gradle").writeText(
            """               
            plugins {
                id("com.android.application")
                id("com.google.android.gms.oss-licenses-plugin")
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
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withGradleVersion("8.2")
            .withArguments("releaseOssLicensesTask", "-s")
            .withPluginClasspath().build()
        Assert.assertEquals(result.task(":collectReleaseDependencies")!!.outcome, TaskOutcome.SUCCESS)
        Assert.assertEquals(result.task(":releaseOssDependencyTask")!!.outcome, TaskOutcome.SUCCESS)
        Assert.assertEquals(result.task(":releaseOssLicensesTask")!!.outcome, TaskOutcome.SUCCESS)
        val metadata =
            File(projectDir, "build/generated/resources/releaseOssLicensesTask/raw/third_party_license_metadata")
        Assert.assertTrue(metadata.readText().contains("play-services-oss-licenses"))
    }
}
