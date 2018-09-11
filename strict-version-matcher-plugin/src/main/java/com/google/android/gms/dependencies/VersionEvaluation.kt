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

    fun getEvaluator(versionString: String, isSemVer: Boolean): VersionEvaluator {
        return if (versionString.startsWith("[") && versionString.endsWith("]")) {
            ExactVersionEvaluator(versionString.substring(1, versionString.length - 1))
        } else if (isSemVer) {
            SemVerVersionEvaluator(versionString)
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
        internal var versionInfo: SemVerInfo

        init {
            versionInfo = SemVerInfo.parseString(versionString)
        }

        override fun isCompatible(version: String): Boolean {
            val (major, minor) = SemVerInfo.parseString(version)
            return major == versionInfo.major && minor >= versionInfo.minor
        }
    }
}

