/**
 * Copyright 2018-2026 Google LLC
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
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/**
 * Main entry point for the OSS Licenses Gradle Plugin.
 *
 * The plugin architecture follows a three-task workflow for each variant:
 * 1. DependencyTask: Converts AGP's internal dependency protobuf into a simplified JSON.
 * 2. LicensesTask: Resolves licenses from POM files and Google Service artifacts.
 * 3. LicensesCleanUpTask: Cleans up generated directories as part of the clean
 */
class OssLicensesPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.plugins.configureEach { plugin ->
            if (plugin instanceof AppPlugin) {
                def androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension)
                androidComponents.onVariants(androidComponents.selector().all()) { variant ->
                    configureLicenceTasks(project, variant)
                }
            }
        }
    }

    /**
     * Configures the license generation tasks for a specific Android variant.
     *
     * To support Gradle's Configuration Cache, all mappings from GAV coordinates to
     * physical files (POMs and Library artifacts) are resolved during the configuration phase
     * and passed to the execution phase as lazy Provider properties.
     */
    private static void configureLicenceTasks(Project project, ApplicationVariant variant) {
        Provider<Directory> baseDir = project.layout.buildDirectory.dir("generated/third_party_licenses/${variant.name}")
        
        // Task 1: Dependency Identification
        // This task reads the AGP METADATA_LIBRARY_DEPENDENCIES_REPORT protobuf.
        def dependenciesJson =  baseDir.map { it.file("dependencies.json") }
        TaskProvider<DependencyTask> dependencyTask = project.tasks.register(
                "${variant.name}OssDependencyTask",
                DependencyTask.class) {
            it.dependenciesJson.set(dependenciesJson)
            it.libraryDependenciesReport.set(variant.artifacts.get(SingleArtifact.METADATA_LIBRARY_DEPENDENCIES_REPORT.INSTANCE))
        }
        project.logger.debug("Registered task ${dependencyTask.name}")

        // Task 2: License Extraction
        // This task parses POMs and library files to extract license text.
        TaskProvider<LicensesTask> licenseTask = project.tasks.register(
                "${variant.name}OssLicensesTask",
                LicensesTask.class) {
            it.dependenciesJson.set(dependencyTask.flatMap { it.dependenciesJson })

            it.artifactFiles.set(project.provider {
                DependencyUtil.resolveArtifacts(project, variant.runtimeConfiguration)
            })
        }
        project.logger.debug("Registered task ${licenseTask.name}")
        
        // Register the LicensesTask output as a generated resource folder for AGP.
        variant.sources.res.addGeneratedSourceDirectory(licenseTask, LicensesTask::getGeneratedDirectory)

        // Task 3: Cleanup
        // Ensures generated license files are deleted when running the clean task.
        TaskProvider<LicensesCleanUpTask> cleanupTask = project.tasks.register(
                "${variant.name}OssLicensesCleanUp",
                LicensesCleanUpTask.class) {
            it.generatedDirectory.set(baseDir)
        }
        project.logger.debug("Registered task ${cleanupTask.name}")

        project.tasks.named("clean").configure {
            it.dependsOn(cleanupTask)
        }
    }

}
