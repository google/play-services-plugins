/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension
import com.android.build.api.variant.GeneratesApk
import com.android.build.api.variant.Variant
import com.google.android.gms.dependencies.DependencyAnalyzer
import com.google.android.gms.dependencies.DependencyInspector
import java.io.File
import java.util.*
import org.gradle.api.Plugin
import org.gradle.api.Project

class GoogleServicesPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    val config = project.extensions.create("googleServices", GoogleServicesPluginConfig::class.java)
    project.afterEvaluate {
      if (config.disableVersionCheck) {
        return@afterEvaluate
      }

      val globalDependencies = DependencyAnalyzer()
      val strictVersionDepInspector =
          DependencyInspector(
              globalDependencies,
              project.name,
              "This error message came from the google-services Gradle plugin, report" +
                  " issues at https://github.com/google/play-services-plugins and disable by " +
                  "adding \"googleServices { disableVersionCheck = true }\" to your build.gradle file.")
      project.configurations.configureEach { configuration ->
        if (configuration.name.contains("ompile")) {
          configuration.incoming.afterResolve(strictVersionDepInspector::afterResolve)
        }
      }
    }

    var pluginApplied = false

    project.pluginManager.withPlugin("com.android.application") {
      pluginApplied = true
      project.extensions.configure(ApplicationAndroidComponentsExtension::class.java) {
        it.registerSourceType(SOURCE_TYPE)
        it.onVariants { variant -> handleVariant(variant, project) }
      }
    }
    project.pluginManager.withPlugin("com.android.dynamic-feature") {
      pluginApplied = true
      project.extensions.configure(DynamicFeatureAndroidComponentsExtension::class.java) {
        it.registerSourceType(SOURCE_TYPE)
        it.onVariants { variant -> handleVariant(variant, project) }
      }
    }

    project.afterEvaluate {
      if (pluginApplied) {
        return@afterEvaluate
      }
      project.logger.error(
          "The google-services Gradle plugin needs to be applied on a project with " +
              "com.android.application or com.android.dynamic-feature.")
    }
  }

  private fun <T> handleVariant(variant: T, project: Project) where T : Variant, T : GeneratesApk {
    val jsonToXmlTask =
        project.tasks.register(
            "process${variant.name.capitalize()}GoogleServices", GoogleServicesTask::class.java) {
              it.missingGoogleServicesStrategy.set(
                  project.extensions
                      .getByType(GoogleServicesPluginConfig::class.java)
                      .missingGoogleServicesStrategy)
              it.googleServicesJsonFiles.set(
                  getJsonFiles(
                      variant.buildType.orEmpty(),
                      variant.productFlavors.map { it.second },
                      project.projectDir))
              it.applicationId.set(variant.applicationId)
              it.gmpAppId.set(project.layout.buildDirectory.file("gmpAppId/${variant.name}.txt"))
            }

    // TODO: add an AGP version check to this block
    //  when https://issuetracker.google.com/issues/268192807 is fixed
    try {
      variant.sources
          .getByName(SOURCE_TYPE)
          .addStaticSourceDirectory(
              project.layout.projectDirectory.dir("src/${variant.name}/google-services").toString())
    } catch (e: IllegalArgumentException) {
      // directory doesn't exist, ignore
    }
    variant.sources.res?.addGeneratedSourceDirectory(
        jsonToXmlTask, GoogleServicesTask::outputDirectory)
  }

  /* Recommended replacement for Kotlin's deprecated capitalize function */
  private fun String.capitalize(): String = replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
  }

  companion object {
    const val MODULE_GROUP = "com.google.android.gms"
    const val MODULE_GROUP_FIREBASE = "com.google.firebase"
    const val MODULE_CORE = "firebase-core"
    const val MODULE_VERSION = "11.4.2"
    const val MINIMUM_VERSION = "9.0.0"
    const val SOURCE_TYPE = "google-services"
    const val JSON_FILE_NAME = "google-services.json"

    fun getJsonFiles(buildType: String, flavorNames: List<String>, root: File): List<File> {
      return getJsonLocations(buildType, flavorNames).map { root.resolve(it) }
    }

    fun getJsonLocations(buildType: String, flavorNames: List<String>): List<String> {
      var fileLocations: MutableList<String> = ArrayList()
      val flavorName =
          flavorNames.stream().reduce("") { a, b -> a + if (a.isEmpty()) b else b.capitalized() }
      fileLocations.add("")
      fileLocations.add("src/$flavorName/$buildType")
      fileLocations.add("src/$buildType/$flavorName")
      fileLocations.add("src/$flavorName")
      fileLocations.add("src/$buildType")
      fileLocations.add("src/" + flavorName + buildType.capitalized())
      fileLocations.add("src/$buildType")
      var fileLocation = "src"
      for (flavor in flavorNames) {
        fileLocation += "/$flavor"
        fileLocations.add(fileLocation)
        fileLocations.add("$fileLocation/$buildType")
        fileLocations.add(fileLocation + buildType.capitalized())
      }
      return fileLocations
          .distinct()
          .sortedByDescending { path -> path.count { it == '/' } }
          .map { location: String ->
            if (location.isEmpty()) location + JSON_FILE_NAME else "$location/$JSON_FILE_NAME"
          }
    }
  }

  enum class MissingGoogleServicesStrategy {
    IGNORE,
    WARN,
    ERROR
  }

  open class GoogleServicesPluginConfig {
    /** Disables checking of Google Play Services dependencies compatibility. */
    var disableVersionCheck = false

    /**
     * Choose the behavior when google-services.json is missing. Defaults to ERROR, other possible
     * values are: WARN, IGNORE.
     */
    var missingGoogleServicesStrategy = MissingGoogleServicesStrategy.ERROR
  }
}

private fun CharSequence.capitalized(): String =
    toString().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
