package com.google.android.gms;

import com.google.android.gms.dependencies.*;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;

/**
 * This plugin attaches to the Gradle project dependency resolution process in order to alert when
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
 */
public class StrictVersionMatcherPlugin implements Plugin<Project> {
    private static Logger logger = Logging.getLogger(StrictVersionMatcherPlugin.class);
    /**
     * Static tracking of dependency information across all modules the plugin is applied to. It is
     * thread-safe because so multi-threaded builds may continue to add references while other
     * project are using the data stored.
     */
    private static DependencyAnalyzer dependencyTracker = new DependencyAnalyzer();

    private static void registerDependenciesGlobally(ResolvableDependencies resolvableDependencies,
                                                     String projectName, String taskName) {
        ResolutionResult resolutionResult = resolvableDependencies.getResolutionResult();
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
                    "".equals(depResult.getFrom().getId().getDisplayName())) {
                // Register the dep from the project directly.
                fromDep = ArtifactVersion.Companion.fromGradleRef(
                        "gradle.project:" + projectName + "-" + taskName + ":0.0.0");
            } else {
                String depFromString = ("" + depResult.getFrom().getId().getDisplayName());
                if (depFromString.startsWith("project ")) {
                    // TODO(paulrashidi): Figure out more about this format.
                    // In a project with other project dependencies the dep
                    // string will be "project :module1"
                    String depName = depFromString.split(":")[1];
                    // Register the dep from another module in the project.
                    fromDep = ArtifactVersion.Companion.fromGradleRef(
                            "gradle.project:" + projectName + "-" + taskName + "-" +
                                    depName + ":0.0.0");
                } else {
                    try {
                        fromDep = ArtifactVersion.Companion.fromGradleRef(depFromString);
                    } catch (IllegalArgumentException iae) {
                        logger.error("Skipping misunderstood FROM dep string: " + depFromString);
                        continue;
                    }
                }
            }
            if (depResult.getRequested() == null) {
                // TODO(paulrashidi): What does this represent?
                continue;
            }
            ArtifactVersion toDep;
            String toDepString = "" + depResult.getRequested();
            try {
                toDep = ArtifactVersion.Companion.fromGradleRef(toDepString);
            } catch (IllegalArgumentException iae) {
                logger.error("Skipping misunderstood TO dep string: " + toDepString);
                continue;
            }
            dependencyTracker.registerDependency(
                    Dependency.Companion.fromArtifactVersions(fromDep, toDep));
        }
    }

    private static void printNode(int depth, Node n) {
        StringBuilder prefix = new StringBuilder();
        for (int z = 0; z < depth; z++) {
            prefix.append("--");
        }
        prefix.append(" ");
        Dependency dep = n.getDependency();
        if ("gradle.project".equals(n.getDependency().getFromArtifactVersion().getGroupId())) {
            String fromRef = dep.getFromArtifactVersion().getGradleRef().replace(
                    "gradle.project", "");
            String toRef = dep.getToArtifact().getGradleRef().replace(
                    "com.google.android.gms", "c.g.a.g").replace(
                            "com.google.firebase", "c.g.f");
            logger.warn(prefix.toString() + fromRef + " task/module dep -> " + toRef + "@" +
                    dep.getToArtifactVersionString());
        } else {
            String fromRef = dep.getFromArtifactVersion().getGradleRef().replace(
                    "com.google.android.gms", "c.g.a.g").replace(
                            "com.google.firebase", "c.g.f");
            String toRef = dep.getToArtifact().getGradleRef().replace(
                    "com.google.android.gms", "c.g.a.g").replace(
                            "com.google.firebase", "c.g.f");
            logger.warn(prefix.toString() + fromRef + " library depends -> " + toRef + "@" +
                    dep.getToArtifactVersionString());
        }
        if (n.getChild() != null) {
            printNode(depth + 1, n.getChild());
        }
    }

    @Override
    public void apply(@Nonnull Project project) {
        // When debugging and testing ensure to look at release dependencies,
        // not testing dependencies because of the Android test-app
        // de-duplication that happens to produce an Android test app that
        // can be run in the same process as the Android App (under test).
        project.getGradle().addListener(new DependencyInspector(project.getName()));
    }

    /**
     * Performs registration and analysis of dependencies for the project.
     */
    private static class DependencyInspector implements DependencyResolutionListener {
        private final String projectName;

        private DependencyInspector(@Nonnull String projectName) {
            this.projectName = projectName;
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

            // Use of Product flavors can change task names so we look for compile tasks
            // in a case in-sensitive way.
            if (!taskName.contains("ompile")) {
                // Quickly no-op to speed up Gradle build analysis.
                return;
            }

            // Phase 1: register all the dependency information from the project globally.
            logger.info("Registered task dependencies: " + projectName + ":" + taskName);
            if (resolvableDependencies.getResolutionResult() != null &&
                    resolvableDependencies.getResolutionResult().getAllDependencies() != null) {
                registerDependenciesGlobally(resolvableDependencies, projectName, taskName);
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
            Collection<Dependency> activeDeps = dependencyTracker.getActiveDependencies(
                    resolvedVersions.values());
            // Validate each of the dependencies that should apply.
            for (Dependency dep : activeDeps) {
                ArtifactVersion resolvedVersion = resolvedVersions.get(dep.getToArtifact());

                // Check whether dependency is still valid.
                if (!dep.isVersionCompatible(resolvedVersion.getVersion())) {
                    logger.warn("Dependency resolved to an incompatible version: " + dep);
                    logger.info("Dependency Resolution Help: Displaying all currently known " +
                            "paths to any version of the dependency: " + dep.getToArtifact());
                    // This means a resolved version failed a dependency rule.
                    GradleException exception = new GradleException("One resolved Google Play " +
                            "services library dependency depends on another at an exact version " +
                            "(e.g. \"[1.4.3]\"), but isn't being resolved to that version. " +
                            "Behavior exhibited by the library will be unknown. Execute gradle " +
                            "from the command line with ./gradlew --info :app:assembleDebug to " +
                            "see the dependency paths to the artifact. Dependency failing: " +
                            dep.getDisplayString() + " but " + dep.getToArtifact().getArtifactId() +
                            " version was " + resolvedVersion.getVersion() + ". This error came " +
                            "from the strict-dep-checker-plugin and can be disabled by disabling " +
                            "that plugin at your own risk. ");
                    // TODO: Warn, not fail, when the Major version boundaries are breached.
                    // TODO: Experiment with collecting all issues and reporting them at once.
                    Collection<Node> depsPaths = dependencyTracker.getPaths(
                            resolvedVersion.getArtifact());
                    logger.info("NOTE: com.google.android.gms translated to c.g.a.g for brevity. " +
                            "Same for com.google.firebase -> c.g.f");
                    for (Node n : depsPaths) {
                        printNode(1, n);
                    }
                    throw exception;
                }
            }
        }
    }
}
