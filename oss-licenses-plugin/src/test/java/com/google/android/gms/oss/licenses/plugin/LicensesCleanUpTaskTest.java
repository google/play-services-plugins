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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testAction() throws IOException {
    File testDir = temporaryFolder.newFolder();

    // Set up a generated directory with normal expected contents
    File generatedDir = new File(testDir, "generated");
    File dependencyDir = new File(generatedDir, "dependency");
    assertTrue(dependencyDir.mkdirs());
    File dependencyFile = new File(dependencyDir, "dependency.json");
    assertTrue(dependencyFile.createNewFile());

    File licensesDir = new File(generatedDir, "res/raw");
    assertTrue(licensesDir.mkdirs());
    assertTrue(new File(licensesDir, "third_party_licenses").createNewFile());
    assertTrue(new File(licensesDir, "third_party_license_metadata").createNewFile());

    // Create a licenses clean up task
    Project project = ProjectBuilder.builder().withProjectDir(testDir).build();
    LicensesCleanUpTask task =
        project.getTasks().create("licensesCleanUp", LicensesCleanUpTask.class);
    task.getGeneratedDirectory().set(generatedDir);

    // Run the task action
    task.action();

    // Ensure the directory is deleted
    assertFalse(generatedDir.exists());
  }
}
