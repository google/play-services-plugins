/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gms.googleservices;

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GoogleServicesPluginTest {

    @get:Rule
    val tempFolder = TemporaryFolder()


    private fun copyProjectToTemp(projectName: String) {
        File("src/test/testData/$projectName").copyRecursively(tempFolder.root)
    }

    private fun getExpectedResults(projectName: String) =
        File("src/test/testData/$projectName-expected")

    private fun runBuild(expectFailure: Boolean = false) =
        GradleRunner.create()
            .withProjectDir(tempFolder.root)
            .forwardOutput() // useful for debugging build under test
            .withArguments(
                "assembleDebug",
                /*
                GradleRunner.withPluginClasspath() won't work, because it puts the
                plugin in an isolated classloader so it can't interact with AGP.
                Instead we're passing the path to the built Maven repo.
                 */
                "-PpluginRepo=${File("build/repo").absolutePath}"
            ).run {
                if (expectFailure) {
                    buildAndFail()
                } else {
                    build()
                }
            }

    private fun compareResults(actualResults: File, expectedResults: File) {
        Assert.assertFalse(expectedResults.listFiles().isNullOrEmpty())
        expectedResults.walk().forEach {
            if (!it.isFile) {
                return@forEach
            }
            val relativePath = it.relativeTo(expectedResults)
            Assert.assertEquals(
                "File $relativePath doesn't match expected golden value.",
                it.readText(),
                actualResults.resolve(relativePath).readText()
            )
        }
    }

    @Test
    fun `res file generation with AGP 7,4`() {
        val projectName = "project1"

        copyProjectToTemp(projectName)
        val buildResult = runBuild()

        Assert.assertEquals(TaskOutcome.SUCCESS, buildResult.task(":app:assembleDebug")?.outcome)

        val actualResults = tempFolder.root.resolve("app/build/generated/res/")
        val expectedResults = getExpectedResults(projectName)
        compareResults(actualResults, expectedResults)
    }

    @Test
    fun `res file generation with AGP 7,3`() {
        val projectName = "project1"

        copyProjectToTemp(projectName + "-agp730")
        val buildResult = runBuild()

        Assert.assertEquals(TaskOutcome.SUCCESS, buildResult.task(":app:assembleDebug")?.outcome)

        val actualResults = tempFolder.root.resolve("app/build/RES/")
        val expectedResults = getExpectedResults(projectName)
        compareResults(actualResults, expectedResults)
    }

    @Test
    fun `missing Google Services file - error`() {
        val projectName = "project1"

        copyProjectToTemp(projectName)
        tempFolder.root.resolve("app/src/main/google-services/google-services.json").delete()
        val buildResult = runBuild(expectFailure = true)

        Assert.assertEquals(
            TaskOutcome.FAILED,
            buildResult.task(":app:processFreeOneDebugGoogleServices")?.outcome
        )
        Assert.assertTrue(buildResult.output.contains("File google-services.json is missing"))
    }

    @Test
    fun `missing Google Services file - warn`() {
        val projectName = "project1"

        copyProjectToTemp(projectName)
        tempFolder.root.resolve("app/src/main/google-services/google-services.json").delete()
        val buildFile = tempFolder.root.resolve("app/build.gradle.kts")
        buildFile.writeText(buildFile.readText().replace(
            "MissingGoogleServicesStrategy.ERROR",
            "MissingGoogleServicesStrategy.WARN"
        ))
        val buildResult = runBuild()

        Assert.assertEquals(
            TaskOutcome.SUCCESS,
            buildResult.task(":app:processFreeOneDebugGoogleServices")?.outcome
        )
        Assert.assertTrue(buildResult.output.contains("File google-services.json is missing"))
    }

    @Test
    fun `missing Google Services file - ignore`() {
        val projectName = "project1"

        copyProjectToTemp(projectName)
        tempFolder.root.resolve("app/src/main/google-services/google-services.json").delete()
        val buildFile = tempFolder.root.resolve("app/build.gradle.kts")
        buildFile.writeText(buildFile.readText().replace(
            "MissingGoogleServicesStrategy.ERROR",
            "MissingGoogleServicesStrategy.IGNORE"
        ))

        val buildResult = runBuild()

        Assert.assertEquals(
            TaskOutcome.SUCCESS,
            buildResult.task(":app:processFreeOneDebugGoogleServices")?.outcome
        )
        Assert.assertFalse(buildResult.output.contains("File google-services.json is missing"))
    }



    @Test
    fun `flavor specific google-services,json`() {
        val projectName = "project3"

        copyProjectToTemp(projectName)
        val buildResult = runBuild()

        Assert.assertEquals(TaskOutcome.SUCCESS, buildResult.task(":app:assembleDebug")?.outcome)

        val actualResults = tempFolder.root.resolve("app/build/generated/res/")
        val expectedResults = getExpectedResults(projectName)
        compareResults(actualResults, expectedResults)
    }

    @Test
    fun `ambiguous google-services,json`() {
        val projectName = "project3"

        copyProjectToTemp(projectName)
        tempFolder.root.resolve("app/src/free/google-services/google-services.json")
            .copyTo(tempFolder.root.resolve("app/src/main/google-services/google-services.json"))
        val buildResult = runBuild(expectFailure = true)

        Assert.assertEquals(
            TaskOutcome.FAILED,
            buildResult.task(":app:processFreeOneDebugGoogleServices")?.outcome
        )
        Assert.assertTrue(buildResult.output.contains("More than one google-services.json found"))
    }

//    /* Uncomment, edit project paths and run this test to regenerate the
//    XML files that will be used for comparing output. */
//    @Test
//    fun generateGoldenXmlFiles() {
//        val expectedResults = File("src/test/testData/project1-expected")
//        File("src/test/testData/project1").copyRecursively(tempFolder.root)
//        val gradleRunner = GradleRunner.create()
//            .withProjectDir(tempFolder.root)
//            .withArguments(
//                "assembleDebug",
//                /*
//                GradleRunner.withPluginClasspath() won't work, because it puts the
//                plugin in an isolated classloader so it can't interact with AGP.
//                Instead we're passing the path to the built Maven repo.
//                 */
//                "-PpluginRepo=${File("build/repo").absolutePath}"
//            )
//            .build()
//
//        Assert.assertEquals(TaskOutcome.SUCCESS, gradleRunner.task(":app:assembleDebug")?.outcome)
//        val actualResults = tempFolder.root
//            .resolve("app/build/generated/res/google-services")
//        actualResults.copyRecursively(expectedResults)
//    }
}
