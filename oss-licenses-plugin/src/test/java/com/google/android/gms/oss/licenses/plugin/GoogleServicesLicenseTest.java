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
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Test for {@link LicensesTask#isGoogleServices(String, String)}
 */
@RunWith(Parameterized.class)
public class GoogleServicesLicenseTest {
    private static final String BASE_DIR = "src/test/resources";
    private LicensesTask licensesTask;

    @Parameters
    public static Iterable<Object[]> data() {
        return Arrays.asList(
                new Object[][]{
                        {"com.google.android.gms", "play-services-foo", true},
                        {"com.google.firebase", "firebase-bar", true},
                        {"com.example", "random", false},
                });
    }

    @Parameter()
    public String inputGroup;

    @Parameter(1)
    public String inputArtifactName;

    @Parameter(2)
    public Boolean expectedResult;

    @Before
    public void setUp() {
        Project project = ProjectBuilder.builder().withProjectDir(new File(BASE_DIR)).build();
        licensesTask = project.getTasks().create("generateLicenses", LicensesTask.class);
    }

    @Test
    public void test() {
        assertEquals(expectedResult, licensesTask.isGoogleServices(inputGroup, inputArtifactName));
    }
}
