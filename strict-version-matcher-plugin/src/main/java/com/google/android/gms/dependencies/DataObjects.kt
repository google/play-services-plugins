package com.google.android.gms.dependencies

import java.util.logging.Logger

// Utilizing Kotlin to eliminate boilerplate of data classes.

// TODO: Javadoc.
// TODO: Unit tests.
// TODO: Code formatting.
// TODO: Support SemVer qualifiers.
data class Node(val child: Node?, val dependency: Dependency)

data class Artifact(val groupId: String, val artifactId: String) {
    fun getGradleRef(): String {
        return "${groupId}:${artifactId}"
    }

    companion object {
        fun fromGradleRef(referenceString: String): Artifact {
            val stringSplit = referenceString.split(":")
            if (stringSplit.size < 2) {
                throw IllegalArgumentException(
                        "Invalid Gradle reference string: $referenceString");
            }
            return Artifact(groupId = stringSplit[0], artifactId = stringSplit[1])
        }
    }
}

data class ArtifactVersion(val groupId: String, val artifactId: String,
                           val version: String) {
    fun getArtifact(): Artifact {
        return Artifact(groupId = groupId, artifactId = artifactId)
    }

    fun getGradleRef(): String {
        return "${groupId}:${artifactId}:${version}"
    }

    companion object {
        fun fromGradleRef(referenceString: String): ArtifactVersion {
            val stringSplit = referenceString.split(":")
            if (stringSplit.size < 3) {
                throw IllegalArgumentException("Invalid Gradle reference string: $referenceString");
            }
            return ArtifactVersion(groupId = stringSplit[0],
                    artifactId = stringSplit[1], version = stringSplit[2])
        }

        fun fromGradleRefOrNull(referenceString: String): ArtifactVersion? {
            val stringSplit = referenceString.split(":")
            if (stringSplit.size < 3) {
                return null
            }
            return ArtifactVersion(groupId = stringSplit[0],
                    artifactId = stringSplit[1], version = stringSplit[2])
        }
    }
}

data class Dependency(val fromArtifactVersion: ArtifactVersion, val toArtifact: Artifact,
                      val toArtifactVersionString: String) {
    private val logger: Logger = Logger.getLogger("Dependency")
    private val versionEvaluator: VersionEvaluator

    init {
        val isSemver = toArtifact.groupId.equals("com.google.android.gms") ||
                toArtifact.groupId.equals("com.google.firebase");
        versionEvaluator = VersionEvaluators.getEvaluator(toArtifactVersionString, isSemver)
    }

    fun isVersionCompatible(versionString: String): Boolean {
        if (versionEvaluator.isCompatible(versionString)) {
            return true
        }
        logger.fine("Failed comparing ${this.toArtifactVersionString} with" +
                " $versionString using ${versionEvaluator.javaClass}")
        return false
    }

    fun getDisplayString(): String {
        return fromArtifactVersion.getGradleRef() + " -> " +
                toArtifact.getGradleRef() + "@" + toArtifactVersionString
    }

    companion object {
        fun fromArtifactVersions(fromArtifactVersion: ArtifactVersion,
                                 toArtifactVersion: ArtifactVersion): Dependency {
            return Dependency(fromArtifactVersion = fromArtifactVersion,
                    toArtifact = toArtifactVersion.getArtifact(),
                    toArtifactVersionString = toArtifactVersion.version)
        }
    }
}

data class ArtifactDependencyManager(val artifact: Artifact) {
    var dependencies: HashSet<Dependency> = HashSet()
    fun addDependency(dependency: Dependency) {
        // TODO: Check for conflicting duplicate adds and fail.
        dependencies.add(dependency);
    }

    fun getExtendedToString(): String {
        val stringBuilder = StringBuilder(toString())
        for (dep in dependencies) {
            stringBuilder.append(",")
            stringBuilder.append(dep.toArtifactVersionString)
        }
        return stringBuilder.toString()
    }
}

data class SemVerInfo(val major: Int, val minor: Int, val patch: Int) {
    companion object {
        fun parseString(versionString: String): SemVerInfo {
            val version = versionString.trim();
            val parts = version.split(".")
            if (parts.size != 3) {
                throw IllegalArgumentException(
                        "Version string didn't have 3 parts divided by periods: $versionString")
            }
            val major = Integer.valueOf(parts[0])
            val minor = Integer.valueOf(parts[1])

            var patchString = parts[2];
            val dashIndex = patchString.indexOf("-")
            if (dashIndex != -1) {
                patchString = patchString.substring(0, dashIndex)
            }
            // TOOD: Deal with everything that might exist after the dash.
            val patch = Integer.valueOf(patchString)
            return SemVerInfo(major = major, minor = minor, patch = patch)
        }
    }
}

