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

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.internal.impldep.com.fasterxml.jackson.core.json.JsonWriteContext;
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

  private static final Charset UTF_8 = StandardCharsets.UTF_8;
  private static final String BASE_DIR = "src/test/resources";
  private static final String LINE_BREAK = System.getProperty("line.separator");
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private Project project;
  private LicensesTask licensesTask;

  @Before
  public void setUp() throws IOException {
    File outputDir = temporaryFolder.newFolder();
    File outputLicenses = new File(outputDir, "testLicenses");
    File outputMetadata = new File(outputDir, "testMetadata");

    project = ProjectBuilder.builder().withProjectDir(new File(BASE_DIR)).build();
    licensesTask = project.getTasks().create("generateLicenses", LicensesTask.class);

    licensesTask.setRawResourceDir(outputDir);
    licensesTask.setLicenses(outputLicenses);
    licensesTask.setLicensesMetadata(outputMetadata);
  }

  @Test
  public void testInitOutputDir() {
    licensesTask.initOutputDir();

    assertTrue(licensesTask.getRawResourceDir().exists());
  }

  @Test
  public void testInitLicenseFile() throws IOException {
    licensesTask.initLicenseFile();

    assertTrue(licensesTask.getLicenses().exists());
    assertEquals(0, Files.size(licensesTask.getLicenses().toPath()));
  }

  @Test
  public void testInitLicensesMetadata() throws IOException {
    licensesTask.initLicensesMetadata();

    assertTrue(licensesTask.getLicensesMetadata().exists());
    assertEquals(0, Files.size(licensesTask.getLicensesMetadata().toPath()));
  }

  @Test
  public void testIsGranularVersion_True() {
    String versionTrue = "14.6.0";
    assertTrue(LicensesTask.isGranularVersion(versionTrue));
  }

  @Test
  public void testIsGranularVersion_False() {
    String versionFalse = "11.4.0";
    assertFalse(LicensesTask.isGranularVersion(versionFalse));
  }

  @Test
  public void testAddLicensesFromPom() throws IOException {
    File deps1 = getResourceFile("dependencies/groupA/deps1.pom");
    String name1 = "deps1";
    String group1 = "groupA";
    licensesTask.addLicensesFromPom(deps1, group1, name1);

    String content = new String(Files.readAllBytes(licensesTask.getLicenses().toPath()), UTF_8);
    String expected = "http://www.opensource.org/licenses/mit-license.php" + LINE_BREAK;
    assertTrue(licensesTask.licensesMap.containsKey("groupA:deps1"));
    assertEquals(expected, content);
  }

  @Test
  public void testAddLicensesFromPom_withoutDuplicate() throws IOException {
    File deps1 = getResourceFile("dependencies/groupA/deps1.pom");
    String name1 = "deps1";
    String group1 = "groupA";
    licensesTask.addLicensesFromPom(deps1, group1, name1);

    File deps2 = getResourceFile("dependencies/groupB/bcd/deps2.pom");
    String name2 = "deps2";
    String group2 = "groupB";
    licensesTask.addLicensesFromPom(deps2, group2, name2);

    String content = new String(Files.readAllBytes(licensesTask.getLicenses().toPath()), UTF_8);
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
  public void testAddLicensesFromPom_withMultiple() throws IOException {
    File deps1 = getResourceFile("dependencies/groupA/deps1.pom");
    String name1 = "deps1";
    String group1 = "groupA";
    licensesTask.addLicensesFromPom(deps1, group1, name1);

    File deps2 = getResourceFile("dependencies/groupE/deps5.pom");
    String name2 = "deps5";
    String group2 = "groupE";
    licensesTask.addLicensesFromPom(deps2, group2, name2);

    String content = new String(Files.readAllBytes(licensesTask.getLicenses().toPath()), UTF_8);
    String expected =
        "http://www.opensource.org/licenses/mit-license.php"
            + LINE_BREAK
            + "https://www.apache.org/licenses/LICENSE-2.0"
            + LINE_BREAK;

    assertThat(licensesTask.licensesMap.size(), is(3));
    assertTrue(licensesTask.licensesMap.containsKey("groupA:deps1"));
    assertTrue(licensesTask.licensesMap.containsKey("groupE:deps5 MIT License"));
    assertTrue(licensesTask.licensesMap.containsKey("groupE:deps5 Apache License 2.0"));
    assertEquals(expected, content);
  }

  @Test
  public void testAddLicensesFromPom_withDuplicate() throws IOException {
    File deps1 = getResourceFile("dependencies/groupA/deps1.pom");
    String name1 = "deps1";
    String group1 = "groupA";
    licensesTask.addLicensesFromPom(deps1, group1, name1);

    File deps2 = getResourceFile("dependencies/groupA/deps1.pom");
    String name2 = "deps1";
    String group2 = "groupA";
    licensesTask.addLicensesFromPom(deps2, group2, name2);

    String content = new String(Files.readAllBytes(licensesTask.getLicenses().toPath()), UTF_8);
    String expected = "http://www.opensource.org/licenses/mit-license.php" + LINE_BREAK;

    assertThat(licensesTask.licensesMap.size(), is(1));
    assertTrue(licensesTask.licensesMap.containsKey("groupA:deps1"));
    assertEquals(expected, content);
  }

  private File getResourceFile(String resourcePath) {
    return new File(getClass().getClassLoader().getResource(resourcePath).getFile());
  }

  @Test
  public void testGetBytesFromInputStream_throwException() throws IOException {
    InputStream inputStream = mock(InputStream.class);
    when(inputStream.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new IOException());
    try {
      LicensesTask.getBytesFromInputStream(inputStream, 1, 1);
      fail("This test should throw Exception.");
    } catch (RuntimeException e) {
      assertEquals("Failed to read license text.", e.getMessage());
    }
  }

  @Test
  public void testGetBytesFromInputStream_normalText() {
    String test = "test";
    InputStream inputStream = new ByteArrayInputStream(test.getBytes(UTF_8));
    String content = new String(LicensesTask.getBytesFromInputStream(inputStream, 1, 1), UTF_8);
    assertEquals("e", content);
  }

  @Test
  public void testGetBytesFromInputStream_specialCharacters() {
    String test = "Copyright © 1991-2017 Unicode";
    InputStream inputStream = new ByteArrayInputStream(test.getBytes(UTF_8));
    String content = new String(LicensesTask.getBytesFromInputStream(inputStream, 4, 18), UTF_8);
    assertEquals("right © 1991-2017", content);
  }

  @Test
  public void testAddGooglePlayServiceLicenses() throws IOException {
    File tempOutput = new File(licensesTask.getRawResourceDir(), "dependencies/groupC");
    tempOutput.mkdirs();
    createLicenseZip(tempOutput.getPath() + "play-services-foo-license.aar");
    File artifact = new File(tempOutput.getPath() + "play-services-foo-license.aar");
    licensesTask.addGooglePlayServiceLicenses(artifact);

    String content = new String(Files.readAllBytes(licensesTask.getLicenses().toPath()), UTF_8);
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
    File groupC = new File(licensesTask.getRawResourceDir(), "dependencies/groupC");
    groupC.mkdirs();
    createLicenseZip(groupC.getPath() + "/play-services-foo-license.aar");
    File artifactFoo = new File(groupC.getPath() + "/play-services-foo-license.aar");

    File groupD = new File(licensesTask.getRawResourceDir(), "dependencies/groupD");
    groupD.mkdirs();
    createLicenseZip(groupD.getPath() + "/play-services-bar-license.aar");
    File artifactBar = new File(groupD.getPath() + "/play-services-bar-license.aar");

    licensesTask.addGooglePlayServiceLicenses(artifactFoo);
    licensesTask.addGooglePlayServiceLicenses(artifactBar);

    String content = new String(Files.readAllBytes(licensesTask.getLicenses().toPath()), UTF_8);
    String expected = "safeparcel" + LINE_BREAK + "JSR 305" + LINE_BREAK;
    assertEquals(expected, content);
    assertThat(licensesTask.googleServiceLicenses.size(), is(2));
    assertTrue(licensesTask.googleServiceLicenses.contains("safeparcel"));
    assertTrue(licensesTask.googleServiceLicenses.contains("JSR 305"));
    assertThat(licensesTask.licensesMap.size(), is(2));
    assertTrue(licensesTask.licensesMap.containsKey("safeparcel"));
    assertTrue(licensesTask.licensesMap.containsKey("JSR 305"));
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
  public void testAppendLicense() throws IOException {
    licensesTask.appendDependency(
        new LicensesTask.Dependency("license1", "license1"),
        "test".getBytes(UTF_8));

    String expected = "test" + LINE_BREAK;
    String content = new String(Files.readAllBytes(licensesTask.getLicenses().toPath()), UTF_8);
    assertTrue(licensesTask.licensesMap.containsKey("license1"));
    assertEquals(expected, content);
  }

  @Test
  public void testWriteMetadata() throws IOException {
    LicensesTask.Dependency dep1 = new LicensesTask.Dependency("test:foo", "Dependency 1");
    LicensesTask.Dependency dep2 = new LicensesTask.Dependency("test:bar", "Dependency 2");
    licensesTask.licensesMap.put(dep1.getKey(), dep1.buildLicensesMetadata("0:4"));
    licensesTask.licensesMap.put(dep2.getKey(), dep2.buildLicensesMetadata("6:10"));
    licensesTask.writeMetadata();

    String expected = "0:4 Dependency 1" + LINE_BREAK + "6:10 Dependency 2" + LINE_BREAK;
    String content = new String(Files.readAllBytes(licensesTask.getLicensesMetadata().toPath()),
        UTF_8);
    assertEquals(expected, content);
  }

  @Test
  public void testDependenciesWithNameDuplicatedNames() throws IOException {
    File deps6 = getResourceFile("dependencies/groupF/deps6.pom");
    String name1 = "deps6";
    String group1 = "groupF";
    licensesTask.addLicensesFromPom(deps6, group1, name1);

    File deps7 = getResourceFile("dependencies/groupF/deps7.pom");
    String name2 = "deps7";
    String group2 = "groupF";
    licensesTask.addLicensesFromPom(deps7, group2, name2);

    assertThat(licensesTask.licensesMap.size(), is(2));
    assertTrue(licensesTask.licensesMap.containsKey("groupF:deps6"));
    assertTrue(licensesTask.licensesMap.containsKey("groupF:deps7"));
  }

  @Test
  public void action_absentDependencies_rendersAbsentData() throws Exception {
    File dependenciesJson = temporaryFolder.newFile();
    ArtifactInfo[] artifactInfoArray = new ArtifactInfo[] { DependencyUtil.ABSENT_ARTIFACT };
    Gson gson = new Gson();
    try (FileWriter writer = new FileWriter(dependenciesJson)) {
      gson.toJson(artifactInfoArray, writer);
    }
    licensesTask.getDependenciesJson().set(dependenciesJson);

    licensesTask.action();

    String line;
    try (BufferedReader reader = new BufferedReader(new FileReader(licensesTask.getLicenses()))) {
      line = reader.readLine();
    }
    assertEquals(line, LicensesTask.ABSENT_DEPENDENCY_TEXT);
  }

  @Test
  public void testThirdPartyLicenses() throws IOException {
    File thirdPartyLicensesDir = new File(BASE_DIR + "/thirdPartyLicenses");
    File[] thirdPartyLicenses = thirdPartyLicensesDir.listFiles();
    assert thirdPartyLicenses != null;
    licensesTask.setThirdPartyLicenses(thirdPartyLicenses);
    licensesTask.addThirdPartyLicenses();

    String expected = "test" + LINE_BREAK;
    String content = new String(Files.readAllBytes(thirdPartyLicenses[0].toPath()), UTF_8);

    assertThat(licensesTask.licensesMap.size(), is(1));
    assertTrue(licensesTask.licensesMap.containsKey("license1"));
    assertEquals(expected, content);
  }
}
