package com.google.android.gms.dependencies

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
        fun parseString(versionString:String) : SemVerVersionInfo {
            val version = versionString.trim();
            val parts = version.split(".")
            if (parts.size != 3) {
                throw IllegalArgumentException("versionString didn't have 3 parts divided by periods.");
            }
            val major = Integer.valueOf(parts[0])
            val minor = Integer.valueOf(parts[1])

            var patchString = parts[2];
            var dashIndex = patchString.indexOf("-")
            if (dashIndex != -1) {
                patchString = patchString.substring(0, dashIndex)
            }
            // TODO: Update to account for qualifiers to the version.
            val patch = Integer.valueOf(patchString)
            return SemVerVersionInfo(major= major, minor = minor, patch = patch)
        }
    }
}