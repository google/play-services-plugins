package com.google.android.gms.dependencies;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

/**
 * This listener attaches to the Gradle project dependency resolution process in order to alert when
 * the strict version declarations ("[16.0.0]" dependency declarations in POM files) in Google Play
 * services libraries aren't being maintained.
 * <p>
 * Specifically, when an exact version is specified in Google Play services library POM files it
 * means the libraries were ProGuard/R8'ed together to optimize symbols across the library group
 * (AARs). Those changes aren't represented in the SemVer version declarations and unknown behavior
 * is exhibited when the strict version number declarations are ignored. The strict number
 * declaration can be skipped by Gradle's default behavior when other libraries request higher
 * versions of libraries as part of the dependency resolution process so this plugin breaks the
 * build to warn of the situation and provides paths to the problematic dependency paths.
 * <p>
 * This listener is used in both the google-services and strict-version-matcher-plugin Gradle
 * plugins.
 */
public class DependencyInspector implements DependencyResolutionListener {
  private static final String GRADLE_PROJECT = "gradle.project";
  private static Logger logger = LoggerFactory.getLogger(DependencyInspector.class);
  private final DependencyAnalyzer dependencyAnalyzer;
  private final String projectName;
  private final String exceptionMessageAddendum;

  /**
   * Attaches a Listener for inspection and analysis.
   *
   * @param dependencyAnalyzer       where to register newly discovered dependencies and then
   *                                 extract all known dependencies for analysis.
   * @param projectName              Gradle project name for clear error and info messaging.
   * @param exceptionMessageAddendum Message to append to the error message of exceptions thrown.
   *
   * @see DependencyAnalyzer
   */
  public DependencyInspector(@Nonnull DependencyAnalyzer dependencyAnalyzer,
                             @Nonnull String projectName,
                             @Nullable String exceptionMessageAddendum) {
    this.dependencyAnalyzer = dependencyAnalyzer;
    this.exceptionMessageAddendum = exceptionMessageAddendum;
    this.projectName = projectName;
  }

  /**
   * Returns {@code inputString} after shortening the Google owned Group Ids.
   * <p>
   * E.g. com.google.android.gms -> c.g.a.g
   */
  private static String simplifyKnownGroupIds(@Nonnull String inputString) {
    return inputString.replace(
        "com.google.android.gms", "c.g.a.g").replace(
        "com.google.firebase", "c.g.f");
  }

  private static void printNode(int depth, @Nonnull Node n) {
    StringBuilder prefix = new StringBuilder();
    for (int z = 0; z < depth; z++) {
      prefix.append("--");
    }
    prefix.append(" ");
    Dependency dep = n.getDependency();
    if (GRADLE_PROJECT.equals(n.getDependency().getFromArtifactVersion().getGroupId())) {
      String fromRef = dep.getFromArtifactVersion().getGradleRef().replace(
          GRADLE_PROJECT, "");
      String toRef = simplifyKnownGroupIds(dep.getToArtifact().getGradleRef());
      logger.info(prefix.toString() + fromRef + " task/module dep -> " + toRef + "@" +
          dep.getToArtifactVersionString());
    } else {
      String fromRef = simplifyKnownGroupIds(dep.getFromArtifactVersion().getGradleRef());
      String toRef = simplifyKnownGroupIds(dep.getToArtifact().getGradleRef());
      logger.info(prefix.toString() + fromRef + " library depends -> " + toRef + "@" +
          dep.getToArtifactVersionString());
    }
    if (n.getChild() != null) {
      printNode(depth + 1, n.getChild());
    }
  }

  private void registerDependencies(@Nonnull ResolvableDependencies resolvableDependencies,
                                    @Nonnull String projectName, @Nonnull String taskName) {
    ResolutionResult resolutionResult = resolvableDependencies.getResolutionResult();
    String depFromString= "";
    // Record all of the dependencies into the tracker.
    for (DependencyResult depResult : resolutionResult.getAllDependencies()) {
      ArtifactVersion fromDep;
      // Notes regarding getAllDependencies()
      // * it contains all dep links within each project.
      // * it contains links that may not be needed after the final
      //   versions are determined.
      // * depResult.getFrom() == null represents a direct dep from the
      //   project being evaluated.
      if (depResult.getFrom() == null ||
          "".equals(depResult.getFrom().getId().getDisplayName()) ||
          "project :".equals(depResult.getFrom().getId().getDisplayName())) {
        // Register the dep from the project directly.
        fromDep = ArtifactVersion.Companion.fromGradleRef(
            GRADLE_PROJECT + ":" + projectName + "-" + taskName + ":0.0.0");
      } else {
         depFromString = ("" + depResult.getFrom().getId().getDisplayName());
        if (depFromString.startsWith("project ")) {
          // TODO(paulrashidi): Figure out if a third level dependency shows depFromString.
          // In a project with other project dependencies the dep
          // string will be "project :module1"
          // Sometimes depFromString is just "project:" and in that case we want the name
          // to just be an empty string.
          String[] splitDepName = depFromString.split(":");
          String depName = splitDepName.length > 1 ? splitDepName[1] : "module";
          // Register the dep from another module in the project.
          fromDep = ArtifactVersion.Companion.fromGradleRef(
              GRADLE_PROJECT + ":" + projectName + "-" + taskName + "-" + depName + ":0.0.0");
        } else {
          try {
            fromDep = ArtifactVersion.Companion.fromGradleRef(depFromString);
          } catch (IllegalArgumentException iae) {
            logger.info("Skipping misunderstood FROM dep string: " + depFromString);
            continue;
          }
        }
      }
      if (depResult.getRequested() == null) {
        // Prevent odd build setups from throwing errors.
        continue;
      }
      ArtifactVersion toDep;
      String toDepString = depResult.getRequested().toString();
      if (depResult.getRequested() instanceof ModuleComponentSelector) {
        ModuleComponentSelector selector = (ModuleComponentSelector) depResult.getRequested();
        if (!"".equals(selector.getVersionConstraint().getStrictVersion())){
          toDepString = selector.getGroup() + ":" + selector.getModule() + ":[" + selector.getVersionConstraint().getStrictVersion() + "]";
        }
      }
      try {
        toDep = ArtifactVersion.Companion.fromGradleRef(toDepString);
      } catch (IllegalArgumentException iae) {
        logger.info("Skipping misunderstood TO dep string: " + toDepString);
        continue;
      }
      dependencyAnalyzer.registerDependency(
          Dependency.Companion.fromArtifactVersions(fromDep, toDep));
    }
  }

  @Override
  public void beforeResolve(ResolvableDependencies resolvableDependencies) {
    // This information isn't currently useful to the plugin.

    // After doing testing, the only dependencies info available in
    // resolvableDependencies is resolvableDependencies.dependencies.
    // Those are the declared dependencies for task within the module.
    // Also, not sure if Android test tasks have already had the
    // dependencies de-duped by this time.
  }

  @Override
  public void afterResolve(ResolvableDependencies resolvableDependencies) {
    String taskName = resolvableDependencies.getName();

    // Phase 1: register all the dependency information from the project globally.
    logger.info("Registered task dependencies: " + projectName + ":" + taskName);
    if (resolvableDependencies.getResolutionResult() != null &&
        resolvableDependencies.getResolutionResult().getAllDependencies() != null) {
      registerDependencies(resolvableDependencies, projectName, taskName);
    }

    // Phase 2: take the resolved versions of Artifacts, go get the dependencies that
    // apply to those specific versions, and then ensure all are being honored.
    logger.info("Starting dependency analysis");
    ResolutionResult resolutionResult = resolvableDependencies.getResolutionResult();

    // Create an Artifact to ArtifactVersion mapping for resolved components.
    HashMap<Artifact, ArtifactVersion> resolvedVersions = new HashMap<>();
    for (ResolvedComponentResult resolvedComponentResult :
        resolutionResult.getAllComponents()) {
      ArtifactVersion version = ArtifactVersion.Companion.fromGradleRefOrNull(
          resolvedComponentResult.getId().toString());
      if (version != null) {
        resolvedVersions.put(version.getArtifact(), version);
      }
    }

    // Quick no-op when no versions.
    if (resolvedVersions.size() < 1) {
      return;
    }

    // Retrieve dependencies that apply to the resolved dep set.
    Collection<Dependency> activeDeps = dependencyAnalyzer.getActiveDependencies(
        resolvedVersions.values());
    // Validate each of the dependencies that should apply.
    for (Dependency dep : activeDeps) {
      ArtifactVersion resolvedVersion = resolvedVersions.get(dep.getToArtifact());

      // Check whether dependency is still valid.
      if (!dep.isVersionCompatible(resolvedVersion.getVersion())) {
        // This means a resolved version failed a dependency rule.

        logger.warn("Dependency resolved to an incompatible version: " + dep);

        // TODO: Warn, not fail, when the Major version boundaries are breached.
        // TODO: Experiment with collecting all issues and reporting them at once.
        Collection<Node> depsPaths = dependencyAnalyzer.getPaths(
            resolvedVersion.getArtifact());

        // Print extended path information at INFO level.
        logger.info("Dependency Resolution Help: Displaying all currently known " +
            "paths to any version of the dependency: " + dep.getToArtifact());
        logger.info("NOTE: com.google.android.gms translated to c.g.a.g for brevity. " +
            "Same for com.google.firebase -> c.g.f");
        // TODO: The depPaths nodes need to be consolidated prior to display.
        for (Node n : depsPaths) {
          printNode(1, n);
        }

        throw new GradleException(getErrorMessage(dep, resolvedVersion, depsPaths));
      }
    }
  }

  @NotNull
  private String getErrorMessage(@Nonnull Dependency dep, @Nonnull ArtifactVersion resolvedVersion,
                                 @Nonnull Collection<Node> depPaths) {
    StringBuilder errorMessage = new StringBuilder("In project '")
        .append(projectName)
        .append("' a resolved Google Play services library dependency depends on another at an " +
                "exact version (e.g. \"")
        .append(dep.getToArtifactVersionString())
        .append("\", but isn't being resolved to that version. Behavior exhibited by the library " +
                "will be unknown.")
        .append(System.lineSeparator())
        .append(System.lineSeparator())
        .append("Dependency failing: ")
        .append(dep.getDisplayString())
        .append(", but ")
        .append(dep.getToArtifact().getArtifactId())
        .append(" version was ")
        .append(resolvedVersion.getVersion())
        .append(".")
        .append(System.lineSeparator())
        .append(System.lineSeparator())
        .append("The following dependencies are project dependencies that are direct or have " +
                "transitive dependencies that lead to the artifact with the issue.");

    // Append the highest level dependencies into the error message using a Set to deduplicate them.
    // The paths are different at their leaf nodes, but that information isn't being displayed.
    HashSet<String> directDependencyStrings = new HashSet<>();
    StringBuilder currentString = new StringBuilder();
    for (Node node : depPaths) {
      String[] projectNameParts =
          node.getDependency().getFromArtifactVersion().getArtifactId().split("-");
      if (projectNameParts[0].equals(projectNameParts[2])) {
        currentString.append("-- Project '")
            .append(projectNameParts[0])
            .append("' depends onto ");
      } else {
        currentString.append("-- Project '")
            .append(projectNameParts[0])
            .append("' depends on project '")
            .append(projectNameParts[2])
            .append("' which depends onto ");
      }
      currentString.append(node.getDependency().getToArtifact().getGroupId())
          .append(":")
          .append(node.getDependency().getToArtifact().getArtifactId())
          .append("@")
          .append(node.getDependency().getToArtifactVersionString());

      directDependencyStrings.add(currentString.toString());
      currentString.delete(0, currentString.length());
    }

    // Add dependency strings to error message.
    for (String d : directDependencyStrings) {
      errorMessage.append(System.lineSeparator()).append(d);
    }

    errorMessage.append(System.lineSeparator())
        .append(System.lineSeparator())
        .append("For extended debugging info execute Gradle from the command line with ")
        .append("./gradlew --info :")
        .append(projectName)
        .append(":assembleDebug to see the dependency paths to the artifact. ");

    if (exceptionMessageAddendum != null && !"".equals(exceptionMessageAddendum.trim())) {
      errorMessage.append(exceptionMessageAddendum);
    }

    // Keep the error from being a single line in AndroidStudio window.
    // REGEX: Any 120 (".") characters get put into a capture group.
    return errorMessage.toString().replaceAll(".{120}(?=.)",
        // The capture group is then replaced with what was captured "$0" plus an end line.
       "$0" + System.lineSeparator());
  }
}
