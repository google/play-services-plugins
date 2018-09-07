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

import com.google.android.gms.dependencies.Version
import com.google.android.gms.dependencies.VersionRange
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact

import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class StrictVersionMatcherPlugin implements Plugin<Project> {
  public static HashMap<String, HashMap<String, HashSet<VersionRange>>> versionsByGroupAndName = new HashMap<>()
  public static boolean depsScanned = false

  @Override
  void apply(Project project) {
    failOnVersionConflictForGroup(project, "com.google.android.gms")
    failOnVersionConflictForGroup(project, "com.google.firebase")
  }

  /**
   * Adds two hooks to the build project, the first is when every dependency
   * is resolved, if it matches the group prefix its requested version is
   * parsed and stored. The second is hooked in right before compilation, and
   * checks the resolved dependencies to ensure they match the requested ranges
   * stored earlier. If a mismatch occurs, a GradleException is thrown.
   */
  static void failOnVersionConflictForGroup(Project project, String groupPrefix) {
    versionsByGroupAndName = new HashMap<>()
    project.configurations.all { Configuration configuration ->
      configuration.resolutionStrategy.eachDependency { details ->
        checkNewModule()
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
            resolvableDependencies.getResolutionResult().allComponents { ResolvedArtifact artifact ->
              String group = artifact.moduleVersion.group
              String name = artifact.moduleVersion.name
              ModuleVersionIdentifier versionInfo = artifact.moduleVersion.version
              Version version = Version.fromString(versionInfo.getGroup() + ":" +
                      versionInfo.getName() + ":" + versionInfo.getVersion())
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
      depsScanned = false
      versionsByGroupAndName = new HashMap<>()
    }
  }
}
