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

import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link LicensesCleanUpTask} */
@RunWith(JUnit4.class)
public class LicensesCleanUpTaskTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testAction() throws IOException {
    File testDir = temporaryFolder.newFolder();

    File dependencyDir = new File(testDir, "dependency");
    dependencyDir.mkdir();

    File dependencyFile = new File(dependencyDir, "dependency.json");

    File licensesDir = new File(testDir, "raw");
    licensesDir.mkdir();

    File licensesFile = new File(licensesDir, "third_party_licenses");
    File metadataFile = new File(licensesDir, "third_party_license_metadata");

    Project project = ProjectBuilder.builder().withProjectDir(testDir).build();
    LicensesCleanUpTask task =
        project.getTasks().create("licensesCleanUp", LicensesCleanUpTask.class);
    task.dependencyDir = dependencyDir;
    task.dependencyFile = dependencyFile;
    task.licensesDir = licensesDir;
    task.licensesFile = licensesFile;
    task.metadataFile = metadataFile;

    task.action();
    assertFalse(task.dependencyFile.exists());
    assertFalse(task.dependencyDir.exists());
    assertFalse(task.licensesFile.exists());
    assertFalse(task.metadataFile.exists());
    assertFalse(task.licensesDir.exists());
  }
}
