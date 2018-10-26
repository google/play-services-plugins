/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gms.googleservices;

import static com.google.common.truth.Truth.assertThat;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.junit.Test;

public class GoogleServicesPluginTest {

  @Test
  public void testNoFlavor() {
    List<String> output = toStringList(GoogleServicesPlugin.getJsonLocations("release", null));
    assertThat(output).contains("src/release");
  }

  @Test
  public void testOneFlavor() {
    List<String> output =
        toStringList(GoogleServicesPlugin.getJsonLocations("flavor/release", null));
    assertThat(output)
        .containsAllOf(
            "src/release",
            "src/flavorRelease",
            "src/flavor",
            "src/flavor/release",
            "src/release/flavor");
  }

  @Test
  public void testMultipleFlavors() {
    List<String> output =
        toStringList(GoogleServicesPlugin.getJsonLocations("flavorTest/release", null));
    assertThat(output)
        .containsAllOf(
            "src/release",
            "src/flavorRelease",
            "src/flavor",
            "src/flavor/release",
            "src/release/flavorTest",
            "src/flavorTest",
            "src/flavorTestRelease",
            "src/flavor/test/release",
            "src/flavor/testRelease");
  }

  // This is neccesary because some of the strings are actually groovy string implementations
  // which fail equality tests with java strings during testing
  private static List<String> toStringList(List<String> input) {
    ArrayList<String> strings = new ArrayList<String>(input.size());
    for (Object oldString : input) {
      strings.add(oldString.toString());
    }
    return strings;
  }
}
