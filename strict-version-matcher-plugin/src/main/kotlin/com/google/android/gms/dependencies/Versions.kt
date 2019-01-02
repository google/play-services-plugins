package com.google.android.gms.dependencies

import java.util.regex.Pattern

/**
 * Allow storing, comparing, and parsing of SemVer version strings.
 */
data class SemVerVersionInfo(val major: Int, val minor: Int, val patch: Int) {
    companion object {
        /**
         * Parse a SemVer formatted string to {SemVerVersionInfo}.
         *
         * Current implementation is rudimentary, but supports the Google Play
         * services current use.
         *
         * @param[versionString] Three-part Semver string to convert.
         */
        fun parseString(versionString: String): SemVerVersionInfo {
            val version = versionString.trim()
            val parts = version.split(".")
            if (parts.size != 3) {
                throw IllegalArgumentException("versionString didn't have 3 parts divided by periods.")
            }
            val major = Integer.valueOf(parts[0])
            val minor = Integer.valueOf(parts[1])

            var patchString = parts[2]
            val dashIndex = patchString.indexOf("-")
            if (dashIndex != -1) {
                patchString = patchString.substring(0, dashIndex)
            }
            // TODO: Update to account for qualifiers to the version.
            val patch = Integer.valueOf(patchString)
            return SemVerVersionInfo(major = major, minor = minor, patch = patch)
        }
    }
}

data class Version(val rawString: String, val trimmedString: String) {
    companion object {
        fun fromString(version: String?): Version? {
            if (version == null) {
                return null
            }
            return Version(version, version.split("-")[0])
        }
    }
}

data class VersionRange(val closedStart: Boolean, val closedEnd: Boolean, val rangeStart: Version, val rangeEnd: Version) {
    fun toVersionString(): String {
        return (if (closedStart) "[" else "(") + rangeStart.trimmedString + "," + rangeEnd.trimmedString + (if (closedEnd) "]" else ")")
    }

    fun versionInRange(compareTo: Version): Boolean {
        if (closedStart) {
            if (versionCompare(rangeStart.trimmedString, compareTo.trimmedString) > 0) {
                return false
            }
        } else {
            if (versionCompare(rangeStart.trimmedString, compareTo.trimmedString) >= 0) {
                return false
            }
        }
        if (closedEnd) {
            if (versionCompare(rangeEnd.trimmedString, compareTo.trimmedString) < 0) {
                return false
            }
        } else {
            if (versionCompare(rangeEnd.trimmedString, compareTo.trimmedString) <= 0) {
                return false
            }
        }
        return true
    }

    companion object {
        fun versionCompare(str1: String, str2: String): Int {
            val vals1 = str1.split("\\.")
            val vals2 = str2.split("\\.")
            var i = 0
            while (i < vals1.size && i < vals2.size && vals1[i] == vals2[i]) {
                i++
            }
            if (i < vals1.size && i < vals2.size) {
                val diff = Integer.compare(Integer.valueOf(vals1[i]), Integer.valueOf(vals2[i]))
                return Integer.signum(diff)
            }
            return Integer.signum(vals1.size - vals2.size)
        }

        // Some example of things that match this pattern are:
        // "[1]" "[10]" "[10.3.234]"
        // And here is an example with the capture group 1 in <triangle brackets>
        // [<1.><2.><3>]  or
        // VisibleForTesting(otherwise = Private)
        val VERSION_RANGE_PATTERN = Pattern.compile("\\[(\\d+\\.)*(\\d+)+(-\\w)*]")

        fun fromString(versionRange: String): VersionRange? {
            val versionRangeMatcher = VERSION_RANGE_PATTERN.matcher(versionRange)
            if (versionRangeMatcher.matches()) {
                val v = Version.fromString(versionRangeMatcher.group(1)) ?: return null
                return VersionRange(true, true, v, v)
            }
            return null
        }
    }

}
