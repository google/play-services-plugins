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
    dependencyTask.setOutputDir(outputDir);
    dependencyTask.setOutputFile(outputJson);
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
    dependencyTask.setOutputDir(outputDir);
    dependencyTask.setOutputFile(outputJson);
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
    dependencyTask.setOutputDir(outputDir);
    dependencyTask.setOutputFile(outputJson);
    ImmutableSet<ArtifactInfo> expectedArtifacts = ImmutableSet.of(DependencyUtil.ABSENT_ARTIFACT);

    dependencyTask.action();

    verifyExpectedDependencies(expectedArtifacts, outputJson);
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
