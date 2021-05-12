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

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.testfixtures.ProjectBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DependencyTask} */
@RunWith(JUnit4.class)
public class DependencyTaskTest {

  private Project project;
  private DependencyTask dependencyTask;

  @Before
  public void setUp() {
    project = ProjectBuilder.builder().build();
    dependencyTask = project.getTasks().create("getDependency", DependencyTask.class);
  }

  @Test
  public void collectDependenciesFromConfigurations_multipleConfigs_combined() {
    List<String> compileExpectedIds = Arrays.asList(
        "com.example:real-dependency:1.2.3",
        "com.example:totally-useful:4.5.6"
    );
    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        compileExpectedIds);
    List<String> apiExpectedIds = Arrays.asList(
        "com.example:api-thing:1.2.3",
        "com.example:idk-rpc:4.5.6"
    );
    Configuration apiConfiguration = mockConfiguration(
        "api",
        /* canBeResolved = */ true,
        apiExpectedIds);
    ConfigurationContainer configurationContainer = mock(ConfigurationContainer.class);
    when(configurationContainer.iterator())
        .thenReturn(Arrays.asList(compileConfiguration, apiConfiguration).iterator());
    List<String> allExpectedIds = new ArrayList<>(compileExpectedIds);
    allExpectedIds.addAll(apiExpectedIds);
    Collections.sort(allExpectedIds);

    List<String> dependencies = dependencyTask
        .collectDependenciesFromConfigurations(configurationContainer, new HashSet<>());
    Collections.sort(dependencies);

    verify(compileConfiguration, times(1)).getAllDependencies();
    verify(apiConfiguration, times(1)).getAllDependencies();
    assertThat(dependencies, is(allExpectedIds));
  }

  @Test
  public void collectDependenciesFromConfigurations_libraryProject_combined() {
    List<String> libraryProjectExpectedIds = Arrays.asList(
        "com.example:api-thing:1.2.3",
        "com.example:idk-rpc:4.5.6"
    );
    Configuration libraryConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        libraryProjectExpectedIds);
    Project libraryProject = mock(Project.class);
    ConfigurationContainer libraryConfigurationContainer = mock(ConfigurationContainer.class);
    when(libraryConfigurationContainer.iterator())
        .thenReturn(Collections.singletonList(libraryConfiguration).iterator());
    when(libraryProject.getConfigurations()).thenReturn(libraryConfigurationContainer);
    ProjectDependency libraryProjectDependency = mockDependency(
        ProjectDependency.class, "this:is:ignored");
    when(libraryProjectDependency.getDependencyProject()).thenReturn(libraryProject);

    List<String> compileExpectedIds = Arrays.asList(
        "com.example:real-dependency:1.2.3",
        "com.example:totally-useful:4.5.6"
    );
    DependencySet dependencySet = mockDependencySet(new HashSet<Dependency>() {{
      add(libraryProjectDependency);
      add(mockDependency(ExternalModuleDependency.class, compileExpectedIds.get(0)));
      add(mockDependency(ExternalModuleDependency.class, compileExpectedIds.get(1)));
    }});
    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        dependencySet);
    ConfigurationContainer configurationContainer = mock(ConfigurationContainer.class);
    when(configurationContainer.iterator())
        .thenReturn(Collections.singletonList(compileConfiguration).iterator());
    List<String> allExpectedIds = new ArrayList<>(compileExpectedIds);
    allExpectedIds.addAll(libraryProjectExpectedIds);
    Collections.sort(allExpectedIds);

    List<String> dependencies = dependencyTask
        .collectDependenciesFromConfigurations(configurationContainer, new HashSet<>());
    Collections.sort(dependencies);

    verify(compileConfiguration, times(1)).getAllDependencies();
    verify(libraryConfiguration, times(1)).getAllDependencies();
    assertThat(dependencies, is(allExpectedIds));
  }

  @Test
  public void collectDependenciesFromConfigurations_libraryDependencyCycle_visitedOnce() {
    List<String> libraryProjectExpectedIds = Arrays.asList(
        "com.example:api-thing:1.2.3",
        "com.example:idk-rpc:4.5.6"
    );
    Project libraryProject = mock(Project.class);
    ProjectDependency libraryProjectDependency = mockDependency(
        ProjectDependency.class, "this:is:ignored");
    when(libraryProjectDependency.getDependencyProject()).thenReturn(libraryProject);
    // Library project self-dependency cycle by including itself as a dependent project.
    DependencySet libraryDependencySet = mockDependencySet(new HashSet<Dependency>() {{
      add(libraryProjectDependency);
      add(mockDependency(ExternalModuleDependency.class, libraryProjectExpectedIds.get(0)));
      add(mockDependency(ExternalModuleDependency.class, libraryProjectExpectedIds.get(1)));
    }});
    Configuration libraryConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        libraryDependencySet);
    ConfigurationContainer libraryConfigurationContainer = mock(ConfigurationContainer.class);
    when(libraryConfigurationContainer.iterator())
        .thenAnswer(ignored -> Collections.singletonList(libraryConfiguration).iterator());
    when(libraryProject.getConfigurations()).thenReturn(libraryConfigurationContainer);

    List<String> compileExpectedIds = Arrays.asList(
        "com.example:real-dependency:1.2.3",
        "com.example:totally-useful:4.5.6"
    );
    DependencySet dependencySet = mockDependencySet(new HashSet<Dependency>() {{
      add(libraryProjectDependency);
      add(mockDependency(ExternalModuleDependency.class, compileExpectedIds.get(0)));
      add(mockDependency(ExternalModuleDependency.class, compileExpectedIds.get(1)));
    }});
    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        dependencySet);
    ConfigurationContainer configurationContainer = mock(ConfigurationContainer.class);
    when(configurationContainer.iterator())
        .thenReturn(Collections.singletonList(compileConfiguration).iterator());
    List<String> allExpectedIds = new ArrayList<>(compileExpectedIds);
    allExpectedIds.addAll(libraryProjectExpectedIds);
    Collections.sort(allExpectedIds);

    List<String> dependencies = dependencyTask
        .collectDependenciesFromConfigurations(configurationContainer, new HashSet<>());
    Collections.sort(dependencies);

    verify(compileConfiguration, times(1)).getAllDependencies();
    verify(libraryConfiguration, times(1)).getAllDependencies();
    verify(libraryProject, times(1)).getConfigurations();
    assertThat(dependencies, is(allExpectedIds));
  }

  @Test
  public void collectDependenciesFromConfigurations_appSelfCycle_visitedOnce() {
    List<String> appExpectedIds = Arrays.asList(
        "com.example:real-dependency:1.2.3",
        "com.example:totally-useful:4.5.6"
    );
    Project appProject = mock(Project.class);
    when(appProject.getDisplayName()).thenReturn("YOLO");
    ProjectDependency appProjectDependency = mockDependency(
        ProjectDependency.class, "this:is:ignored");
    when(appProjectDependency.getDependencyProject()).thenReturn(appProject);
    // App project self-dependency cycle by including itself as a dependent project.
    DependencySet dependencySet = mockDependencySet(new HashSet<Dependency>() {{
      add(appProjectDependency);
      add(mockDependency(ExternalModuleDependency.class, appExpectedIds.get(0)));
      add(mockDependency(ExternalModuleDependency.class, appExpectedIds.get(1)));
    }});
    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        dependencySet);
    ConfigurationContainer configurationContainer = mock(ConfigurationContainer.class);
    when(configurationContainer.iterator())
        .thenAnswer(ignored -> Collections.singletonList(compileConfiguration).iterator());
    when(appProject.getConfigurations()).thenReturn(configurationContainer);
    Collections.sort(appExpectedIds);

    List<String> dependencies = dependencyTask.collectDependenciesFromConfigurations(
        configurationContainer,
        Collections.singleton(appProject)
    );
    Collections.sort(dependencies);

    verify(compileConfiguration, times(1)).getAllDependencies();
    verify(appProject, never()).getConfigurations();
    assertThat(dependencies, is(appExpectedIds));
  }

  @Test
  public void collectDependenciesFromConfigurations_withTestConfig_ignored() {
    List<String> expectedIds = Arrays.asList(
        "com.example:real-dependency:1.2.3",
        "com.example:totally-useful:4.5.6"
    );
    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        expectedIds);
    List<String> unexpectedIds = Arrays.asList(
        "com.test-only:test-thing:1.2.3",
        "com.test-only:not-shipped:4.5.6"
    );
    Configuration testConfiguration = mockConfiguration(
        "testCompile",
        /* canBeResolved = */ true,
        unexpectedIds);
    ConfigurationContainer configurationContainer = mock(ConfigurationContainer.class);
    when(configurationContainer.iterator())
        .thenReturn(Arrays.asList(compileConfiguration, testConfiguration).iterator());

    List<String> dependencies = dependencyTask
        .collectDependenciesFromConfigurations(configurationContainer, new HashSet<>());
    Collections.sort(dependencies);

    verify(compileConfiguration, times(1)).getAllDependencies();
    verify(testConfiguration, never()).getAllDependencies();
    assertThat(dependencies, is(expectedIds));
  }

  @Test
  public void collectDependenciesFromConfigurations_withUnresolvableConfig_ignored() {
    List<String> expectedIds = Arrays.asList(
        "com.example:real-dependency:1.2.3",
        "com.example:totally-useful:4.5.6"
    );
    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        expectedIds);
    List<String> unexpectedIds = Arrays.asList(
        "com.mystery:unresolvable:1.2.3",
        "com.enigma:dont-resolve-this:4.5.6"
    );
    Configuration unresolvableConfiguration = mockConfiguration(
        "api",
        /* canBeResolved = */ false,
        unexpectedIds);
    ConfigurationContainer configurationContainer = mock(ConfigurationContainer.class);
    when(configurationContainer.iterator())
        .thenReturn(Arrays.asList(compileConfiguration, unresolvableConfiguration).iterator());

    List<String> dependencies = dependencyTask
        .collectDependenciesFromConfigurations(configurationContainer, new HashSet<>());
    Collections.sort(dependencies);

    verify(compileConfiguration, times(1)).getAllDependencies();
    verify(unresolvableConfiguration, never()).getAllDependencies();
    assertThat(dependencies, is(expectedIds));
  }

  @Test
  public void collectDependenciesFromConfigurations_unusableDependencyClass_ignored() {
    List<String> expectedIds = Collections.singletonList("should.be:alone:4.5.6");
    DependencySet dependencySet = mockDependencySet(new HashSet<Dependency>() {{
      add(mockDependency(Dependency.class, "should.be:skipped:1.2.3"));
      add(mockDependency(ExternalModuleDependency.class, expectedIds.get(0)));
    }});
    Configuration configuration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        dependencySet);
    ConfigurationContainer configurationContainer = mock(ConfigurationContainer.class);
    when(configurationContainer.iterator())
        .thenReturn(Collections.singletonList(configuration).iterator());

    List<String> dependencies = dependencyTask
        .collectDependenciesFromConfigurations(configurationContainer, new HashSet<>());
    Collections.sort(dependencies);

    verify(configuration, times(1)).getAllDependencies();
    assertThat(dependencies, is(expectedIds));
  }

  private Configuration mockConfiguration(String name, boolean canBeResolved,
      List<String> mavenIds) {
    DependencySet dependencies = mockDependencySet(mavenIds);
    return mockConfiguration(name, canBeResolved, dependencies);
  }

  private Configuration mockConfiguration(String name, boolean canBeResolved,
      DependencySet dependencies) {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn(name);
    when(configuration.isCanBeResolved()).thenReturn(canBeResolved);
    when(configuration.getAllDependencies()).thenReturn(dependencies);
    return configuration;
  }

  private DependencySet mockDependencySet(List<String> mavenIds) {
    Set<Dependency> dependencies = new HashSet<>();
    for (String mavenId : mavenIds) {
      dependencies.add(mockDependency(ExternalModuleDependency.class, mavenId));
    }
    return mockDependencySet(dependencies);
  }

  private DependencySet mockDependencySet(Set<Dependency> dependencies) {
    DependencySet dependencySet = mock(DependencySet.class);
    when(dependencySet.iterator()).thenAnswer(ignored -> dependencies.iterator());
    return dependencySet;
  }

  private <T extends Dependency> T mockDependency(
      Class<T> dependencyClass,
      String mavenId
  ) {
    T dependency = mock(dependencyClass);
    String[] mavenIdFields = mavenId.split(":");
    when(dependency.getGroup()).thenReturn(mavenIdFields[0]);
    when(dependency.getName()).thenReturn(mavenIdFields[1]);
    when(dependency.getVersion()).thenReturn(mavenIdFields[2]);
    return dependency;
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
        new String[] {
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
        new String[] {
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
        new String[] {
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
    Set<ResolvedArtifact> artifactSet = prepareArtifactSet(2);
    ResolvedConfiguration resolvedConfiguration = mockResolvedConfiguration(artifactSet);

    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("implementation");
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    when(configuration.isCanBeResolved()).thenReturn(false);

    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(nullValue()));
  }

  @Test
  public void testGetResolvedArtifacts_isTest() {
    Set<ResolvedArtifact> artifactSet = prepareArtifactSet(2);
    ResolvedConfiguration resolvedConfiguration = mockResolvedConfiguration(artifactSet);

    Configuration configuration = mock(Configuration.class);
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    when(configuration.getName()).thenReturn("testCompile");

    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(nullValue()));
  }

  @Test
  public void testGetResolvedArtifacts_isNotPackaged() {
    Set<ResolvedArtifact> artifactSet = prepareArtifactSet(2);
    ResolvedConfiguration resolvedConfiguration = mockResolvedConfiguration(artifactSet);

    Configuration configuration = mock(Configuration.class);
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    when(configuration.getName()).thenReturn("random");

    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(nullValue()));
  }

  @Test
  public void testGetResolvedArtifacts_isPackagedApi() {
    Set<ResolvedArtifact> artifactSet = prepareArtifactSet(2);
    ResolvedConfiguration resolvedConfiguration = mockResolvedConfiguration(artifactSet);

    Configuration configuration = mock(Configuration.class);
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    when(configuration.getName()).thenReturn("api");

    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(artifactSet));
  }

  @Test
  public void testGetResolvedArtifacts_isPackagedImplementation() {
    Set<ResolvedArtifact> artifactSet = prepareArtifactSet(2);
    ResolvedConfiguration resolvedConfiguration = mockResolvedConfiguration(artifactSet);

    Configuration configuration = mock(Configuration.class);
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    when(configuration.getName()).thenReturn("implementation");

    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(artifactSet));
  }

  @Test
  public void testGetResolvedArtifacts_isPackagedCompile() {
    Set<ResolvedArtifact> artifactSet = prepareArtifactSet(2);
    ResolvedConfiguration resolvedConfiguration = mockResolvedConfiguration(artifactSet);

    Configuration configuration = mock(Configuration.class);
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    when(configuration.getName()).thenReturn("compile");

    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(artifactSet));
  }

  @Test
  public void testGetResolvedArtifacts_isPackagedInHierarchy() {
    Set<ResolvedArtifact> artifactSet = prepareArtifactSet(2);
    ResolvedConfiguration resolvedConfiguration = mockResolvedConfiguration(artifactSet);

    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("random");
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    Configuration parent = mock(Configuration.class);
    when(parent.getName()).thenReturn("compile");
    Set<Configuration> hierarchy = new HashSet<>();
    hierarchy.add(parent);
    when(configuration.getHierarchy()).thenReturn(hierarchy);

    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(artifactSet));
  }

  @Test
  public void testGetResolvedArtifacts_ResolveException() {
    ResolvedConfiguration resolvedConfiguration = mock(ResolvedConfiguration.class);
    when(resolvedConfiguration.getLenientConfiguration()).thenThrow(ResolveException.class);

    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("compile");
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(nullValue()));
  }

  @Test
  public void testGetResolvedArtifacts_returnArtifact() {
    Set<ResolvedArtifact> artifactSet = prepareArtifactSet(2);
    ResolvedConfiguration resolvedConfiguration = mockResolvedConfiguration(artifactSet);

    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("compile");
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(artifactSet));
  }

  @Test
  public void testGetResolvedArtifacts_libraryProject_returnsArtifacts() {
    ResolvedDependency libraryResolvedDependency = mock(ResolvedDependency.class);
    when(libraryResolvedDependency.getModuleVersion())
            .thenReturn(DependencyTask.LOCAL_LIBRARY_VERSION);
    ResolvedDependency libraryChildResolvedDependency = mock(ResolvedDependency.class);
    Set<ResolvedArtifact> libraryChildArtifactSet =
            prepareArtifactSet(/* start= */ 0, /* count= */ 2);
    when(libraryChildResolvedDependency.getAllModuleArtifacts())
            .thenReturn(libraryChildArtifactSet);
    when(libraryResolvedDependency.getChildren())
            .thenReturn(Collections.singleton(libraryChildResolvedDependency));

    Set<ResolvedArtifact> appArtifactSet = prepareArtifactSet(/* start= */ 2, /* count= */ 2);
    ResolvedDependency appResolvedDependency = mock(ResolvedDependency.class);
    when(appResolvedDependency.getAllModuleArtifacts()).thenReturn(appArtifactSet);

    Set<ResolvedDependency> resolvedDependencySet = new HashSet<>(
            Arrays.asList(libraryResolvedDependency, appResolvedDependency));
    ResolvedConfiguration resolvedConfiguration = mockResolvedConfigurationFromDependencySet(
            resolvedDependencySet);

    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("compile");
    when(configuration.isCanBeResolved()).thenReturn(true);
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);

    Set<ResolvedArtifact> artifactSuperSet = new HashSet<>();
    artifactSuperSet.addAll(appArtifactSet);
    artifactSuperSet.addAll(libraryChildArtifactSet);
    assertThat(dependencyTask.getResolvedArtifacts(configuration), is(artifactSuperSet));
    // Calling getAllModuleArtifacts on a library will cause an exception.
    verify(libraryResolvedDependency, never()).getAllModuleArtifacts();
  }

  @NotNull
  private ResolvedConfiguration mockResolvedConfiguration(Set<ResolvedArtifact> artifactSet) {
    ResolvedDependency resolvedDependency = mock(ResolvedDependency.class);
    when(resolvedDependency.getAllModuleArtifacts()).thenReturn(artifactSet);
    Set<ResolvedDependency> resolvedDependencySet = Collections.singleton(resolvedDependency);
    return mockResolvedConfigurationFromDependencySet(resolvedDependencySet);
  }

  @NotNull
  private ResolvedConfiguration mockResolvedConfigurationFromDependencySet(
          Set<ResolvedDependency> resolvedDependencySet) {

    LenientConfiguration lenientConfiguration = mock(LenientConfiguration.class);
    when(lenientConfiguration.getFirstLevelModuleDependencies()).thenReturn(resolvedDependencySet);
    ResolvedConfiguration resolvedConfiguration = mock(ResolvedConfiguration.class);
    when(resolvedConfiguration.getLenientConfiguration()).thenReturn(lenientConfiguration);
    return resolvedConfiguration;
  }

  @Test
  public void testAddArtifacts() {
    Set<ResolvedArtifact> artifacts = prepareArtifactSet(3);

    dependencyTask.addArtifacts(artifacts);
    assertThat(dependencyTask.artifactInfos.size(), is(3));
  }

  @Test
  public void testAddArtifacts_willNotAddDuplicate() {
    Set<ResolvedArtifact> artifacts = prepareArtifactSet(2);

    String[] keySets = new String[] {"location1", "location2"};
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

  private Set<ResolvedArtifact> prepareArtifactSet(int count) {
    return prepareArtifactSet(0, count);
  }

  private Set<ResolvedArtifact> prepareArtifactSet(int start, int count) {
    Set<ResolvedArtifact> artifacts = new HashSet<>();
    String namePrefix = "artifact";
    String groupPrefix = "group";
    String locationPrefix = "location";
    String versionPostfix = ".0";
    for (int i = start; i < start + count; i++) {
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
