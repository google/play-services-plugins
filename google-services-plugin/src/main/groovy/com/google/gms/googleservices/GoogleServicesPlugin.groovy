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
import org.gradle.api.model.ObjectFactory

import java.util.regex.Matcher
import java.util.regex.Pattern

class GoogleServicesPlugin implements Plugin<Project> {
  public final static String MODULE_GROUP = "com.google.android.gms"
  public final static String MODULE_GROUP_FIREBASE = "com.google.firebase"
  public final static String MODULE_CORE = "firebase-core"
  public final static String MODULE_VERSION = "11.4.2"
  public final static String MINIMUM_VERSION = "9.0.0"

  @Override
  void apply(Project project) {
    GoogleServicesPluginConfig config = project.extensions.create('googleServices', GoogleServicesPluginConfig)

    project.afterEvaluate {
      if (config.disableVersionCheck) {
        return
      }

      DependencyAnalyzer globalDependencies = new DependencyAnalyzer()
      DependencyInspector strictVersionDepInspector = new DependencyInspector(globalDependencies, project.getName(),
            "This error message came from the google-services Gradle plugin, report" +
                " issues at https://github.com/google/play-services-plugins and disable by " +
                "adding \"googleServices { disableVersionCheck = true }\" to your build.gradle file.");
      project.getConfigurations().all{projectConfig -> 
        if (projectConfig.getName().contains("ompile")) {
          projectConfig.getIncoming().afterResolve(strictVersionDepInspector.&afterResolve);
        }
      };
                
                
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

    File outputDir =
        project.file("$project.buildDir/generated/res/google-services/$variant.dirName")

    project
      .tasks
      .create(
        "process${variant.name.capitalize()}GoogleServices",
         GoogleServicesTask) { task ->
          task.setIntermediateDir(outputDir)
          task.applicationId.set(variant.applicationId)
          task.setBuildType(variant.buildType.name)
          task.setProductFlavors(variant.productFlavors.collect { it.name })

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
  }

  public static class GoogleServicesPluginConfig {
    boolean disableVersionCheck = false
  }

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
}
