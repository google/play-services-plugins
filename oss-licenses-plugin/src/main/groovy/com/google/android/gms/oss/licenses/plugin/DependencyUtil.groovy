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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.AmbiguousVariantSelectionException
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.slf4j.LoggerFactory

/**
 * Collection of shared utility methods and constants for dependency resolution.
 */
class DependencyUtil {
    /**
     * An artifact that represents the absence of an AGP dependency list.
     */
    protected static final ArtifactInfo ABSENT_ARTIFACT =
            new ArtifactInfo("absent", "absent", "absent")

    protected static final String LOCAL_LIBRARY_VERSION = "unspecified"
    private static final String TEST_PREFIX = "test"
    private static final String ANDROID_TEST_PREFIX = "androidTest"
    private static final Set<String> TEST_COMPILE = ["testCompile",
                                                     "androidTestCompile"]

    private static final Set<String> PACKAGED_DEPENDENCIES_PREFIXES = ["compile",
                                                                       "implementation",
                                                                       "api"]

    private static final logger = LoggerFactory.getLogger(DependencyTask.class)


    /**
     * Returns the POM file associated with the supplied artifactInfo or null
     * if none was found.
     */
    static File resolvePomFileArtifact(Project project, ArtifactInfo artifactInfo) {
        def moduleComponentIdentifier =
                createModuleComponentIdentifier(artifactInfo)
        logger.info("Resolving POM file for $moduleComponentIdentifier licenses.")
        def components = project.getDependencies()
                .createArtifactResolutionQuery()
                .forComponents(moduleComponentIdentifier)
                .withArtifacts(MavenModule.class, MavenPomArtifact.class)
                .execute()
        if (components.resolvedComponents.isEmpty()) {
            logger.warn("$moduleComponentIdentifier has no POM file.")
            return null
        }

        def artifacts = components.resolvedComponents[0].getArtifacts(MavenPomArtifact.class)
        if (artifacts.isEmpty()) {
            logger.error("$moduleComponentIdentifier empty POM artifact list.")
            return null
        }
        if (!(artifacts[0] instanceof ResolvedArtifactResult)) {
            logger.error("$moduleComponentIdentifier unexpected type ${artifacts[0].class}")
            return null
        }
        return ((ResolvedArtifactResult) artifacts[0]).getFile()
    }

    private static ModuleComponentIdentifier createModuleComponentIdentifier(ArtifactInfo artifactInfo) {
        return new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(artifactInfo.group, artifactInfo.name), artifactInfo.version)
    }

    /**
     * Returns the library file of a resolved dependency matching artifactInfo
     * or null. Eagerly returns the first matching instance in the first
     * matching configuration. Skips unresolvable, unresolved, test, and
     * non-package dependency configurations.
     *
     * Will not cause an unresolved configuration to be resolved.
     */
    static File getLibraryFile(Project project, ArtifactInfo artifactInfo) {
        def configurationContainer = project.getConfigurations()
        for (Configuration configuration in configurationContainer) {
            if (shouldSkipConfiguration(configuration)) {
                continue
            }

            if (isNotResolved(configuration)) {
                logger.info("Configuration ${configuration.name} is not resolved, skipping.")
                continue
            }

            def resolvedDependencies = configuration.getResolvedConfiguration().getLenientConfiguration().allModuleDependencies
            return findLibraryFileInResolvedDependencies(resolvedDependencies, artifactInfo, Set.of())
        }
        logger.warn("No resolved configurations contained $artifactInfo")
        return null
    }

    private static File findLibraryFileInResolvedDependencies(
            Set<ResolvedDependency> resolvedDependencies,
            ArtifactInfo artifactInfo,
            Set<ResolvedDependency> visitedDependencies) {

        for (ResolvedDependency resolvedDependency in resolvedDependencies) {
            try {
                if (resolvedDependency.getModuleVersion() == LOCAL_LIBRARY_VERSION) {
                    /**
                     * Attempting to getAllModuleArtifacts on a local library project will result
                     * in AmbiguousVariantSelectionException as there are not enough criteria
                     * to match a specific variant of the library project. Instead we skip the
                     * the library project itself and enumerate its dependencies.
                     */
                    if (resolvedDependency in visitedDependencies) {
                        logger.info("Dependency cycle at ${resolvedDependency.name}")
                        continue
                    }
                    File childResult = findLibraryFileInResolvedDependencies(
                            resolvedDependency.getChildren(),
                            artifactInfo,
                            visitedDependencies + resolvedDependency
                    )
                    if (childResult != null) {
                        return childResult
                    }
                } else {
                    for (resolvedArtifact in resolvedDependency.getAllModuleArtifacts()) {
                        if (isMatchingArtifact(resolvedArtifact, artifactInfo)) {
                            return resolvedArtifact.file
                        }
                    }
                }
            } catch (AmbiguousVariantSelectionException exception) {
                logger.info("Failed to process ${resolvedDependency.name}", exception)
            }
        }
        return null
    }

    /**
     * Returns true for configurations that cannot be resolved in the newer
     * version of gradle API, are tests, or are not packaged dependencies.
     */
    private static boolean shouldSkipConfiguration(Configuration configuration) {
        (!canBeResolved(configuration)
                || isTest(configuration)
                || !isPackagedDependency(configuration))
    }

    /**
     * Checks if the configuration can be resolved. isCanBeResolved is added to
     * gradle Api since 3.3. For the previous version, all the configurations
     * can be resolved.
     * @param configuration
     * @return true if configuration can be resolved or the api is lower than
     * 3.3, otherwise false.
     */
    protected static boolean canBeResolved(Configuration configuration) {
        return configuration.metaClass.respondsTo(configuration,
                "isCanBeResolved") ? configuration.isCanBeResolved() : true
    }

    /**
     * Checks if the configuration is from test.
     * @param configuration
     * @return true if configuration is a test configuration or its parent
     * configurations are either testCompile or androidTestCompile, otherwise
     * false.
     */
    protected static boolean isTest(Configuration configuration) {
        boolean isTestConfiguration = (
                configuration.name.startsWith(TEST_PREFIX) ||
                        configuration.name.startsWith(ANDROID_TEST_PREFIX))
        configuration.hierarchy.each {
            isTestConfiguration |= TEST_COMPILE.contains(it.name)
        }
        return isTestConfiguration
    }

    /**
     * Checks if the configuration is for a packaged dependency (rather than e.g. a build or test time dependency)
     * @param configuration
     * @return true if the configuration is in the set of @link #BINARY_DEPENDENCIES
     */
    protected static boolean isPackagedDependency(Configuration configuration) {
        boolean isPackagedDependency = PACKAGED_DEPENDENCIES_PREFIXES.any {
            configuration.name.startsWith(it)
        }
        configuration.hierarchy.each {
            String configurationHierarchyName = it.name
            isPackagedDependency |= PACKAGED_DEPENDENCIES_PREFIXES.any {
                configurationHierarchyName.startsWith(it)
            }
        }

        return isPackagedDependency
    }

    private static boolean isNotResolved(Configuration configuration) {
        return configuration.state != Configuration.State.RESOLVED
    }

    private static boolean isMatchingArtifact(ResolvedArtifact resolvedArtifact, ArtifactInfo artifactInfo) {
        return (resolvedArtifact.moduleVersion.id.group == artifactInfo.group
                && resolvedArtifact.name == artifactInfo.name
                && resolvedArtifact.moduleVersion.id.version == artifactInfo.version)
    }
}
