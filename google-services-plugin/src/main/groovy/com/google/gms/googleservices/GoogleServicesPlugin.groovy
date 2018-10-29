/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gms.googleservices

import com.google.android.gms.dependencies.DependencyAnalyzer;
import com.google.android.gms.dependencies.DependencyInspector;
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

class GoogleServicesPlugin implements Plugin<Project> {

  public final static String JSON_FILE_NAME = 'google-services.json'

  public final static String MODULE_GROUP = "com.google.android.gms"
  public final static String MODULE_GROUP_FIREBASE = "com.google.firebase"
  public final static String MODULE_CORE = "firebase-core"
  public final static String MODULE_VERSION = "11.4.2"
  public final static String MINIMUM_VERSION = "9.0.0"
  // Some example of things that match this pattern are:
  // "aBunchOfFlavors/release"
  // "flavor/debug"
  // "test"
  // And here is an example with the capture groups in [square brackets]
  // [a][BunchOfFlavors]/[release]
  public final static Pattern VARIANT_PATTERN = ~/(?:([^\p{javaUpperCase}]+)((?:\p{javaUpperCase}[^\p{javaUpperCase}]*)*)\/)?([^\/]*)/
  // Some example of things that match this pattern are:
  // "TestTheFlavor"
  // "FlavorsOfTheRainbow"
  // "Test"
  // And here is an example with the capture groups in [square brackets]
  // "[Flavors][Of][The][Rainbow]"
  // Note: Pattern must be applied in a loop, not just once.
  public final static Pattern FLAVOR_PATTERN = ~/(\p{javaUpperCase}[^\p{javaUpperCase}]*)/
  // These are the plugin types and the set of associated plugins whose presence should be checked for.
  private final static enum PluginType{
    APPLICATION([
      "android",
      "com.android.application"
    ]),
    LIBRARY([
      "android-library",
      "com.android.library"
    ]),
    FEATURE([
      "android-feature",
      "com.android.feature"
    ]),
    MODEL_APPLICATION([
      "com.android.model.application"
    ]),
    MODEL_LIBRARY(["com.android.model.library"])
    public PluginType(Collection plugins) {
      this.plugins = plugins
    }
    private final Collection plugins
    public Collection plugins() {
      return plugins
    }
  }

  public static GoogleServicesPluginConfig config = new GoogleServicesPluginConfig()

  @Override
  void apply(Project project) {
    config = project.extensions.create('googleServices', GoogleServicesPluginConfig)

    project.afterEvaluate {
      if (config.disableVersionCheck) {
        return
      }
      DependencyAnalyzer globalDependencies = new DependencyAnalyzer()
      project.getGradle().addListener(
        new DependencyInspector(globalDependencies, project.getName(),
            "This error message came from the google-services Gradle plugin, report" +
                " issues at https://github.com/google/play-services-plugins and disable by " +
                "adding \"googleServices { disableVersionCheck = false }\" to your build.gradle file."));
    }
    for (PluginType pluginType : PluginType.values()) {
      for (String plugin : pluginType.plugins()) {
        if (project.plugins.hasPlugin(plugin)) {
          setupPlugin(project, pluginType)
          return
        }
      }
    }
    // If the google-service plugin is applied before any android plugin.
    // We should warn that google service plugin should be applied at
    // the bottom of build file.
    showWarningForPluginLocation(project)

    // Setup google-services plugin after android plugin is applied.
    project.plugins.withId("android", {
      setupPlugin(project, PluginType.APPLICATION)
    })
    project.plugins.withId("android-library", {
      setupPlugin(project, PluginType.LIBRARY)
    })
    project.plugins.withId("android-feature", {
      setupPlugin(project, PluginType.FEATURE)
    })
  }

  private void showWarningForPluginLocation(Project project) {
    project.getLogger().warn(
        "Warning: Please apply google-services plugin at the bottom of the build file.")
  }

  private void setupPlugin(Project project, PluginType pluginType) {
    switch (pluginType) {
      case PluginType.APPLICATION:
        project.android.applicationVariants.all { variant ->
          handleVariant(project, variant)
        }
        break
      case PluginType.LIBRARY:
        project.android.libraryVariants.all { variant ->
          handleVariant(project, variant)
        }
        break
      case PluginType.FEATURE:
        project.android.featureVariants.all { variant ->
          handleVariant(project, variant)
        }
        break
      case PluginType.MODEL_APPLICATION:
        project.model.android.applicationVariants.all { variant ->
          handleVariant(project, variant)
        }
        break
      case PluginType.MODEL_LIBRARY:
        project.model.android.libraryVariants.all { variant ->
          handleVariant(project, variant)
        }
        break
    }
  }


  private static void handleVariant(Project project,
      def variant) {

    File quickstartFile = null
    List<String> fileLocations = getJsonLocations("$variant.dirName", project)
    String searchedLocation = System.lineSeparator()
    for (String location : fileLocations) {
      File jsonFile = project.file(location + '/' + JSON_FILE_NAME)
      searchedLocation = searchedLocation + jsonFile.getPath() + System.lineSeparator()
      if (jsonFile.isFile()) {
        quickstartFile = jsonFile
        break
      }
    }

    if (quickstartFile == null) {
      quickstartFile = project.file(JSON_FILE_NAME)
      searchedLocation = searchedLocation + quickstartFile.getPath()
    }

    File outputDir =
        project.file("$project.buildDir/generated/res/google-services/$variant.dirName")

    GoogleServicesTask task = project.tasks
        .create("process${variant.name.capitalize()}GoogleServices",
         GoogleServicesTask)

    task.quickstartFile = quickstartFile
    task.intermediateDir = outputDir
    task.searchedLocation = searchedLocation

    // This is neccesary for backwards compatibility with versions of gradle that do not support
    // this new API.
    if (variant.metaClass.respondsTo(variant, "applicationIdTextResource")
      || variant.metaClass.hasProperty(variant, "applicationIdTextResource")) {
      task.packageNameXOR2 = variant.applicationIdTextResource
      task.dependsOn(variant.applicationIdTextResource)
    } else {
      task.packageNameXOR1 = variant.applicationId
    }

    // Use the target version for the task.
    variant.registerResGeneratingTask(task, outputDir)
  }

  private static List<String> splitVariantNames(String variant) {
    if (variant == null) {
      return Collections.emptyList()
    }
    List<String> flavors = new ArrayList<>()
    Matcher flavorMatcher = FLAVOR_PATTERN.matcher(variant)
    while (flavorMatcher.find()) {
      String match = flavorMatcher.group(1)
      if (match != null) {
        flavors.add(match.toLowerCase())
      }
    }
    return flavors
  }

  private static long countSlashes(String input) {
    return input.codePoints().filter{x -> x == '/'}.count()
  }

  static List<String> getJsonLocations(String variantDirname, Project project) {
    Matcher variantMatcher = VARIANT_PATTERN.matcher(variantDirname)
    List<String> fileLocations = new ArrayList<>()
    if (!variantMatcher.matches()) {
      project.getLogger().warn("$variantDirname failed to parse into flavors. Please start " +
        "all flavors with a lowercase character")
      fileLocations.add("src/$variantDirname")
      return fileLocations
    }
    List<String> flavorNames = new ArrayList<>()
    if (variantMatcher.group(1) != null) {
      flavorNames.add(variantMatcher.group(1).toLowerCase())
    }
    flavorNames.addAll(splitVariantNames(variantMatcher.group(2)))
    String buildType = variantMatcher.group(3)
    String flavorName = "${variantMatcher.group(1)}${variantMatcher.group(2)}"
    fileLocations.add("src/$flavorName/$buildType")
    fileLocations.add("src/$buildType/$flavorName")
    fileLocations.add("src/$flavorName")
    fileLocations.add("src/$buildType")
    fileLocations.add("src/$flavorName${buildType.capitalize()}")
    fileLocations.add("src/$buildType")
    String fileLocation = "src"
    for(String flavor : flavorNames) {
      fileLocation += "/$flavor"
      fileLocations.add(fileLocation)
      fileLocations.add("$fileLocation/$buildType")
      fileLocations.add("$fileLocation${buildType.capitalize()}")
    }
    fileLocations.unique().sort{a,b -> countSlashes(b) <=> countSlashes(a)}
    return fileLocations
  }

  public static class GoogleServicesPluginConfig {
    boolean disableVersionCheck = false
  }
}
