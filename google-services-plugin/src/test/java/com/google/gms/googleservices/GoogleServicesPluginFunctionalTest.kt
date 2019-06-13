package com.google.gms.googleservices

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File

@RunWith(Parameterized::class)
class GoogleServicesPluginFunctionalTest(androidPluginVersion: String, private val gradleVersion: String) {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val projectGenerator = TestProjectGenerator(
        androidPluginVersion = androidPluginVersion,
        apiLevel = 28,
        packageName = "com.google.gms.googleservices.test"
    )
    private lateinit var projectDir: File

    @Before
    fun setUp() {
        projectDir = tempFolder.newFolder("project")
    }

    @Test
    fun applicationModule() {
        projectGenerator.generateAndroidModule(projectDir, "com.android.application")

        val result = buildProject()

        assertEquals(TaskOutcome.SUCCESS, result.task(":processDebugGoogleServices")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleDebug")?.outcome)
    }

    @Test
    fun libraryModule() {
        projectGenerator.generateAndroidModule(projectDir, "com.android.library")

        val result = buildProject()

        assertEquals(TaskOutcome.SUCCESS, result.task(":processDebugGoogleServices")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleDebug")?.outcome)
    }

    private fun buildProject(): BuildResult {
        return GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withPluginClasspath()
            .withProjectDir(projectDir)
            .withArguments(":assembleDebug")
            .forwardOutput()
            .build()
    }

    private data class AndroidPluginVersion(
        val version: String,
        val minGradleVersion: GradleVersion
    ) {
        constructor(version: String, minGradleVersion: String) : this(version, GradleVersion.version(minGradleVersion))
    }

    companion object {
        private val AGP_VERSIONS = arrayOf(
            AndroidPluginVersion("3.1.4", minGradleVersion = "4.4"),
            AndroidPluginVersion("3.2.1", minGradleVersion = "4.6"),
            AndroidPluginVersion("3.3.2", minGradleVersion = "4.10.1"),
            AndroidPluginVersion("3.4.2", minGradleVersion = "5.1.1"),
            AndroidPluginVersion("3.5.0", minGradleVersion = "5.4.1")
        )
        private val GRADLE_VERSIONS = arrayOf(
            GradleVersion.version("4.6"),
            GradleVersion.version("4.10.3"),
            GradleVersion.version("5.1.1"),
            GradleVersion.version("5.4.1")
        )

        @JvmStatic
        @Parameters(name = "Android Plugin: {0}, Gradle: {1}")
        fun params(): List<Array<Any>> {
            return AGP_VERSIONS.asSequence()
                .flatMap { androidPluginVersion ->
                    GRADLE_VERSIONS.asSequence()
                        .filter { gradleVersion -> gradleVersion >= androidPluginVersion.minGradleVersion }
                        .map { gradleVersion -> arrayOf<Any>(androidPluginVersion.version, gradleVersion.version) }
                }
                .toList()
        }
    }
}
