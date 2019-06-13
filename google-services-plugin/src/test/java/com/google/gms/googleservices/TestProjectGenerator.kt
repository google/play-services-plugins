package com.google.gms.googleservices

import com.google.common.io.Resources
import java.io.File

class TestProjectGenerator(
    private val androidPluginVersion: String,
    private val apiLevel: Int,
    private val packageName: String
) {
    fun generateAndroidModule(projectDir: File, androidPluginId: String) {
        createBuildGradle(projectDir, androidPluginId)
        createSettingsGradle(projectDir)
        createLocalProperties(projectDir)
        createAndroidManifest(projectDir)
        copyGoogleServicesJson(projectDir)
    }

    private fun createBuildGradle(projectDir: File, androidPluginId: String) {
        val buildGradle = File(projectDir, "build.gradle")

        //language=Groovy
        buildGradle.writeText(
            """
            buildscript {
                repositories {
                    google()
                    jcenter()
                }
                dependencies {
                    classpath 'com.android.tools.build:gradle:$androidPluginVersion'
                }
            }

            plugins {
                id 'com.google.gms.google-services'
            }

            apply plugin: '$androidPluginId'

            repositories {
                google()
                jcenter()
            }

            android {
                compileSdkVersion $apiLevel

                defaultConfig {
                    minSdkVersion 14
                    targetSdkVersion $apiLevel
                }
            }
        """.trimIndent()
        )
    }

    private fun createSettingsGradle(projectDir: File) {
        projectDir.newFile("settings.gradle").createNewFile()
    }

    private fun createLocalProperties(projectDir: File) {
        val androidHome = System.getenv("ANDROID_HOME")
            ?: throw AssertionError("ANDROID_HOME is not set")

        val localPropertiesFile = projectDir.newFile("local.properties")
        localPropertiesFile.writeText("sdk.dir=$androidHome")
    }

    private fun createAndroidManifest(projectDir: File) {
        val manifestFile = projectDir.newFile("src/main/AndroidManifest.xml")
        //language=XML
        manifestFile.writeText(
            """
            <manifest package="$packageName" />
        """.trimIndent()
        )
    }

    private fun copyGoogleServicesJson(projectDir: File) {
        val googleServicesJson = projectDir.newFile("google-services.json")
        googleServicesJson.outputStream().buffered().use { output ->
            @Suppress("UnstableApiUsage")
            Resources.copy(Resources.getResource("mock-google-services.json"), output)
        }
    }

    private fun File.newFile(path: String): File {
        val file = File(this, path)
        file.parentFile.mkdirs()
        return file
    }
}
