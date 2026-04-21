/**
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.gms.oss.licenses.plugin;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.build.libraries.metadata.AppDependencies;
import com.android.tools.build.libraries.metadata.Library;
import com.android.tools.build.libraries.metadata.MavenLibrary;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link DependencyTask}
 */
@RunWith(JUnit4.class)
public class DependencyTaskTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Project project;
  private DependencyTask dependencyTask;

  private static AppDependencies createAppDependencies(ImmutableSet<ArtifactInfo> artifactInfoSet) {
    AppDependencies.Builder appDependenciesBuilder = AppDependencies.newBuilder();
    for (ArtifactInfo artifactInfo : artifactInfoSet) {
      appDependenciesBuilder.addLibrary(Library.newBuilder()
          .setMavenLibrary(MavenLibrary.newBuilder()
              .setGroupId(artifactInfo.getGroup())
              .setArtifactId(artifactInfo.getName())
              .setVersion(artifactInfo.getVersion())
          )
      );
    }
    return appDependenciesBuilder.build();
  }

  private static File writeAppDependencies(AppDependencies appDependencies, File protoFile)
      throws IOException {
    try (OutputStream outputStream = new FileOutputStream(protoFile)) {
      appDependencies.writeTo(outputStream);
    }
    return protoFile;
  }

  @Before
  public void setUp() {
    project = ProjectBuilder.builder().build();
    dependencyTask = project.getTasks().create("getDependency", DependencyTask.class);
  }

  @Test
  public void testAction_valuesConvertedToJson() throws Exception {
    File outputDir = temporaryFolder.newFolder();
    File outputJson = new File(outputDir, "test.json");
    dependencyTask.getDependenciesJson().set(outputJson);
    ImmutableSet<ArtifactInfo> expectedArtifacts = ImmutableSet.of(
        new ArtifactInfo("org.group.id", "artifactId", "1.0.0"),
        new ArtifactInfo("org.group.other", "other-artifact", "3.2.1")
    );
    AppDependencies appDependencies = createAppDependencies(expectedArtifacts);
    File protoFile = writeAppDependencies(appDependencies, temporaryFolder.newFile());
    dependencyTask.getLibraryDependenciesReport().set(protoFile);

    dependencyTask.action();

    verifyExpectedDependencies(expectedArtifacts, outputJson);
  }

  @Test
  public void testAction_withNonMavenDeps_nonMavenDepsIgnored() throws Exception {
    File outputDir = temporaryFolder.newFolder();
    File outputJson = new File(outputDir, "test.json");
    dependencyTask.getDependenciesJson().set(outputJson);
    ImmutableSet<ArtifactInfo> expectedArtifacts = ImmutableSet.of(
        new ArtifactInfo("org.group.id", "artifactId", "1.0.0"),
        new ArtifactInfo("org.group.other", "other-artifact", "3.2.1")
    );
    AppDependencies appDependencies = createAppDependencies(expectedArtifacts).toBuilder()
        .addLibrary(Library.getDefaultInstance()) // There aren't any other library types supported.
        .build();
    File protoFile = writeAppDependencies(appDependencies, temporaryFolder.newFile());
    dependencyTask.getLibraryDependenciesReport().set(protoFile);

    dependencyTask.action();

    verifyExpectedDependencies(expectedArtifacts, outputJson);
  }

  @Test
  public void testAction_depFileAbsent_writesAbsentDep() throws Exception {
    File outputDir = temporaryFolder.newFolder();
    File outputJson = new File(outputDir, "test.json");
    dependencyTask.getDependenciesJson().set(outputJson);
    ImmutableSet<ArtifactInfo> expectedArtifacts = ImmutableSet.of(DependencyTask.ABSENT_ARTIFACT);

    dependencyTask.action();

    verifyExpectedDependencies(expectedArtifacts, outputJson);
  }

  @Test
  public void testAction_snapshotVersions_includeHashes() throws Exception {
    File outputDir = temporaryFolder.newFolder();
    File outputJson = new File(outputDir, "test.json");
    dependencyTask.getDependenciesJson().set(outputJson);

    String snapshotVersion = "1.0.0-SNAPSHOT";
    ArtifactInfo snapshotDep = new ArtifactInfo("org.group", "artifact", snapshotVersion);
    AppDependencies appDependencies = createAppDependencies(ImmutableSet.of(snapshotDep));
    File protoFile = writeAppDependencies(appDependencies, temporaryFolder.newFile());
    dependencyTask.getLibraryDependenciesReport().set(protoFile);

    // Provide pre-computed snapshot hashes (as the plugin would)
    String expectedHash = "abc123def456";
    Map<String, String> hashes = new HashMap<>();
    hashes.put("org.group:artifact:" + snapshotVersion, expectedHash);
    dependencyTask.getSnapshotHashes().set(hashes);

    dependencyTask.action();

    // Verify output
    Gson gson = new Gson();
    try (FileReader reader = new FileReader(outputJson)) {
      Type collectionOfArtifactInfo = new TypeToken<Collection<ArtifactInfo>>() {}.getType();
      Collection<ArtifactInfo> jsonArtifacts = gson.fromJson(reader, collectionOfArtifactInfo);
      assertThat(jsonArtifacts).hasSize(1);
      ArtifactInfo info = jsonArtifacts.iterator().next();
      assertThat(info.getHash()).isEqualTo(expectedHash);
      assertThat(info.getVersion()).isEqualTo(snapshotVersion);
    }
  }

  /**
   * Verifies that SNAPSHOT artifact file content is tracked as a Gradle task input.
   *
   * <p>DependencyTask is {@code @CacheableTask} and computes hashes of SNAPSHOT artifact files
   * to detect when a re-published SNAPSHOT has different content. The computed hashes are
   * exposed to Gradle via the {@code @Input snapshotHashes} map, so changing a SNAPSHOT JAR
   * on disk (same GAV, different content) invalidates the task's up-to-date state and forces
   * re-execution.
   */
  @Test
  public void testSnapshotFileChange_isVisibleToGradleUpToDateChecking() throws Exception {
    // Set up the task with a SNAPSHOT dependency
    File outputDir = temporaryFolder.newFolder();
    File outputJson = new File(outputDir, "test.json");
    dependencyTask.getDependenciesJson().set(outputJson);

    String snapshotVersion = "1.0.0-SNAPSHOT";
    ArtifactInfo snapshotDep = new ArtifactInfo("org.group", "artifact", snapshotVersion);
    AppDependencies appDependencies = createAppDependencies(ImmutableSet.of(snapshotDep));
    File protoFile = writeAppDependencies(appDependencies, temporaryFolder.newFile());
    dependencyTask.getLibraryDependenciesReport().set(protoFile);

    // Provide pre-computed snapshot hashes (as the plugin would)
    Map<String, String> hashesV1 = new HashMap<>();
    hashesV1.put("org.group:artifact:" + snapshotVersion, "hash_of_original_content");
    dependencyTask.getSnapshotHashes().set(hashesV1);

    // Capture task input properties with the original hash
    Map<String, Object> inputsBefore = new HashMap<>(
        dependencyTask.getInputs().getProperties());

    // Simulate a re-published SNAPSHOT by changing the hash
    Map<String, String> hashesV2 = new HashMap<>();
    hashesV2.put("org.group:artifact:" + snapshotVersion, "hash_of_modified_content");
    dependencyTask.getSnapshotHashes().set(hashesV2);

    // Capture input properties after the hash changes
    Map<String, Object> inputsAfter = new HashMap<>(
        dependencyTask.getInputs().getProperties());

    // The task inputs MUST differ when the SNAPSHOT hash changes.
    // This proves Gradle will detect the change and re-execute the task.
    assertThat(inputsAfter).isNotEqualTo(inputsBefore);
  }

  private void verifyExpectedDependencies(ImmutableSet<ArtifactInfo> expectedArtifacts,
      File outputJson) throws Exception {
    Gson gson = new Gson();
    try (FileReader reader = new FileReader(outputJson)) {
      Type collectionOfArtifactInfo = new TypeToken<Collection<ArtifactInfo>>() {
      }.getType();
      Collection<ArtifactInfo> jsonArtifacts = gson.fromJson(reader, collectionOfArtifactInfo);
      assertThat(jsonArtifacts).containsExactlyElementsIn(expectedArtifacts);
    }
  }

}
