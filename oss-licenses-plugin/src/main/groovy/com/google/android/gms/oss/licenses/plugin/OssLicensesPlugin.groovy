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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.slf4j.LoggerFactory

class OssLicensesPlugin implements Plugin<Project> {

    private static final logger = LoggerFactory.getLogger(DependencyTask.class)

    void apply(Project project) {
        def variantTolicenseTaskMap = new HashMap<String, LicensesTask>()
        project.androidComponents {
            onVariants(selector().all(), { variant ->
                def baseDir = new File(project.buildDir,
                        "generated/third_party_licenses/${variant.name}")
                def dependenciesJson = new File(baseDir, "dependencies.json")

                def dependencyTask = project.tasks.register(
                        "${variant.name}OssDependencyTask",
                        DependencyTask.class) {
                    it.dependenciesJson.set(dependenciesJson)
                    it.libraryDependenciesReport.set(variant.artifacts.get(SingleArtifact.METADATA_LIBRARY_DEPENDENCIES_REPORT.INSTANCE))
                }.get()
                logger.debug("Created task ${dependencyTask.name}")

                def resourceBaseDir = new File(baseDir, "/res")
                def rawResourceDir = new File(resourceBaseDir, "/raw")
                def licensesFile = new File(rawResourceDir, "third_party_licenses")
                def licensesMetadataFile = new File(rawResourceDir,
                        "third_party_license_metadata")

                def licenseTask = project.tasks.register(
                        "${variant.name}OssLicensesTask",
                        LicensesTask.class) {
                    it.dependenciesJson.set(dependencyTask.dependenciesJson)
                    it.rawResourceDir = rawResourceDir
                    it.licenses = licensesFile
                    it.licensesMetadata = licensesMetadataFile
                }.get()
                logger.debug("Created task ${licenseTask.name}")

                variantTolicenseTaskMap[variant.name] = licenseTask

                def cleanupTask = project.tasks.register(
                        "${variant.name}OssLicensesCleanUp",
                        LicensesCleanUpTask.class) {
                    it.dependenciesJson = dependenciesJson
                    it.dependencyDir = baseDir
                    it.licensesFile = licensesFile
                    it.metadataFile = licensesMetadataFile
                    it.licensesDir = rawResourceDir
                }.get()
                logger.debug("Created task ${cleanupTask.name}")

                project.tasks.findByName("clean").dependsOn(cleanupTask)
            })
        }

        // TODO: Switch to new Variant API when API is ready and before
        //  BaseVariant is removed in 8.0
        project.android.applicationVariants.all { BaseVariant variant ->
            def licenseTask = variantTolicenseTaskMap[variant.name]
            if (licenseTask == null) {
                return
            }
            def generatedResFolder = project.files(licenseTask.rawResourceDir.parentFile).builtBy(licenseTask)
            variant.registerGeneratedResFolders(generatedResFolder)
        }
    }
}
