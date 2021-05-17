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

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class GoogleServicesPluginTest {

  @Test
  public void testNoFlavor() {
    List<String> output = toStringList(GoogleServicesTask.getJsonLocations("release", Collections.emptyList()));
    assertThat(output).contains("src/release/google-services.json");
  }

  @Test
  public void testOneFlavor() {
    List<String> output =
        toStringList(GoogleServicesTask.getJsonLocations("release", Collections.singletonList("flavor")));
    assertThat(output)
        .containsAllOf(
            "google-services.json",
            "src/release/google-services.json",
            "src/flavorRelease/google-services.json",
            "src/flavor/google-services.json",
            "src/flavor/release/google-services.json",
            "src/release/flavor/google-services.json");
  }

  @Test
  public void testMultipleFlavors() {
    List<String> output =
        toStringList(GoogleServicesTask.getJsonLocations("release", Arrays.asList("flavor", "test")));
    assertThat(output)
        .containsAllOf(
            "google-services.json",
            "src/release/google-services.json",
            "src/flavorRelease/google-services.json",
            "src/flavor/google-services.json",
            "src/flavor/release/google-services.json",
            "src/release/flavorTest/google-services.json",
            "src/flavorTest/google-services.json",
            "src/flavorTestRelease/google-services.json",
            "src/flavor/test/release/google-services.json",
            "src/flavor/testRelease/google-services.json");
  }

  // This is necessary because some of the strings are actually groovy string implementations
  // which fail equality tests with java strings during testing
  private static List<String> toStringList(List<String> input) {
    ArrayList<String> strings = new ArrayList<>(input.size());
    for (Object oldString : input) {
      strings.add(oldString.toString());
    }
    return strings;
  }
}
