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

package com.google.android.gms

import java.util.HashSet
import java.util.HashMap
import java.util.SortedSet
import java.util.TreeSet
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.invocation.Gradle
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class StrictVersionMatcherPlugin implements Plugin<Project> {
  // Some example of things that match this pattern are:
  // "[1]"
  // "[10]"
  // "[10.3.234]"
  // And here is an example with the capture group 1 in <triangle brackets>
  // [<1.2.3.4>]
  public static final Pattern VERSION_RANGE_PATTERN = ~/\[(\d+(\.\d+)*)\]/

  public static HashMap<String, HashMap<String, HashSet<VersionRange>>> versionsByGroupAndName = new HashMap<>();

  public static boolean depsScanned = false


  @Override
  void apply(Project project) {
    failOnVersionConflictForGroup(project, "com.google.android.gms")
    failOnVersionConflictForGroup(project, "com.google.firebase")
  }

  static int versionCompare(String str1, String str2) {
    String[] vals1 = str1.split("\\.");
    String[] vals2 = str2.split("\\.");
    int i = 0;
    while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
      i++;
    }
    if (i < vals1.length && i < vals2.length) {
      int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
      return Integer.signum(diff);
    }
    return Integer.signum(vals1.length - vals2.length);
  }

  /**
   * Adds two hooks to the build project, the first is when every dependency
   * is resolved, if it matches the group prefix its requested version is
   * parsed and stored. The second is hooked in right before compilation, and
   * checks the resolved dependencies to ensure they match the requested ranges
   * stored earlier. If a mismatch occurs, a GradleException is thrown.
   */
  static void failOnVersionConflictForGroup(Project project, String groupPrefix) {
    versionsByGroupAndName = new HashMap<>();
    project.configurations.all {
      resolutionStrategy.eachDependency { details ->
        checkNewModule();
        String group = details.requested.group
        String name = details.requested.name
        String version = details.requested.version
        if (group == null || name == null || version == null) {
          return
        }
        if (group.startsWith(groupPrefix)) {
          if(!versionsByGroupAndName.containsKey(group)) {
            versionsByGroupAndName.put(group, new HashMap<>())
          }
          if(!versionsByGroupAndName.get(group).containsKey(name)) {
            versionsByGroupAndName.get(group).put(name, new HashSet<>())
          }
          VersionRange versionRange = VersionRange.fromString(version)
          if (versionRange != null) {
            versionsByGroupAndName.get(group).get(name).add(versionRange)
          }
        }
      }
    }
    project.getGradle().addListener(new DependencyResolutionListener() {
          @Override
          void beforeResolve(ResolvableDependencies resolvableDependencies) {
          }

          @Override
          void afterResolve(ResolvableDependencies resolvableDependencies) {
            depsScanned = true
            resolvableDependencies.getResolutionResult().allComponents {artifact ->
              String group = artifact.moduleVersion.group
              String name = artifact.moduleVersion.name
              Version version = Version.fromString(artifact.moduleVersion.version)
              if (group == null || name == null || version == null) {
                return
              }
              if (group.startsWith(groupPrefix)) {
                HashSet<VersionRange> ranges = versionsByGroupAndName
                          .getOrDefault(group, new HashMap())
                          .getOrDefault(name, new HashSet())
                for (VersionRange range : ranges) {
                  if (!range.versionInRange(version)) {
                    throw new GradleException("The library $group:$name is being requested by "
                    + "various other libraries at $ranges, but resolves to $version.rawVersion. "
                    + "Disable the plugin and check your dependencies tree using ./gradlew :app:dependencies.")
                  }
                }
              }
            }
          }
        })
  }

  /**
   * Checks if we are scanning through a new module, if we are, we should reset the versions.
   */
  static void checkNewModule() {
    if (depsScanned) {
      depsScanned = false;
      versionsByGroupAndName = new HashMap<>();
    }
  }

  @groovy.transform.Immutable static class Version {
    String rawVersion, trimmedVersion
    public static Version fromString(String version) {
      if (version == null) {
        return null
      }
      return new Version(version, version.split("-")[0])
    }
  }
  @groovy.transform.Immutable static class VersionRange {
    boolean closedStart
    boolean closedEnd
    Version rangeStart
    Version rangeEnd

    public static VersionRange fromString(String versionRange) {
      Matcher versionRangeMatcher = VERSION_RANGE_PATTERN.matcher(versionRange)
      if (versionRangeMatcher.matches()) {
        Version version = Version.fromString(versionRangeMatcher.group(1))
        return new VersionRange(
            true,
            true,
            version,
            version)
      }
      return null
    }

    boolean versionInRange(Version version) {
      if (closedStart) {
        if (versionCompare(rangeStart.trimmedVersion, version.trimmedVersion) > 0) {
          return false;
        }
      } else {
        if (versionCompare(rangeStart.trimmedVersion, version.trimmedVersion) >= 0) {
          return false;
        }
      }
      if (closedEnd) {
        if (versionCompare(rangeEnd.trimmedVersion, version.trimmedVersion) < 0) {
          return false;
        }
      } else {
        if (versionCompare(rangeEnd.trimmedVersion, version.trimmedVersion) <= 0) {
          return false;
        }
      }
      return true;
    }

    public String toString() {
      return ((closedStart ? "[" : "(")
            + rangeStart.trimmedVersion + ","
            + rangeEnd.trimmedVersion
            + (closedEnd ? "]" : ")"))
    }
  }
}
