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
import org.junit.Rule;
import org.junit.Test
import org.junit.rules.TemporaryFolder;
import java.io.File

class GoogleServicesPluginTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testResGeneration() {
        val expectedResults = File("src/test/testData/project1-expected")
        File("src/test/testData/project1").copyRecursively(tempFolder.root)
        val gradleRunner = GradleRunner.create()
            .withProjectDir(tempFolder.root)
            .forwardOutput()
            .withArguments(
                "assembleDebug",
                /*
                GradleRunner.withPluginClasspath() won't work, because it puts the
                plugin in an isolated classloader so it can't interact with AGP.
                Instead we're passing the path to the built Maven repo.
                 */
                "-PpluginRepo=${File("build/repo").absolutePath}"
            )
            .build()

        Assert.assertEquals(TaskOutcome.SUCCESS, gradleRunner.task(":app:assembleDebug")?.outcome)
        val actualResults = tempFolder.root
            .resolve("app/build/generated/res/google-services")
        actualResults.walk().forEach {
                if (!it.isFile) {
                    return@forEach
                }
                val relativePath = it.relativeTo(actualResults)
                Assert.assertEquals(
                    "File $relativePath doesn't match expected golden value.",
                    expectedResults.resolve(relativePath).readText(),
                    it.readText()
                )
            }
    }


//    /* Uncomment and run this test to regenerate the
//    XML files that will be used for other tests. */
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
