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

package com.google.android.gms.oss.licenses.plugin;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DependencyTask}
 */
@RunWith(JUnit4.class)
public class DependencyTaskTest {
    private DependencyTask dependencyTask;

    @Before
    public void setUp() {
        Project project = ProjectBuilder.builder().build();
        dependencyTask = project.getTasks().create("getDependency", DependencyTask.class);
    }

    @Test
    public void testCheckArtifactSet_missingSet() {
        File dependencies = new File("src/test/resources", "testDependency.json");
        String[] artifactSet =
                new String[]{"dependencies/groupA/deps1.txt", "dependencies/groupB/abc/deps2.txt"};
        dependencyTask.artifactSet = new HashSet<>(Arrays.asList(artifactSet));

        assertFalse(dependencyTask.checkArtifactSet(dependencies));
    }

    @Test
    public void testCheckArtifactSet_correctSet() {
        File dependencies = new File("src/test/resources", "testDependency.json");
        String[] artifactSet =
                new String[]{
                        "dependencies/groupA/deps1.txt",
                        "dependencies/groupB/abc/deps2.txt",
                        "src/test/resources/dependencies/groupC/deps3.txt",
                        "src/test/resources/dependencies/groupD/deps4.txt"
                };
        dependencyTask.artifactSet = new HashSet<>(Arrays.asList(artifactSet));
        assertTrue(dependencyTask.checkArtifactSet(dependencies));
    }

    @Test
    public void testCheckArtifactSet_addMoreSet() {
        File dependencies = new File("src/test/resources", "testDependency.json");
        String[] artifactSet =
                new String[]{
                        "dependencies/groupA/deps1.txt",
                        "dependencies/groupB/abc/deps2.txt",
                        "src/test/resources/dependencies/groupC/deps3.txt",
                        "src/test/resources/dependencies/groupD/deps4.txt",
                        "dependencies/groupE/deps5.txt"
                };
        dependencyTask.artifactSet = new HashSet<>(Arrays.asList(artifactSet));
        assertFalse(dependencyTask.checkArtifactSet(dependencies));
    }

    @Test
    public void testCheckArtifactSet_replaceSet() {
        File dependencies = new File("src/test/resources", "testDependency.json");
        String[] artifactSet =
                new String[]{
                        "dependencies/groupA/deps1.txt",
                        "dependencies/groupB/abc/deps2.txt",
                        "src/test/resources/dependencies/groupC/deps3.txt",
                        "dependencies/groupE/deps5.txt"
                };
        dependencyTask.artifactSet = new HashSet<>(Arrays.asList(artifactSet));
        assertFalse(dependencyTask.checkArtifactSet(dependencies));
    }

    @Test
    public void testGetResolvedArtifacts_cannotResolve() {
        Configuration configuration = mock(Configuration.class);
        when(configuration.isCanBeResolved()).thenReturn(false);

        assertThat(dependencyTask.getResolvedArtifacts(configuration), is(nullValue()));
    }

    @Test
    public void testGetResolvedArtifacts_isTest() {
        Configuration configuration = mock(Configuration.class);
        when(configuration.getName()).thenReturn("testCompile");
        when(configuration.isCanBeResolved()).thenReturn(true);

        assertThat(dependencyTask.getResolvedArtifacts(configuration), is(nullValue()));
    }

    @Test
    public void testGetResolvedArtifacts_ResolveException() {
        ResolvedConfiguration resolvedConfiguration = mock(ResolvedConfiguration.class);
        when(resolvedConfiguration.getResolvedArtifacts()).thenThrow(ResolveException.class);

        Configuration configuration = mock(Configuration.class);
        when(configuration.getName()).thenReturn("compile");
        when(configuration.isCanBeResolved()).thenReturn(true);
        when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

        assertThat(dependencyTask.getResolvedArtifacts(configuration), is(nullValue()));
    }

    @Test
    public void testGetResolvedArtifacts_returnArtifact() {
        Set<ResolvedArtifact> artifactSet = (Set<ResolvedArtifact>) mock(Set.class);
        ResolvedConfiguration resolvedConfiguration = mock(ResolvedConfiguration.class);
        when(resolvedConfiguration.getResolvedArtifacts()).thenReturn(artifactSet);

        Configuration configuration = mock(Configuration.class);
        when(configuration.getName()).thenReturn("compile");
        when(configuration.isCanBeResolved()).thenReturn(true);
        when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

        assertThat(dependencyTask.getResolvedArtifacts(configuration), is(artifactSet));
    }

    @Test
    public void testAddArtifacts() {
        ResolvedConfiguration resolvedConfiguration = spy(ResolvedConfiguration.class);
        Set<ResolvedArtifact> artifacts = preppareArtifactSet(3);
        when(resolvedConfiguration.getResolvedArtifacts()).thenReturn(artifacts);

        Configuration configuration = mock(Configuration.class);
        when(configuration.isCanBeResolved()).thenReturn(true);
        when(configuration.getName()).thenReturn("compile");
        when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

        dependencyTask.addArtifacts(artifacts);
        assertThat(dependencyTask.artifactInfos.size(), is(3));
    }

    @Test
    public void testAddArtifacts_willNotAddDuplicate() {
        ResolvedConfiguration resolvedConfiguration = spy(ResolvedConfiguration.class);
        Set<ResolvedArtifact> artifacts = preppareArtifactSet(2);
        when(resolvedConfiguration.getResolvedArtifacts()).thenReturn(artifacts);

        Configuration configuration = mock(Configuration.class);
        when(configuration.isCanBeResolved()).thenReturn(true);
        when(configuration.getName()).thenReturn("compile");
        when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

        String[] keySets = new String[]{"location1", "location2"};
        dependencyTask.artifactSet = new HashSet<>(Arrays.asList(keySets));
        dependencyTask.addArtifacts(artifacts);

        assertThat(dependencyTask.artifactInfos.size(), is(1));
    }

    @Test
    public void testCanBeResolved_isTrue() {
        Configuration configuration = mock(Configuration.class);
        when(configuration.isCanBeResolved()).thenReturn(true);

        assertTrue(dependencyTask.canBeResolved(configuration));
    }

    @Test
    public void testCanBeResolved_isFalse() {
        Configuration configuration = mock(Configuration.class);
        when(configuration.isCanBeResolved()).thenReturn(false);

        assertFalse(dependencyTask.canBeResolved(configuration));
    }

    @Test
    public void testIsTest_isNotTest() {
        Configuration configuration = mock(Configuration.class);
        when(configuration.getName()).thenReturn("random");

        assertFalse(dependencyTask.isTest(configuration));
    }

    @Test
    public void testIsTest_isTestCompile() {
        Configuration configuration = mock(Configuration.class);
        when(configuration.getName()).thenReturn("testCompile");

        assertTrue(dependencyTask.isTest(configuration));
    }

    @Test
    public void testIsTest_isAndroidTestCompile() {
        Configuration configuration = mock(Configuration.class);
        when(configuration.getName()).thenReturn("androidTestCompile");

        assertTrue(dependencyTask.isTest(configuration));
    }

    @Test
    public void testIsTest_fromHierarchy() {
        Configuration configuration = mock(Configuration.class);
        when(configuration.getName()).thenReturn("random");

        Configuration parent = mock(Configuration.class);
        when(parent.getName()).thenReturn("testCompile");

        Set<Configuration> hierarchy = new HashSet<>();
        hierarchy.add(parent);

        when(configuration.getHierarchy()).thenReturn(hierarchy);
        assertTrue(dependencyTask.isTest(configuration));
    }

    private Set<ResolvedArtifact> preppareArtifactSet(int count) {
        Set<ResolvedArtifact> artifacts = new HashSet<>();
        String namePrefix = "artifact";
        String groupPrefix = "group";
        String locationPrefix = "location";
        String versionPostfix = ".0";
        for (int i = 0; i < count; i++) {
            String index = String.valueOf(i);
            artifacts.add(
                    prepareArtifact(
                            namePrefix + index,
                            groupPrefix + index,
                            locationPrefix + index,
                            index + versionPostfix));
        }
        return artifacts;
    }

    private ResolvedArtifact prepareArtifact(
            String name, String group, String filePath, String version) {
        ModuleVersionIdentifier moduleId = mock(ModuleVersionIdentifier.class);
        when(moduleId.getGroup()).thenReturn(group);
        when(moduleId.getVersion()).thenReturn(version);

        ResolvedModuleVersion moduleVersion = mock(ResolvedModuleVersion.class);
        when(moduleVersion.getId()).thenReturn(moduleId);

        File artifactFile = mock(File.class);
        when(artifactFile.getAbsolutePath()).thenReturn(filePath);

        ResolvedArtifact artifact = mock(ResolvedArtifact.class);
        when(artifact.getName()).thenReturn(name);
        when(artifact.getFile()).thenReturn(artifactFile);
        when(artifact.getModuleVersion()).thenReturn(moduleVersion);

        return artifact;
    }
}
