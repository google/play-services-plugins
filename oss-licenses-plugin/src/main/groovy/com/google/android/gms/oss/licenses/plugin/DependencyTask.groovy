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

import com.android.tools.build.libraries.metadata.AppDependencies
import groovy.json.JsonBuilder
import groovy.transform.PackageScope
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.MapProperty
import org.slf4j.LoggerFactory

import java.util.stream.Collectors

import static com.android.tools.build.libraries.metadata.Library.LibraryOneofCase.MAVEN_LIBRARY

/**
 * Converts the AppDependencies protobuf file provided by the Android Gradle
 * Plugin into a JSON format that will be consumed by the {@link LicensesTask}.
 *
 * If the protobuf is not present (e.g. debug variants) it writes a single
 * dependency on the {@link #ABSENT_ARTIFACT}.
 *
 * To support active development with SNAPSHOT dependencies, pre-computed hashes
 * of SNAPSHOT artifacts are provided via {@link #getSnapshotHashes()}. These are
 * tracked as {@code @Input} so Gradle detects when a re-published SNAPSHOT has
 * different content, triggering re-execution and propagating the change to
 * {@link LicensesTask}.
 */
@CacheableTask
abstract class DependencyTask extends DefaultTask {
    private static final logger = LoggerFactory.getLogger(DependencyTask.class)

    // Sentinel written to the JSON when AGP does not provide a dependency report (e.g. debug
    // variants). LicensesTask detects this and renders a placeholder message instead of licenses.
    @PackageScope
    static final ArtifactInfo ABSENT_ARTIFACT =
            new ArtifactInfo("absent", "absent", "absent")

    @OutputFile
    abstract RegularFileProperty getDependenciesJson()

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    abstract RegularFileProperty getLibraryDependenciesReport()

    /**
     * Pre-computed SHA-256 hashes for SNAPSHOT artifacts, keyed by GAV coordinate.
     * Computed lazily in {@link OssLicensesPlugin} from the resolved artifact files.
     *
     * This is an {@code @Input} so Gradle tracks the hash values for up-to-date checks.
     * When a SNAPSHOT is re-published with different content, its hash changes, which
     * causes this task to re-execute and produce an updated JSON report — in turn
     * triggering {@link LicensesTask} to re-run.
     */
    @Input
    @Optional
    abstract MapProperty<String, String> getSnapshotHashes()

    @TaskAction
    void action() {
        def artifactInfoSet = loadArtifactInfo()

        File outputFile = dependenciesJson.asFile.get()

        initOutput(outputFile.parentFile)
        outputFile.newWriter().withWriter {
            JsonBuilder json = new JsonBuilder()
            json.call(artifactInfoSet) { ArtifactInfo info ->
                // ensure that order of json entries is consistent to have reproducible output json
                group info.group
                name info.name
                version info.version
                if (info.hash != null) {
                    hash info.hash
                }
            }
            it.write(json.toPrettyString())
        }
    }

    private Set<ArtifactInfo> loadArtifactInfo() {
        if (!libraryDependenciesReport.isPresent()) {
            logger.info("$name not provided with AppDependencies proto file.")
            return [ABSENT_ARTIFACT]
        }

        AppDependencies appDependencies = loadDependenciesFile()

        return convertDependenciesToArtifactInfo(appDependencies)
    }

    private AppDependencies loadDependenciesFile() {
        File dependenciesFile = libraryDependenciesReport.asFile.get()
        return dependenciesFile.withInputStream {
            AppDependencies.parseFrom(it)
        } as AppDependencies
    }

    private Set<ArtifactInfo> convertDependenciesToArtifactInfo(
            AppDependencies appDependencies
    ) {
        Map<String, String> hashes = snapshotHashes.getOrElse([:])

        return appDependencies.libraryList.stream()
                .filter { it.libraryOneofCase == MAVEN_LIBRARY }
                .sorted { o1, o2 ->
                    // ensure the set is sorted to have reproducible output json
                    int groupComparison = o1.mavenLibrary.groupId <=> o2.mavenLibrary.groupId
                    if (groupComparison == 0) {
                        o1.mavenLibrary.artifactId <=> o2.mavenLibrary.artifactId
                    } else {
                        groupComparison
                    }
                }
                .map { library ->
                    String group = library.mavenLibrary.groupId
                    String name = library.mavenLibrary.artifactId
                    String version = library.mavenLibrary.version
                    String gav = "$group:$name:$version".toString()
                    String hash = hashes.get(gav)

                    return new ArtifactInfo(group, name, version, hash)
                }.collect(Collectors.toCollection(LinkedHashSet::new))
    }

    private static void initOutput(File outputDir) {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
    }
}
