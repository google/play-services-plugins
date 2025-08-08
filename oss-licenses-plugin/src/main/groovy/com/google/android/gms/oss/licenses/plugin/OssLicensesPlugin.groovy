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
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

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

    private static void configureLicenceTasks(Project project, ApplicationVariant variant) {
        Provider<Directory> baseDir = project.layout.buildDirectory.dir("generated/third_party_licenses/${variant.name}")
        def dependenciesJson =  baseDir.map { it.file("dependencies.json") }
        TaskProvider<DependencyTask> dependencyTask = project.tasks.register(
                "${variant.name}OssDependencyTask",
                DependencyTask.class) {
            it.dependenciesJson.set(dependenciesJson)
            it.libraryDependenciesReport.set(variant.artifacts.get(SingleArtifact.METADATA_LIBRARY_DEPENDENCIES_REPORT.INSTANCE))
        }
        project.logger.debug("Registered task ${dependencyTask.name}")

        TaskProvider<LicensesTask> licenseTask = project.tasks.register(
                "${variant.name}OssLicensesTask",
                LicensesTask.class) {
            markNotCompatibleWithConfigurationCache(it)
            it.dependenciesJson.set(dependencyTask.flatMap { it.dependenciesJson })
        }
        project.logger.debug("Registered task ${licenseTask.name}")
        variant.sources.resources.addGeneratedSourceDirectory(licenseTask, LicensesTask::getGeneratedDirectory)

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

    private static void markNotCompatibleWithConfigurationCache(Task it) {
        // Configuration cache method incubating in Gradle 7.4
        if (it.metaClass.respondsTo(it, "notCompatibleWithConfigurationCache", String)) {
            it.notCompatibleWithConfigurationCache(
                    "Requires Project instance to resolve POM files during " +
                            " task execution, but depends on another Task to " +
                            " create the artifact list. Without the list we " +
                            " cannot enumerate POM files during configuration."
            )
        }
    }
}
