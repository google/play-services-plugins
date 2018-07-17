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

import static org.gradle.internal.impldep.org.testng.Assert.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link LicensesTask} */
@RunWith(JUnit4.class)
public class LicensesTaskTest {
  private static final String BASE_DIR = "src/test/resources";
  private static final String LINE_BREAK = System.getProperty("line.separator");
  private Project project;
  private LicensesTask licensesTask;

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Before
  public void setUp() throws IOException {
    File outputDir = temporaryFolder.newFolder();
    File outputLicenses = new File(outputDir, "testLicenses");
    File outputMetadata = new File(outputDir, "testMetadata");

    project = ProjectBuilder.builder().withProjectDir(new File(BASE_DIR)).build();
    licensesTask = project.getTasks().create("generateLicenses", LicensesTask.class);

    licensesTask.outputDir = outputDir;
    licensesTask.licenses = outputLicenses;
    licensesTask.licensesMetadata = outputMetadata;
  }

  private void createLicenseZip(String name) throws IOException {
    File zipFile = new File(name);
    ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zipFile));
    File input = new File(BASE_DIR + "/sampleLicenses");
    for (File file : input.listFiles()) {
      ZipEntry entry = new ZipEntry(file.getName());
      byte[] bytes = Files.readAllBytes(file.toPath());
      output.putNextEntry(entry);
      output.write(bytes, 0, bytes.length);
      output.closeEntry();
    }
    output.close();
  }

  @Test
  public void testInitOutputDir() {
    licensesTask.initOutputDir();

    assertTrue(licensesTask.outputDir.exists());
  }

  @Test
  public void testInitLicenseFile() throws IOException {
    licensesTask.initLicenseFile();

    assertTrue(licensesTask.licenses.exists());
    assertEquals(0, Files.size(licensesTask.licenses.toPath()));
  }

  @Test
  public void testInitLicensesMetadata() throws IOException {
    licensesTask.initLicensesMetadata();

    assertTrue(licensesTask.licensesMetadata.exists());
    assertEquals(0, Files.size(licensesTask.licensesMetadata.toPath()));
  }

  @Test
  public void testIsGranularVersion_True() {
    String versionTrue = "14.6.0";
    assertTrue(licensesTask.isGranularVersion(versionTrue));
  }

  @Test
  public void testIsGranularVersion_False() {
    String versionFalse = "11.4.0";
    assertFalse(licensesTask.isGranularVersion(versionFalse));
  }

  @Test
  public void testAddLicensesFromPom() throws IOException {
    File deps1 = new File("dependencies/groupA/deps1.txt");
    String name1 = "deps1";
    String group1 = "groupA";
    licensesTask.addLicensesFromPom(deps1, name1, group1);

    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()));
    String expected = "http://www.opensource.org/licenses/mit-license.php" + LINE_BREAK;
    assertTrue(licensesTask.licensesMap.containsKey("groupA:deps1"));
    assertEquals(expected, content);
  }

  @Test
  public void testAddLicensesFromPom_withoutDuplicate() throws IOException {
    File deps1 = new File("dependencies/groupA/deps1.txt");
    String name1 = "deps1";
    String group1 = "groupA";
    licensesTask.addLicensesFromPom(deps1, name1, group1);

    File deps2 = new File("dependencies/groupB/deps2.txt");
    String name2 = "deps2";
    String group2 = "groupB";
    licensesTask.addLicensesFromPom(deps2, name2, group2);

    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()));
    String expected =
        "http://www.opensource.org/licenses/mit-license.php"
            + LINE_BREAK
            + "https://www.apache.org/licenses/LICENSE-2.0"
            + LINE_BREAK;

    assertThat(licensesTask.licensesMap.size(), is(2));
    assertTrue(licensesTask.licensesMap.containsKey("groupA:deps1"));
    assertTrue(licensesTask.licensesMap.containsKey("groupB:deps2"));
    assertEquals(expected, content);
  }

  @Test
  public void testAddLicensesFromPom_withDuplicate() throws IOException {
    File deps1 = new File("dependencies/groupA/deps1.txt");
    String name1 = "deps1";
    String group1 = "groupA";
    licensesTask.addLicensesFromPom(deps1, name1, group1);

    File deps2 = new File("dependencies/groupA/deps1.txt");
    String name2 = "deps1";
    String group2 = "groupA";
    licensesTask.addLicensesFromPom(deps2, name2, group2);

    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()));
    String expected = "http://www.opensource.org/licenses/mit-license.php" + LINE_BREAK;

    assertThat(licensesTask.licensesMap.size(), is(1));
    assertTrue(licensesTask.licensesMap.containsKey("groupA:deps1"));
    assertEquals(expected, content);
  }

  @Test
  public void testGetTextFromInputStream_throwException() throws IOException {
    InputStream inputStream = mock(InputStream.class);
    when(inputStream.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new IOException());
    try {
      licensesTask.getTextFromInputStream(inputStream, 1, 1);
      fail("This test should throw Exception.");
    } catch (RuntimeException e) {
      assertEquals("Failed to read license text.", e.getMessage());
    }
  }

  @Test
  public void testGetTextFromInputStream_normalText() {
    String test = "test";
    InputStream inputStream = new ByteArrayInputStream(test.getBytes());
    String content = licensesTask.getTextFromInputStream(inputStream, 1, 1);
    assertEquals("e", content);
  }

  @Test
  public void testGetTextFromInputStream_specialCharacters() {
    String test = "Copyright © 1991-2017 Unicode";
    InputStream inputStream = new ByteArrayInputStream(test.getBytes());
    String content = licensesTask.getTextFromInputStream(inputStream, 4, 18);
    assertEquals("right © 1991-2017", content);
  }

  @Test
  public void testAddGooglePlayServiceLicenses() throws IOException {
    File tempOutput = new File(licensesTask.outputDir, "dependencies/groupC");
    tempOutput.mkdirs();
    createLicenseZip(tempOutput.getPath() + "play-services-foo-license.aar");
    File artifact = new File(tempOutput.getPath() + "play-services-foo-license.aar");
    licensesTask.addGooglePlayServiceLicenses(artifact);

    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()));
    String expected = "safeparcel" + LINE_BREAK + "JSR 305" + LINE_BREAK;
    assertEquals(expected, content);
    assertThat(licensesTask.googleServiceLicenses.size(), is(2));
    assertTrue(licensesTask.googleServiceLicenses.contains("safeparcel"));
    assertTrue(licensesTask.googleServiceLicenses.contains("JSR 305"));
    assertThat(licensesTask.licensesMap.size(), is(2));
    assertTrue(licensesTask.licensesMap.containsKey("safeparcel"));
    assertTrue(licensesTask.licensesMap.containsKey("JSR 305"));
  }

  @Test
  public void testAddGooglePlayServiceLicenses_withoutDuplicate() throws IOException {
    File groupC = new File(licensesTask.outputDir, "dependencies/groupC");
    groupC.mkdirs();
    createLicenseZip(groupC.getPath() + "/play-services-foo-license.aar");
    File artifactFoo = new File(groupC.getPath() + "/play-services-foo-license.aar");

    File groupD = new File(licensesTask.outputDir, "dependencies/groupD");
    groupD.mkdirs();
    createLicenseZip(groupD.getPath() + "/play-services-bar-license.aar");
    File artifactBar = new File(groupD.getPath() + "/play-services-bar-license.aar");

    licensesTask.addGooglePlayServiceLicenses(artifactFoo);
    licensesTask.addGooglePlayServiceLicenses(artifactBar);

    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()));
    String expected = "safeparcel" + LINE_BREAK + "JSR 305" + LINE_BREAK;
    assertEquals(expected, content);
    assertThat(licensesTask.googleServiceLicenses.size(), is(2));
    assertTrue(licensesTask.googleServiceLicenses.contains("safeparcel"));
    assertTrue(licensesTask.googleServiceLicenses.contains("JSR 305"));
    assertThat(licensesTask.licensesMap.size(), is(2));
    assertTrue(licensesTask.licensesMap.containsKey("safeparcel"));
    assertTrue(licensesTask.licensesMap.containsKey("JSR 305"));
  }

  @Test
  public void testAppendLicense() throws IOException {
    licensesTask.appendLicense("license1", 4, "test");

    String expected = "test" + LINE_BREAK;
    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()));
    assertTrue(licensesTask.licensesMap.containsKey("license1"));
    assertEquals(expected, content);
  }

  @Test
  public void testWriteMetadata() throws IOException {
    licensesTask.licensesMap.put("license1", "0:4");
    licensesTask.licensesMap.put("license2", "6:10");
    licensesTask.writeMetadata();

    String expected = "0:4 license1" + LINE_BREAK + "6:10 license2" + LINE_BREAK;
    String content = new String(Files.readAllBytes(licensesTask.licensesMetadata.toPath()));
    assertEquals(expected, content);
  }
}
