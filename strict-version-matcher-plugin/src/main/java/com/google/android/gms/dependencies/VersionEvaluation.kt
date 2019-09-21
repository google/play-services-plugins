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

  private fun containsVersionRange(version:String):Boolean{
    return version.contains(',') ||
           //open range
           version.endsWith('[') ||
           version.endsWith(')') ||
           version.startsWith(']') ||
           version.endsWith('(')
  }

  fun getEvaluator(versionString: String, enableStrictMatching: Boolean): VersionEvaluator {
    val hasVersionRange = containsVersionRange(versionString)
    return if (hasVersionRange) {
      //TODO: Support range version
      AlwaysCompatibleEvaluator()
    }else if (enableStrictMatching) {
      // TODO: Re-enable SemVer validator.
      // SemVerVersionEvaluator(versionString)
      AlwaysCompatibleEvaluator()
    }else{
      val version = versionString.removePrefix("[").removeSuffix("]")
      ExactVersionEvaluator(version)
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

