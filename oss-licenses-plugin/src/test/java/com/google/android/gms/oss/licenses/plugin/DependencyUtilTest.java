package com.google.android.gms.oss.licenses.plugin;


import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Configuration.State;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DependencyUtilTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void getLibraryFile_returnsLibraryFile() throws Exception {
    ArtifactInfo artifactInfo = new ArtifactInfo("com.google.android.gms.test", "test", "1.0.0");
    File expectedFile = temporaryFolder.newFile();
    ResolvedArtifact resolvedArtifact = mockResolvedArtifact(artifactInfo, expectedFile);
    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        State.RESOLVED,
        mockSingleDependencySet(resolvedArtifact));
    Project project = mockProjectWithConfiguration(compileConfiguration);

    File libraryFile = DependencyUtil.getLibraryFile(project, artifactInfo);

    assertThat(libraryFile).isEqualTo(expectedFile);
  }

  @Test
  public void getLibraryFile_noVersionMatch_returnsNull() throws Exception {
    ArtifactInfo artifactInfo = new ArtifactInfo("com.google.android.gms.test", "test", "1.0.0");
    File expectedFile = temporaryFolder.newFile();
    ResolvedArtifact resolvedArtifact = mockResolvedArtifact(artifactInfo, expectedFile);
    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        State.RESOLVED,
        mockSingleDependencySet(resolvedArtifact));
    Project project = mockProjectWithConfiguration(compileConfiguration);
    ArtifactInfo differentVersionInfo = new ArtifactInfo("com.google.android.gms.test", "test",
        "2.0.0");

    File libraryFile = DependencyUtil.getLibraryFile(project, differentVersionInfo);

    assertThat(libraryFile).isNull();
  }

  @Test
  public void getLibraryFile_noMatch_returnsNull() throws Exception {
    ArtifactInfo artifactInfo = new ArtifactInfo("com.google.android.gms.test", "test", "1.0.0");
    File expectedFile = temporaryFolder.newFile();
    ResolvedArtifact resolvedArtifact = mockResolvedArtifact(artifactInfo, expectedFile);
    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        State.RESOLVED,
        mockSingleDependencySet(resolvedArtifact));
    Project project = mockProjectWithConfiguration(compileConfiguration);
    ArtifactInfo missingArtifactInfo = new ArtifactInfo("com.uhoh.example", "whoops", "1.0.0");

    File libraryFile = DependencyUtil.getLibraryFile(project, missingArtifactInfo);

    assertThat(libraryFile).isNull();
  }

  @Test
  public void getLibraryFile_unresolvedConfiguration_returnsNull() throws Exception {
    ArtifactInfo artifactInfo = new ArtifactInfo("com.google.android.gms.test", "test", "1.0.0");
    File expectedFile = temporaryFolder.newFile();
    ResolvedArtifact resolvedArtifact = mockResolvedArtifact(artifactInfo, expectedFile);
    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        State.UNRESOLVED,
        mockSingleDependencySet(resolvedArtifact));
    Project project = mockProjectWithConfiguration(compileConfiguration);

    File libraryFile = DependencyUtil.getLibraryFile(project, artifactInfo);

    assertThat(libraryFile).isNull();
    verify(compileConfiguration, never()).getResolvedConfiguration();
  }

  @Test
  public void getLibraryFile_withUnwantedConfigs_skipsUnwantedConfigs() throws Exception {
    ArtifactInfo artifactInfo = new ArtifactInfo("com.google.android.gms.test", "test", "1.0.0");
    File expectedFile = temporaryFolder.newFile();
    ResolvedArtifact resolvedArtifact = mockResolvedArtifact(artifactInfo, expectedFile);
    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        State.RESOLVED,
        mockSingleDependencySet(resolvedArtifact));
    Configuration arbitraryConfiguration = mockConfiguration(
        "arbitrary",
        /* canBeResolved = */ true,
        State.RESOLVED,
        mockSingleDependencySet(resolvedArtifact));
    Configuration testConfiguration = mockConfiguration(
        "androidTest",
        /* canBeResolved = */ true,
        State.RESOLVED,
        mockSingleDependencySet(resolvedArtifact));
    Configuration unresolvableConfiguration = mockConfiguration(
        "api",
        /* canBeResolved = */ false,
        State.UNRESOLVED,
        mockSingleDependencySet(resolvedArtifact));
    Project project = mockProjectWithConfigurations(ImmutableSet.of(
        compileConfiguration,
        arbitraryConfiguration,
        testConfiguration,
        unresolvableConfiguration)
    );

    File libraryFile = DependencyUtil.getLibraryFile(project, artifactInfo);

    assertThat(libraryFile).isEqualTo(expectedFile);
    verify(compileConfiguration, times(1)).getResolvedConfiguration();
    verify(arbitraryConfiguration, never()).getResolvedConfiguration();
    verify(testConfiguration, never()).getResolvedConfiguration();
    verify(unresolvableConfiguration, never()).getResolvedConfiguration();
  }

  @Test
  public void getLibraryFile_withLibraryDependency_readsChildrenOfLibrary() throws Exception {
    ArtifactInfo artifactInfo = new ArtifactInfo("com.google.android.gms.test", "test", "1.0.0");
    File expectedFile = temporaryFolder.newFile();
    ResolvedArtifact resolvedArtifact = mockResolvedArtifact(artifactInfo, expectedFile);

    ResolvedDependency libraryDependency = mock(ResolvedDependency.class);
    when(libraryDependency.getModuleVersion()).thenReturn(DependencyUtil.LOCAL_LIBRARY_VERSION);
    ImmutableSet<ResolvedDependency> children = mockSingleDependencySet(resolvedArtifact);
    when(libraryDependency.getChildren()).thenReturn(children);

    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        State.RESOLVED,
        ImmutableSet.of(libraryDependency));
    Project project = mockProjectWithConfiguration(compileConfiguration);

    File libraryFile = DependencyUtil.getLibraryFile(project, artifactInfo);

    assertThat(libraryFile).isEqualTo(expectedFile);
    verify(libraryDependency, never()).getAllModuleArtifacts();
  }

  @Test
  public void getLibraryFile_withLibraryDependencyCycle_readsChildrenOfLibrary() throws Exception {
    ArtifactInfo artifactInfo = new ArtifactInfo("com.google.android.gms.test", "test", "1.0.0");
    File expectedFile = temporaryFolder.newFile();
    ResolvedArtifact resolvedArtifact = mockResolvedArtifact(artifactInfo, expectedFile);

    ResolvedDependency libraryDependency = mock(ResolvedDependency.class);
    ResolvedDependency childLibraryDependency = mock(ResolvedDependency.class);
    when(libraryDependency.getModuleVersion()).thenReturn(DependencyUtil.LOCAL_LIBRARY_VERSION);
    when(libraryDependency.getChildren()).thenReturn(ImmutableSet.of(childLibraryDependency));
    when(childLibraryDependency.getModuleVersion()).thenReturn(
        DependencyUtil.LOCAL_LIBRARY_VERSION);
    ImmutableSet<ResolvedDependency> grandChildren = ImmutableSet.of(
        libraryDependency, // <- Two hop dependency cycle to parent
        mockResolvedDependency(ImmutableSet.of(resolvedArtifact))
    );
    when(childLibraryDependency.getChildren()).thenReturn(grandChildren);

    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        State.RESOLVED,
        ImmutableSet.of(libraryDependency));
    Project project = mockProjectWithConfiguration(compileConfiguration);

    File libraryFile = DependencyUtil.getLibraryFile(project, artifactInfo);

    assertThat(libraryFile).isEqualTo(expectedFile);
    verify(libraryDependency, never()).getAllModuleArtifacts();
  }

  @Test
  public void getLibraryFile_withStubLibraryDependency_readsFromMainConfig() throws Exception {
    ArtifactInfo artifactInfo = new ArtifactInfo("com.google.android.gms.test", "test", "1.0.0");
    File expectedFile = temporaryFolder.newFile();
    ResolvedArtifact resolvedArtifact = mockResolvedArtifact(artifactInfo, expectedFile);

    ResolvedDependency libraryDependency = mock(ResolvedDependency.class);
    when(libraryDependency.getModuleVersion()).thenReturn(DependencyUtil.LOCAL_LIBRARY_VERSION);
    ImmutableSet<ResolvedDependency> children = ImmutableSet.of();
    when(libraryDependency.getChildren()).thenReturn(children);

    Configuration compileConfiguration = mockConfiguration(
        "compile",
        /* canBeResolved = */ true,
        State.RESOLVED,
        ImmutableSet.of(
            libraryDependency, mockResolvedDependency(ImmutableSet.of(resolvedArtifact))
        ));
    Project project = mockProjectWithConfiguration(compileConfiguration);

    File libraryFile = DependencyUtil.getLibraryFile(project, artifactInfo);

    assertThat(libraryFile).isEqualTo(expectedFile);
    verify(libraryDependency, never()).getAllModuleArtifacts();
  }

  private Configuration mockConfiguration(String name, boolean canBeResolved,
      Configuration.State state, ImmutableSet<ResolvedDependency> resolvedDependencies) {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn(name);
    when(configuration.getState()).thenReturn(state);
    when(configuration.isCanBeResolved()).thenReturn(canBeResolved);

    ResolvedConfiguration resolvedConfiguration = mock(ResolvedConfiguration.class);
    when(configuration.getResolvedConfiguration()).thenReturn(resolvedConfiguration);
    LenientConfiguration lenientConfiguration = mock(LenientConfiguration.class);
    when(lenientConfiguration.getAllModuleDependencies()).thenReturn(resolvedDependencies);
    when(resolvedConfiguration.getLenientConfiguration()).thenReturn(lenientConfiguration);

    return configuration;
  }

  private ImmutableSet<ResolvedDependency> mockSingleDependencySet(
      ResolvedArtifact resolvedArtifact) {
    return ImmutableSet.of(mockResolvedDependency(ImmutableSet.of(resolvedArtifact)));
  }

  private ResolvedDependency mockResolvedDependency(
      ImmutableSet<ResolvedArtifact> resolvedArtifacts) {
    ResolvedDependency resolvedDependency = mock(ResolvedDependency.class);
    when(resolvedDependency.getAllModuleArtifacts()).thenReturn(resolvedArtifacts);
    return resolvedDependency;
  }

  private Project mockProjectWithConfiguration(Configuration configuration) {
    return mockProjectWithConfigurations(ImmutableSet.of(configuration));
  }

  private Project mockProjectWithConfigurations(ImmutableSet<Configuration> configurations) {
    ConfigurationContainer configurationContainer = mock(ConfigurationContainer.class);
    when(configurationContainer.iterator())
        .thenReturn(configurations.iterator());

    Project project = mock(Project.class);
    when(project.getConfigurations()).thenReturn(configurationContainer);
    return project;
  }

  private ResolvedArtifact mockResolvedArtifact(ArtifactInfo artifactInfo, File artifactFile) {
    ModuleVersionIdentifier moduleId = mock(ModuleVersionIdentifier.class);
    when(moduleId.getGroup()).thenReturn(artifactInfo.getGroup());
    when(moduleId.getVersion()).thenReturn(artifactInfo.getVersion());

    ResolvedModuleVersion moduleVersion = mock(ResolvedModuleVersion.class);
    when(moduleVersion.getId()).thenReturn(moduleId);

    ResolvedArtifact artifact = mock(ResolvedArtifact.class);
    when(artifact.getName()).thenReturn(artifactInfo.getName());
    when(artifact.getFile()).thenReturn(artifactFile);
    when(artifact.getModuleVersion()).thenReturn(moduleVersion);

    return artifact;
  }

  @Test
  public void canBeResolved_isTrue() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.isCanBeResolved()).thenReturn(true);

    assertThat(DependencyUtil.canBeResolved(configuration)).isTrue();
  }

  @Test
  public void canBeResolved_isFalse() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.isCanBeResolved()).thenReturn(false);

    assertThat(DependencyUtil.canBeResolved(configuration)).isFalse();
  }

  @Test
  public void isTest_arbitraryConfiguration_returnsFalse() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("arbitrary");

    assertThat(DependencyUtil.isTest(configuration)).isFalse();
  }

  @Test
  public void isTest_testCompile_returnsTrue() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("testCompile");

    assertThat(DependencyUtil.isTest(configuration)).isTrue();
  }

  @Test
  public void isTest_androidTestCompile_returnsTrue() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("androidTestCompile");

    assertThat(DependencyUtil.isTest(configuration)).isTrue();
  }

  @Test
  public void isTest_parentIsTest_returnsTrue() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("someArbitraryChildName");

    Configuration parent = mock(Configuration.class);
    when(parent.getName()).thenReturn("testCompile");

    Set<Configuration> hierarchy = new HashSet<>();
    hierarchy.add(parent);

    when(configuration.getHierarchy()).thenReturn(hierarchy);
    assertThat(DependencyUtil.isTest(configuration)).isTrue();
  }

  @Test
  public void isPackagedDependency_arbitraryConfiguration_returnsFalse() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("arbitrary");

    assertThat(DependencyUtil.isPackagedDependency(configuration)).isFalse();
  }

  @Test
  public void isPackagedDependency_apiConfiguration_returnsTrue() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("api");

    assertThat(DependencyUtil.isPackagedDependency(configuration)).isTrue();
  }

  @Test
  public void isPackagedDependency_implementationConfiguration_returnsTrue() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("implementation");

    assertThat(DependencyUtil.isPackagedDependency(configuration)).isTrue();
  }

  @Test
  public void isPackagedDependency_compileConfiguration_returnsTrue() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("compile");

    assertThat(DependencyUtil.isPackagedDependency(configuration)).isTrue();
  }

  @Test
  public void isPackagedDependency_compileConfigurationChild_returnsTrue() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getName()).thenReturn("someArbitraryChildName");

    Configuration parent = mock(Configuration.class);
    when(parent.getName()).thenReturn("compile");
    Set<Configuration> hierarchy = new HashSet<>();
    hierarchy.add(parent);
    when(configuration.getHierarchy()).thenReturn(hierarchy);

    assertThat(DependencyUtil.isPackagedDependency(configuration)).isTrue();
  }

}
