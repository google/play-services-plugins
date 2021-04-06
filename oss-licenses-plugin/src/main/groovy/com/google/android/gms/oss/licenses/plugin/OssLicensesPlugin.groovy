/**
 * Copyright 2018 Google LLC
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

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Plugin
import org.gradle.api.Project

class OssLicensesPlugin implements Plugin<Project> {
    void apply(Project project) {
        def getDependencies = project.tasks.create("getDependencies",
                DependencyTask)
        def dependencyOutput = new File(project.buildDir,
            "generated/third_party_licenses")
        def generatedJson = new File(dependencyOutput, "dependencies.json")
        getDependencies.setProject(project)
        getDependencies.outputDir = dependencyOutput
        getDependencies.outputFile = generatedJson

        def resourceOutput = new File(dependencyOutput, "/res")
        def outputDir = new File(resourceOutput, "/raw")
        def licensesFile = new File(outputDir, "third_party_licenses")
        def licensesMetadataFile = new File(outputDir,
                "third_party_license_metadata")
        def licenseTask = project.tasks.create("generateLicenses", LicensesTask)

        licenseTask.dependenciesJson = generatedJson
        licenseTask.outputDir = outputDir
        licenseTask.licenses = licensesFile
        licenseTask.licensesMetadata = licensesMetadataFile

        licenseTask.inputs.file(generatedJson)
        licenseTask.outputs.dir(outputDir)
        licenseTask.outputs.files(licensesFile, licensesMetadataFile)

        licenseTask.dependsOn(getDependencies)

        project.android.applicationVariants.all { BaseVariant variant ->
            // This is necessary for backwards compatibility with versions of gradle that do not support
            // this new API.
            if (variant.hasProperty("preBuildProvider")) {
                variant.preBuildProvider.configure { dependsOn(licenseTask) }
            } else {
                //noinspection GrDeprecatedAPIUsage
                variant.preBuild.dependsOn(licenseTask)
            }

            // This is necessary for backwards compatibility with versions of gradle that do not support
            // this new API.
            if (variant.respondsTo("registerGeneratedResFolders")) {
                licenseTask.ext.generatedResFolders = project.files(resourceOutput).builtBy(licenseTask)
                variant.registerGeneratedResFolders(licenseTask.generatedResFolders)

                if (variant.hasProperty("mergeResourcesProvider")) {
                    variant.mergeResourcesProvider.configure { dependsOn(licenseTask) }
                } else {
                    //noinspection GrDeprecatedAPIUsage
                    variant.mergeResources.dependsOn(licenseTask)
                }
            } else {
                //noinspection GrDeprecatedAPIUsage
                variant.registerResGeneratingTask(licenseTask, resourceOutput)
            }
        }

        def cleanupTask = project.tasks.create("licensesCleanUp",
                LicensesCleanUpTask)
        cleanupTask.dependencyFile = generatedJson
        cleanupTask.dependencyDir = dependencyOutput
        cleanupTask.licensesFile = licensesFile
        cleanupTask.metadataFile = licensesMetadataFile
        cleanupTask.licensesDir = outputDir

        project.tasks.findByName("clean").dependsOn(cleanupTask)
    }
}
