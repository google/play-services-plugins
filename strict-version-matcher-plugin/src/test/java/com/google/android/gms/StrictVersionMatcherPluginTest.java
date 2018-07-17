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

package com.google.android.gms;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.gms.StrictVersionMatcherPlugin.Version;
import com.google.android.gms.StrictVersionMatcherPlugin.VersionRange;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.junit.Test;

public class StrictVersionMatcherPluginTest {

  @Test
  public void testVersionCompare() {
    assertThat(StrictVersionMatcherPlugin.versionCompare("1.0.0", "2.0.0")).isLessThan(0);
    assertThat(StrictVersionMatcherPlugin.versionCompare("1.10.1", "1.9.0")).isGreaterThan(0);
    assertThat(StrictVersionMatcherPlugin.versionCompare("1.0.0", "1.0.0")).isEqualTo(0);
  }

  @Test
  public void closedNonRangeVersionRangeTest() {
    VersionRange range = VersionRange.fromString("[1.2.3]");
    Version version = Version.fromString("1.2.3-SNAPSHOT");
    Version highVersion = Version.fromString("5.3.2");
    assertThat(range.versionInRange(version)).isTrue();
    assertThat(range.versionInRange(highVersion)).isFalse();
  }
}
