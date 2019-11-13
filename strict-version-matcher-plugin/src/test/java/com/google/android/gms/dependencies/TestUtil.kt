package com.google.android.gms.dependencies

// These are constants used to refer to artifacts and constants across all of the test classes to increase readability.

@JvmField val ARTIFACT_A_100 = ArtifactVersion.fromGradleRef("com.google.firebase:artA:1.0.0")
@JvmField val ARTIFACT_A_200 = ArtifactVersion.fromGradleRef("com.google.firebase:artA:2.0.0")
@JvmField val ARTIFACT_B_100 = ArtifactVersion.fromGradleRef("com.google.firebase:artB:1.0.0")
@JvmField val ARTIFACT_B_200 = ArtifactVersion.fromGradleRef("com.google.firebase:artB:2.0.0")
@JvmField val ARTIFACT_B_300 = ArtifactVersion.fromGradleRef("com.google.firebase:artB:3.0.0")
@JvmField val ARTIFACT_C_100 = ArtifactVersion.fromGradleRef("com.google.android.gms:artC:1.0.0")
@JvmField val ARTIFACT_C_200 = ArtifactVersion.fromGradleRef("com.google.android.gms:artC:2.0.0")
@JvmField val ARTIFACT_D_100 = ArtifactVersion.fromGradleRef("com.google.android.gms:artD:1.0.0")
@JvmField val ARTIFACT_D_200 = ArtifactVersion.fromGradleRef("com.google.android.gms:artD:2.0.0")

@JvmField val ART_A_100_TO_ART_B_100 = Dependency.fromArtifactVersions(ARTIFACT_A_100, ARTIFACT_B_100)
@JvmField val ART_A_200_TO_ART_B_200 = Dependency.fromArtifactVersions(ARTIFACT_A_200, ARTIFACT_B_200)
@JvmField val ART_A_100_TO_ART_C_100 = Dependency.fromArtifactVersions(ARTIFACT_A_100, ARTIFACT_C_100)
@JvmField val ART_A_100_TO_ART_C_200 = Dependency.fromArtifactVersions(ARTIFACT_A_100, ARTIFACT_C_200)
@JvmField val ART_B_100_TO_ART_D_100 = Dependency.fromArtifactVersions(ARTIFACT_B_100, ARTIFACT_D_100)
@JvmField val ART_C_100_TO_ART_D_100 = Dependency.fromArtifactVersions(ARTIFACT_C_100, ARTIFACT_D_100)
@JvmField val ART_C_200_TO_ART_D_200 = Dependency.fromArtifactVersions(ARTIFACT_C_200, ARTIFACT_D_200)
