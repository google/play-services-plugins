/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.gms.googleservices

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GoogleServicesPluginTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private fun copyProjectToTemp(projectName: String) {
    File("src/test/testData/$projectName").copyRecursively(tempFolder.root)
  }

  private fun getExpectedResults(projectName: String) =
      File("src/test/testData/$projectName-expected")

  private fun runBuild(gradleVersion: String, agpVersion: String, expectFailure: Boolean = false) =
      GradleRunner.create()
          .withProjectDir(tempFolder.root)
          .withGradleVersion(gradleVersion)
          .forwardOutput() // useful for debugging build under test
          .withArguments(
            "-s",
              "assembleDebug",
              /*
              GradleRunner.withPluginClasspath() won't work, because it puts the
              plugin in an isolated classloader so it can't interact with AGP.
              Instead we're passing the path to the built Maven repo.
               */
              "-PpluginRepo=${File("build/repo").absolutePath}",
              "-DpluginVersion=${System.getProperty("plugin_version")}",
              "-DagpVersion=$agpVersion",
            )
          .run {
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
          actualResults.resolve(relativePath).readText())
    }
  }

  @Test
  fun `res file generation with AGP 9,0,0-alpha04`() {
    val projectName = "project1"

    copyProjectToTemp(projectName)
    val buildResult = runBuild(gradleVersion = "9.0.0", agpVersion = "9.0.0-alpha04")

    Assert.assertEquals(TaskOutcome.SUCCESS, buildResult.task(":app:assembleDebug")?.outcome)

    val actualResults = tempFolder.root.resolve("app/build/generated/res/")
    val expectedResults = getExpectedResults(projectName)
    compareResults(actualResults, expectedResults)
  }

  @Test
  fun `res file generation with AGP 8,13`() {
    val projectName = "project1"

    copyProjectToTemp(projectName)
    val buildResult = runBuild(gradleVersion = "8.14.3", agpVersion = "8.13.0")

    Assert.assertEquals(TaskOutcome.SUCCESS, buildResult.task(":app:assembleDebug")?.outcome)

    val actualResults = tempFolder.root.resolve("app/build/generated/res/")
    val expectedResults = getExpectedResults(projectName)
    compareResults(actualResults, expectedResults)
  }

  @Test
  fun `res file generation with AGP 7,4`() {
    val projectName = "project1"

    copyProjectToTemp(projectName)
    val buildResult = runBuild(gradleVersion = "7.6", agpVersion = "7.4.1")

    Assert.assertEquals(TaskOutcome.SUCCESS, buildResult.task(":app:assembleDebug")?.outcome)

    val actualResults = tempFolder.root.resolve("app/build/generated/res/")
    val expectedResults = getExpectedResults(projectName)
    compareResults(actualResults, expectedResults)
  }

  @Test
  fun `res file generation with AGP 7,3`() {
    val projectName = "project1"

    copyProjectToTemp(projectName)
    val buildResult = runBuild(gradleVersion = "7.6", agpVersion = "7.3.0")

    Assert.assertEquals(TaskOutcome.SUCCESS, buildResult.task(":app:assembleDebug")?.outcome)

    val actualResults = tempFolder.root.resolve("app/build/RES/")
    val expectedResults = getExpectedResults(projectName)
    compareResults(actualResults, expectedResults)
  }

  @Test
  fun `missing Google Services file - error`() {
    val projectName = "project1"

    copyProjectToTemp(projectName)
    tempFolder.root.resolve("app/google-services.json").delete()
    val buildResult = runBuild(gradleVersion = "7.6", agpVersion = "7.4.1", expectFailure = true)

    Assert.assertEquals(
        TaskOutcome.FAILED, buildResult.task(":app:processFreeOneDebugGoogleServices")?.outcome)
    Assert.assertTrue(buildResult.output.contains("File google-services.json is missing"))
  }

  @Test
  fun `missing Google Services file - warn`() {
    val projectName = "project1"

    copyProjectToTemp(projectName)
    tempFolder.root.resolve("app/google-services.json").delete()
    val buildFile = tempFolder.root.resolve("app/build.gradle.kts")
    buildFile.writeText(
        buildFile
            .readText()
            .replace("MissingGoogleServicesStrategy.ERROR", "MissingGoogleServicesStrategy.WARN"))
    val buildResult = runBuild(gradleVersion = "7.6", agpVersion = "7.4.1")

    Assert.assertEquals(
        TaskOutcome.SUCCESS, buildResult.task(":app:processFreeOneDebugGoogleServices")?.outcome)
    Assert.assertTrue(buildResult.output.contains("File google-services.json is missing"))
  }

  @Test
  fun `missing Google Services file - ignore`() {
    val projectName = "project1"

    copyProjectToTemp(projectName)
    tempFolder.root.resolve("app/google-services.json").delete()
    val buildFile = tempFolder.root.resolve("app/build.gradle.kts")
    buildFile.writeText(
        buildFile
            .readText()
            .replace("MissingGoogleServicesStrategy.ERROR", "MissingGoogleServicesStrategy.IGNORE"))

    val buildResult = runBuild(gradleVersion = "7.6", agpVersion = "7.4.1")

    Assert.assertEquals(
        TaskOutcome.SUCCESS, buildResult.task(":app:processFreeOneDebugGoogleServices")?.outcome)
    Assert.assertFalse(buildResult.output.contains("File google-services.json is missing"))
  }

  @Test
  fun `flavor specific google-services,json`() {
    val projectName = "project3"

    copyProjectToTemp(projectName)
    val buildResult = runBuild(gradleVersion = "7.6", agpVersion = "7.4.1")

    Assert.assertEquals(TaskOutcome.SUCCESS, buildResult.task(":app:assembleDebug")?.outcome)

    val actualResults = tempFolder.root.resolve("app/build/generated/res/")
    val expectedResults = getExpectedResults(projectName)
    compareResults(actualResults, expectedResults)
  }

  @Test
  fun testNoFlavor() {
    val output: List<String> = GoogleServicesPlugin.getJsonLocations("release", emptyList())
    Assert.assertTrue(output.contains("src/release/google-services.json"))
  }

  @Test
  fun testOneFlavor() {
    val output: List<String> = GoogleServicesPlugin.getJsonLocations("release", listOf("flavor"))
    Assert.assertTrue(
        output.containsAll(
            listOf(
                "google-services.json",
                "src/release/google-services.json",
                "src/flavorRelease/google-services.json",
                "src/flavor/google-services.json",
                "src/flavor/release/google-services.json",
                "src/release/flavor/google-services.json")))
  }

  @Test
  fun testMultipleFlavors() {
    val output: List<String> =
        GoogleServicesPlugin.getJsonLocations("release", listOf("flavor", "test"))

    Assert.assertTrue(
        output.containsAll(
            listOf(
                "google-services.json",
                "src/release/google-services.json",
                "src/flavorRelease/google-services.json",
                "src/flavor/google-services.json",
                "src/flavor/release/google-services.json",
                "src/release/flavorTest/google-services.json",
                "src/flavorTest/google-services.json",
                "src/flavorTestRelease/google-services.json",
                "src/flavor/test/release/google-services.json",
                "src/flavor/testRelease/google-services.json")))
  }

  @Test
  fun testMultipleFlavorsWithCamelCase() {
    val output: List<String> =
        GoogleServicesPlugin.getJsonLocations("release", listOf("flavor", "testTest"))

    Assert.assertTrue(
        output.containsAll(
            listOf(
                "google-services.json",
                "src/release/google-services.json",
                "src/flavorRelease/google-services.json",
                "src/flavor/google-services.json",
                "src/flavor/release/google-services.json",
                "src/release/flavorTestTest/google-services.json",
                "src/flavorTestTest/google-services.json",
                "src/flavorTestTestRelease/google-services.json",
                "src/flavor/testTest/release/google-services.json",
                "src/flavor/testTestRelease/google-services.json")))
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
  //        Assert.assertEquals(TaskOutcome.SUCCESS,
  // gradleRunner.task(":app:assembleDebug")?.outcome)
  //        val actualResults = tempFolder.root
  //            .resolve("app/build/generated/res/google-services")
  //        actualResults.copyRecursively(expectedResults)
  //    }
}
