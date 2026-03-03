/**
 * Copyright 2026 Google LLC
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

package com.google.android.gms.oss.licenses.plugin;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for dependency resolution logic in {@link DependencyUtil}.
 * 
 * Verifies that the plugin correctly identifies and maps various types of dependencies
 * (external, transitive, project-based) using standard Gradle APIs.
 */
public class DependencyResolutionTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Project rootProject;
    private Project appProject;
    private Project libProject;

    @Before
    public void setUp() throws IOException {
        File rootDir = temporaryFolder.newFolder("root");
        rootProject = ProjectBuilder.builder().withProjectDir(rootDir).withName("root").build();
        
        // Setup app project
        File appDir = new File(rootDir, "app");
        appDir.mkdirs();
        appProject = ProjectBuilder.builder().withParent(rootProject).withProjectDir(appDir).withName("app").build();
        appProject.getPlugins().apply("java-library");

        // Setup library project
        File libDir = new File(rootDir, "lib");
        libDir.mkdirs();
        libProject = ProjectBuilder.builder().withParent(rootProject).withProjectDir(libDir).withName("lib").build();
        libProject.getPlugins().apply("java-library");
        
        rootProject.getRepositories().mavenCentral();
        appProject.getRepositories().mavenCentral();
        libProject.getRepositories().mavenCentral();
    }

    @Test
    public void testComplexDependencyGraphResolution() throws IOException {
        // 1. Version Conflict Resolution: 
        // App wants Guava 33.0.0, Lib wants 32.0.0. Gradle should resolve to 33.0.0.
        appProject.getDependencies().add("implementation", "com.google.guava:guava:33.0.0-jre");
        libProject.getDependencies().add("implementation", "com.google.guava:guava:32.0.0-jre");
        
        // 2. Project Dependency:
        // App depends on local Lib project.
        appProject.getDependencies().add("implementation", libProject);
        
        // 3. Transitive Dependency via Project:
        // Lib pulls in Gson.
        libProject.getDependencies().add("implementation", "com.google.code.gson:gson:2.10.1");
        
        // 4. Scoped Dependencies:
        // compileOnly should be ignored by runtime resolution.
        appProject.getDependencies().add("compileOnly", "javax.servlet:servlet-api:2.5");
        // runtimeOnly should be included.
        appProject.getDependencies().add("runtimeOnly", "org.postgresql:postgresql:42.6.0");

        // Resolve the runtime classpath
        Configuration runtimeClasspath = appProject.getConfigurations().getByName("runtimeClasspath");
        runtimeClasspath.resolve();

        // Execute resolution logic
        Map<String, ArtifactFiles> artifactFiles = DependencyUtil.resolveArtifacts(appProject, runtimeClasspath);

        // Assertions
        // - Guava resolved to the higher version
        assertThat(artifactFiles).containsKey("com.google.guava:guava:33.0.0-jre");
        assertThat(artifactFiles).doesNotContainKey("com.google.guava:guava:32.0.0-jre");
        assertThat(artifactFiles.get("com.google.guava:guava:33.0.0-jre").getLibraryFile()).isNotNull();

        // - Gson resolved transitively via the lib project
        assertThat(artifactFiles).containsKey("com.google.code.gson:gson:2.10.1");
        assertThat(artifactFiles.get("com.google.code.gson:gson:2.10.1").getLibraryFile()).isNotNull();

        // - Runtime only dependency is present
        assertThat(artifactFiles).containsKey("org.postgresql:postgresql:42.6.0");
        assertThat(artifactFiles.get("org.postgresql:postgresql:42.6.0").getLibraryFile()).isNotNull();

        // - Compile only dependency is absent
        assertThat(artifactFiles).doesNotContainKey("javax.servlet:servlet-api:2.5");

        // - Local project itself is skipped (we only extract licenses for external modules)
        assertThat(artifactFiles).doesNotContainKey("root:lib:unspecified");
    }

    @Test
    public void testPomResolution() throws IOException {
        appProject.getDependencies().add("implementation", "com.google.guava:guava:33.0.0-jre");
        Configuration runtimeClasspath = appProject.getConfigurations().getByName("runtimeClasspath");
        runtimeClasspath.resolve();

        Map<String, ArtifactFiles> artifactFiles = DependencyUtil.resolveArtifacts(appProject, runtimeClasspath);

        assertThat(artifactFiles).containsKey("com.google.guava:guava:33.0.0-jre");
        assertThat(artifactFiles.get("com.google.guava:guava:33.0.0-jre").getPomFile()).isNotNull();
        assertThat(artifactFiles.get("com.google.guava:guava:33.0.0-jre").getPomFile().getName()).endsWith(".pom");
    }
}
