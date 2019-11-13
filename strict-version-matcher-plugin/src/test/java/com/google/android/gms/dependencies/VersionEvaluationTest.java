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

package com.google.android.gms.dependencies;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VersionEvaluationTest {
  private static final ArtifactVersion NON_GOOGLE_ARTIFACT = ArtifactVersion.Companion.fromGradleRef("com.notgoogle:randomlib:1.0.0");
  private static final ArtifactVersion NON_GOOGLE_ARTIFACT_HARD_DEP = ArtifactVersion.Companion.fromGradleRef("com.notgoogle:randomlib:[1.0.0]");
  private static final ArtifactVersion GOOGLE_ARTIFACT = ArtifactVersion.Companion.fromGradleRef("com.google.firebase:randomlib:1.0.0");
  private static final ArtifactVersion GOOGLE_ARTIFACT_HARD_DEP = ArtifactVersion.Companion.fromGradleRef("com.google.firebase:randomlib:[1.0.0]");
  private static final Dependency HARD_DEP_ON_GOOGLE = Dependency.Companion.fromArtifactVersions(NON_GOOGLE_ARTIFACT, GOOGLE_ARTIFACT_HARD_DEP);
  private static final Dependency SOFT_DEP_ON_GOOGLE = Dependency.Companion.fromArtifactVersions(NON_GOOGLE_ARTIFACT, GOOGLE_ARTIFACT);
  private static final Dependency SOFT_NON_GOOGLE_DEP = Dependency.Companion.fromArtifactVersions(NON_GOOGLE_ARTIFACT, NON_GOOGLE_ARTIFACT);
  private static final Dependency HARD_NON_GOOGLE_DEP = Dependency.Companion.fromArtifactVersions(NON_GOOGLE_ARTIFACT, NON_GOOGLE_ARTIFACT_HARD_DEP);


  @Test
  public void hardDepOnGoogleLibraryRequiresExactVersion(){
    Assert.assertFalse(HARD_DEP_ON_GOOGLE.isVersionCompatible ("2.0.0"));
    Assert.assertTrue(HARD_DEP_ON_GOOGLE.isVersionCompatible ("1.0.0"));
  }

  @Test
  public void softDepOnGoogleLibraryAcceptsAny(){
    Assert.assertTrue(SOFT_DEP_ON_GOOGLE.isVersionCompatible ("1.0.0"));
    Assert.assertTrue(SOFT_DEP_ON_GOOGLE.isVersionCompatible ("2.0.0"));
  }

  @Test
  public void softDepOnNonGoogleLibraryAcceptsAny(){
    Assert.assertTrue(SOFT_NON_GOOGLE_DEP.isVersionCompatible ("2.0.0"));
    Assert.assertTrue(SOFT_NON_GOOGLE_DEP.isVersionCompatible ("1.0.0"));
  }

  @Test
  public void hardDepOnNonGoogleLibraryAcceptsAny(){
    Assert.assertTrue(HARD_NON_GOOGLE_DEP.isVersionCompatible ("2.0.0"));
    Assert.assertTrue(HARD_NON_GOOGLE_DEP.isVersionCompatible ("1.0.0"));
  }
}
