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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test for {@link LicensesTask#isGoogleServices(String)}
 */
@RunWith(Parameterized.class)
public class GoogleServicesLicenseTest {

  @Parameter(0)
  public String inputGroup;
  @Parameter(1)
  public Boolean expectedResult;

  @Parameters
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][]{
            {"com.google.android.gms", true},
            {"com.google.firebase", true},
            {"com.example", false},
        });
  }

  @Test
  public void test() {
    assertEquals(expectedResult, LicensesTask.isGoogleServices(inputGroup));
  }
}
