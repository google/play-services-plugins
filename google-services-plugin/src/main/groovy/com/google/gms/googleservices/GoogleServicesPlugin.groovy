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

import com.google.android.gms.dependencies.DependencyAnalyzer
import com.google.android.gms.dependencies.DependencyInspector
import org.gradle.api.Plugin
import org.gradle.api.Project

class GoogleServicesPlugin implements Plugin<Project> {
  @Override
  void apply(Project project) {
    GoogleServicesPluginConfig config = project.extensions.create('googleServices', GoogleServicesPluginConfig)

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

    // Setup google-services plugin after one of android plugins is applied.
    project.plugins.withId("com.android.application") {
      project.android.applicationVariants.all { variant ->
        handleVariant(project, variant)
      }
    }

    project.plugins.withId("com.android.library") {
      project.android.libraryVariants.all { variant ->
        handleVariant(project, variant)
      }
    }

    project.plugins.withId("com.android.feature") {
      project.android.featureVariants.all { variant ->
        handleVariant(project, variant)
      }
    }
  }

  private static void handleVariant(Project project,
      def variant) {

    File outputDir =
        project.file("$project.buildDir/generated/res/google-services/$variant.dirName")

    GoogleServicesTask task = project.tasks
        .create("process${variant.name.capitalize()}GoogleServices",
         GoogleServicesTask)

    task.setIntermediateDir(outputDir)
    task.setVariantDir(variant.dirName)

    // This is necessary for backwards compatibility with versions of gradle that do not support
    // this new API.
    if (variant.respondsTo("applicationIdTextResource")) {
      task.setPackageNameXOR2(variant.applicationIdTextResource)
      task.dependsOn(variant.applicationIdTextResource)
    } else {
      task.setPackageNameXOR1(variant.applicationId)
    }

    // This is necessary for backwards compatibility with versions of gradle that do not support
    // this new API.
    if (variant.respondsTo("registerGeneratedResFolders")) {
      task.ext.generatedResFolders = project.files(outputDir).builtBy(task)
      variant.registerGeneratedResFolders(task.generatedResFolders)
      if (variant.respondsTo("getMergeResourcesProvider")) {
        variant.mergeResourcesProvider.configure { dependsOn(task) }
      } else {
        //noinspection GrDeprecatedAPIUsage
        variant.mergeResources.dependsOn(task)
      }
    } else {
      //noinspection GrDeprecatedAPIUsage
      variant.registerResGeneratingTask(task, outputDir)
    }
  }

  public static class GoogleServicesPluginConfig {
    boolean disableVersionCheck = false
  }
}
