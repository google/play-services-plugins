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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

/**
 * Collection of shared utility methods and constants for dependency resolution.
 * 
 * These methods are designed to be called during the Gradle Configuration phase
 * to provide pre-resolved dependency information to tasks, supporting 
 * Configuration Cache compatibility.
 */
class DependencyUtil {
    /**
     * An artifact that represents the absence of an AGP dependency list.
     */
    protected static final ArtifactInfo ABSENT_ARTIFACT =
            new ArtifactInfo("absent", "absent", "absent")

    protected static final String LOCAL_LIBRARY_VERSION = "unspecified"

    /**
     * Resolves both POM files and physical library files (JAR/AAR) for all external 
     * components in the provided configuration.
     * 
     * @param project The Gradle project used to create the resolution query.
     * @param runtimeConfiguration The configuration whose dependencies should be resolved.
     * @return A map of GAV coordinates to their resolved ArtifactFiles.
     */
    static Map<String, ArtifactFiles> resolveArtifacts(Project project, Configuration runtimeConfiguration) {
        // We create an ArtifactView to gather the component identifiers and library files.
        // We specifically target external Maven dependencies (ModuleComponentIdentifiers).
        def runtimeArtifactView = runtimeConfiguration.incoming.artifactView {
            it.componentFilter { id -> id instanceof ModuleComponentIdentifier }
        }
        
        def artifactsMap = [:]
        
        // 1. Gather library files directly from the view
        runtimeArtifactView.artifacts.each { artifact ->
            def id = artifact.id.componentIdentifier
            if (id instanceof ModuleComponentIdentifier) {
                String key = "${id.group}:${id.module}:${id.version}".toString()
                artifactsMap[key] = new ArtifactFiles(null, artifact.file)
            }
        }

        // 2. Fetch corresponding POM files using ArtifactResolutionQuery
        def componentIds = runtimeArtifactView.artifacts.collect { it.id.componentIdentifier }
        
        if (!componentIds.isEmpty()) {
            def result = project.dependencies.createArtifactResolutionQuery()
                    .forComponents(componentIds)
                    .withArtifacts(MavenModule, MavenPomArtifact)
                    .execute()

            result.resolvedComponents.each { component ->
                component.getArtifacts(MavenPomArtifact).each { artifact ->
                    if (artifact instanceof ResolvedArtifactResult) {
                        def id = component.id
                        String key = "${id.group}:${id.module}:${id.version}".toString()
                        
                        // Update the existing entry with the POM file
                        if (artifactsMap.containsKey(key)) {
                            artifactsMap[key].pomFile = artifact.file
                        } else {
                            artifactsMap[key] = new ArtifactFiles(artifact.file, null)
                        }
                    }
                }
            }
        }
        
        return artifactsMap
    }
}
