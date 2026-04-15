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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

/**
 * Main entry point for the OSS Licenses Gradle Plugin.
 *
 * Two-task workflow (per variant)
 * 1. {@link DependencyTask} - converts AGP's internal dependency protobuf into a simplified JSON.
 * 2. {@link LicensesTask} - resolves licenses from POM files and Google Play Services artifacts.
 *
 * Configuration Cache & lazy resolution
 * Gradle's Configuration Cache serializes the task graph after the configuration phase,
 * then replays it on subsequent builds without re-executing any configuration-phase code.
 * This means:
 *
 * - No {@code Project} references in task state: {@code Project} is not serializable.
 *   Any closure that captures {@code project} (even indirectly, e.g. via {@code project.provider {}})
 *   will fail serialization. Instead, extract what you need (like {@code project.dependencies}) into
 *   a local variable before entering any lazy closure.
 * - No eager dependency resolution: Calling {@code configuration.resolve()} or iterating
 *   {@code configuration.incoming.artifacts} during configuration time forces Gradle to resolve
 *   the full dependency graph immediately. This is slow, prevents Gradle from parallelizing
 *   resolution across projects, and triggers the "Configuration X was resolved during
 *   configuration time" warning which becomes a hard error under strict CC mode. Instead, use
 *   {@code .resolvedArtifacts} which returns a {@code Provider} that Gradle resolves only when
 *   a task actually needs the value.
 * - Use {@code Provider.map {}} to chain transformations lazily: The {@code .map {}}
 *   lambda runs only when the provider value is first requested (at task execution time on a
 *   cache miss, or not at all on a cache hit where Gradle replays the serialized result).
 *   This keeps all dependency resolution work out of the configuration phase.
 */
class OssLicensesPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.plugins.configureEach { plugin ->
            if (plugin instanceof AppPlugin) {
                def androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension)
                androidComponents.onVariants(androidComponents.selector().all()) { variant -> configureLicenseTasks(project, variant)
                }
            }
        }
    }

    private static void configureLicenseTasks(Project project, ApplicationVariant variant) {
        Provider<Directory> baseDir = project.layout.buildDirectory.dir("generated/third_party_licenses/${variant.name}")

        // --- Lazy artifact providers (see class Javadoc for why this matters) ---
        //
        // .resolvedArtifacts returns a Provider<Set<ResolvedArtifactResult>>. Critically, this
        // does NOT trigger dependency resolution during configuration (i.e. Android Studio sync).
        // Resolution is deferred until a task actually reads the provider's value at execution
        // time. On the first build, Gradle resolves the artifacts and serializes the result into
        // the configuration cache. On subsequent builds, the cached result is used directly — no
        // resolution happens.
        //
        // The componentFilter restricts to external Maven dependencies (ModuleComponentIdentifier).
        // Local project dependencies and file-based dependencies are excluded because they have
        // no POM metadata or bundled license data.
        def libraryArtifacts = variant.runtimeConfiguration.incoming.artifactView {
            componentFilter { it instanceof ModuleComponentIdentifier }
        }.artifacts.resolvedArtifacts

        // Extract DependencyHandler into a local var BEFORE entering any lazy closures below.
        // If we wrote `project.dependencies` inside a .map {} lambda, the lambda would close
        // over the `project` object. Project is NOT serializable, so Configuration Cache would
        // fail with: "cannot serialize object of type DefaultProject". By capturing just the
        // DependencyHandler here, the lambda only closes over the handler (which Gradle can
        // serialize), keeping the task graph CC-safe.
        def depHandler = project.dependencies

        // GAV → library file (JAR/AAR), for extracting bundled license data from
        // Google Play Services artifacts. Used by DependencyTask to calculate hashes for snapshots,
        // and by LicensesTask to extract license files.
        //
        // The .map {} lambda runs lazily — only when a task actually executes and reads this property.
        // On builds with configuration cache hits, Gradle skips the lambda entirely and uses
        // the Map<String, File> it serialized from the previous run.
        def libraryFilesByGavProvider = libraryArtifacts.map { artifacts ->
            artifacts.collectEntries { a ->
                def id = (ModuleComponentIdentifier) a.id.componentIdentifier
                ["${id.group}:${id.module}:${id.version}".toString(), a.file]
            }
        }

        // Task 1: Dependency Identification
        // Converts AGP's METADATA_LIBRARY_DEPENDENCIES_REPORT protobuf into a stable JSON list.
        // libraryDependenciesReport is @Optional — debug variants don't get the report, so the
        // task writes a sentinel entry instead.
        // This task also calculates hashes for SNAPSHOT versions to ensure that LicensesTask
        // re-runs if a snapshot is re-published.
        def dependenciesJson = baseDir.map { it.file("dependencies.json") }
        TaskProvider<DependencyTask> dependencyTask = project.tasks.register("${variant.name}OssDependencyTask",
                DependencyTask.class) {
            it.dependenciesJson.set(dependenciesJson)
            it.libraryDependenciesReport.set(variant.artifacts.get(SingleArtifact.METADATA_LIBRARY_DEPENDENCIES_REPORT.INSTANCE))
            it.libraryFilesByGav.set(libraryFilesByGavProvider)
        }
        project.logger.debug("Registered task ${dependencyTask.name}")

        // Task 2: License Extraction
        // Parses POM files and Google Play Services AARs to produce the final
        // third_party_licenses / third_party_license_metadata raw resource files.
        TaskProvider<LicensesTask> licenseTask = project.tasks.register("${variant.name}OssLicensesTask",
                LicensesTask.class) {
            it.dependenciesJson.set(dependencyTask.flatMap { it.dependenciesJson })

            it.libraryFilesByGav.set(libraryFilesByGavProvider)

            // GAV → POM file, for reading <licenses> URLs from Maven metadata.
            //
            // Why createArtifactResolutionQuery() instead of another ArtifactView?
            // ArtifactView selects variant-published artifacts (JARs, AARs, etc.) — the things
            // that appear on a classpath. POM files are Maven *metadata*, not published variants,
            // so ArtifactView cannot select them. createArtifactResolutionQuery() is Gradle's
            // dedicated API for fetching auxiliary metadata artifacts like POMs and Ivy descriptors.
            //
            // This entire block is inside a .map {} on the libraryArtifacts provider, so it
            // only executes at task execution time (not configuration time). The depHandler
            // variable was captured above specifically to avoid closing over `project` here.
            it.pomFilesByGav.set(libraryArtifacts.map { artifacts ->
                def componentIds = artifacts.collect { it.id.componentIdentifier }
                // Debug variants have no external dependencies (AGP doesn't provide the
                // dependency report), so the artifact set is empty and we return early.
                if (componentIds.isEmpty()) return [:]
                depHandler.createArtifactResolutionQuery()
                        .forComponents(componentIds)
                        .withArtifacts(MavenModule, MavenPomArtifact)
                        .execute()
                        .resolvedComponents
                        .collectEntries { component ->
                            def pom = component.getArtifacts(MavenPomArtifact)
                                    .find { it instanceof ResolvedArtifactResult }
                            def id = component.id
                            def key = "${id.group}:${id.module}:${id.version}".toString()
                            pom ? [(key): pom.file] : [:]
                        }
            })
        }
        project.logger.debug("Registered task ${licenseTask.name}")
        variant.sources.res.addGeneratedSourceDirectory(licenseTask, LicensesTask::getGeneratedDirectory)
    }
}
