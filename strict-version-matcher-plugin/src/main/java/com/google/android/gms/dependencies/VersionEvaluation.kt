package com.google.android.gms.dependencies

interface VersionEvaluator {
    fun isCompatible(version: String): Boolean
}

/**
 * TODO: Javadoc.
 * TODO: Unit tests.
 * TODO: Code formatting.
 */
object VersionEvaluators {

    fun getEvaluator(versionString: String, enableStrictMatching: Boolean): VersionEvaluator {
        val hasVersionRange = versionString.indexOf(",") > 0 || versionString.indexOf(")") > 0 ||
                versionString.indexOf("(") > 0
        return if (versionString.startsWith("[") && versionString.endsWith("]")) {
            ExactVersionEvaluator(versionString.substring(1, versionString.length - 1))
        } else if (enableStrictMatching && !hasVersionRange) {
            // TODO: Re-enable SemVer validator.
            // SemVerVersionEvaluator(versionString)
            AlwaysCompatibleEvaluator()
        } else {
            AlwaysCompatibleEvaluator()
        }

    }

    class AlwaysCompatibleEvaluator : VersionEvaluator {
        override fun isCompatible(version: String): Boolean {
            return true
        }
    }

    class ExactVersionEvaluator(internal var versionString: String) : VersionEvaluator {
        override fun isCompatible(version: String): Boolean {
            return version == versionString
        }
    }

    class SemVerVersionEvaluator(versionString: String) : VersionEvaluator {
        internal var versionInfo = SemVerInfo.parseString(versionString)

        override fun isCompatible(version: String): Boolean {
            val (major, minor) = SemVerInfo.parseString(version)
            return major == versionInfo.major && minor >= versionInfo.minor
        }
    }
}

