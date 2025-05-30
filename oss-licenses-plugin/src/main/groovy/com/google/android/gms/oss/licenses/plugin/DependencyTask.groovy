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

import com.android.tools.build.libraries.metadata.AppDependencies
import groovy.json.JsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.slf4j.LoggerFactory

import java.util.stream.Collectors

import static com.android.tools.build.libraries.metadata.Library.LibraryOneofCase.MAVEN_LIBRARY

/**
 * Converts the AppDependencies protobuf file provided by the Android Gradle
 * Plugin into a JSON format that will be consumed by the {@link LicensesTask}.
 *
 * If the protobuf is not present (e.g. debug variants) it writes a single
 * dependency on the {@link DependencyUtil#ABSENT_ARTIFACT}.
 */
abstract class DependencyTask extends DefaultTask {
    private static final logger = LoggerFactory.getLogger(DependencyTask.class)

    @OutputFile
    abstract RegularFileProperty getDependenciesJson()

    @InputFile
    @org.gradle.api.tasks.Optional
    abstract RegularFileProperty getLibraryDependenciesReport()

    @TaskAction
    void action() {
        def artifactInfoSet = loadArtifactInfo()

        File outputFile = dependenciesJson.asFile.get()

        initOutput(outputFile.parentFile)
        outputFile.newWriter().withWriter {
            it.write(new JsonBuilder(artifactInfoSet).toPrettyString())
        }
    }

    private Set<ArtifactInfo> loadArtifactInfo() {
        if (!libraryDependenciesReport.isPresent()) {
            logger.info("$name not provided with AppDependencies proto file.")
            return [DependencyUtil.ABSENT_ARTIFACT]
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

    private static List<ArtifactInfo> convertDependenciesToArtifactInfo(
            AppDependencies appDependencies
    ) {
        return appDependencies.libraryList.stream()
                .filter { it.libraryOneofCase == MAVEN_LIBRARY }
                .map { library ->
                    return new ArtifactInfo(
                            library.mavenLibrary.groupId,
                            library.mavenLibrary.artifactId,
                            library.mavenLibrary.version
                    )
                }
                .sorted(Comparator.comparing { it.toString() })
                .collect(Collectors.toUnmodifiableList())
    }

    private static void initOutput(File outputDir) {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
    }
}
