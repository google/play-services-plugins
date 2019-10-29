package com.google.android.gms.dependencies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * {Dependency} collector and analyzer for build artifacts.
 * <p>
 * Dependencies between artifacts can can be registered via register* methods. Then, the
 * dependencies that apply to a set of resolved artifacts can be retrieved to understand whether
 * dependency resolution is ignoring certain declared dependencies. The dependencies that are
 * registered can also be retrieved by non-version-specific artifact type.
 * <p>
 * This class is used in the plugin to register all known dependencies between version-specific
 * artifacts and then allow post-Gradle-dependency-resolution analysis to happen. An internal tree
 * is kept that allows version paths to the artifacts versions to be displayed
 * <p>
 * Thread-safety is provided via blocking and deep object copies.
 * <p>
 * TODO: Support SemVer qualifiers.
 */
public class DependencyAnalyzer {
  private Logger logger = LoggerFactory.getLogger(DependencyAnalyzer.class);

  private ArtifactDependencyManager dependencyManager = new ArtifactDependencyManager();

  /**
   * Register a {Dependency}, only for google dependencies.
   */
  synchronized void registerDependency(@Nonnull Dependency dependency) {
    dependencyManager.addDependency(dependency);
  }

  /**
   * Returns a set of Dependencies that were registered between the ArtifactVersions supplied.
   *
   * @param versionedArtifacts List of ArtifactVersions to return dependencies for.
   *
   * @return Dependencies found or an empty collection.
   */
  @Nonnull
  synchronized Collection<Dependency> getActiveDependencies(
      Collection<ArtifactVersion> versionedArtifacts) {
    // Summarize the artifacts in use.
    HashSet<Artifact> artifacts = new HashSet<>();
    HashSet<ArtifactVersion> artifactVersions = new HashSet<>();
    for (ArtifactVersion version : versionedArtifacts) {
      artifacts.add(version.getArtifact());
      artifactVersions.add(version);
    }

    // Find all the dependencies that we need to enforce.
    ArrayList<Dependency> dependencies = new ArrayList<>();
    for (Artifact artifact : artifacts) {
      for (Dependency dep : dependencyManager.getDependencies(artifact)) {
        if (artifactVersions.contains(dep.getFromArtifactVersion()) &&
            artifacts.contains(dep.getToArtifact())) {
          dependencies.add(dep);
        }
      }
    }
    return dependencies;
  }

  synchronized Collection<Node> getPaths(Artifact artifact) {
    ArrayList<Node> pathsToReturn = new ArrayList<>();
    Collection<Dependency> deps = dependencyManager.getDependencies(artifact);
    for (Dependency dep : deps) {
      // Proceed to report back info.
      getNode(pathsToReturn, new Node(null, dep), dep.getFromArtifactVersion());
    }
    return pathsToReturn;
  }

  private synchronized void getNode(ArrayList<Node> terminalPathList, Node n,
                                    ArtifactVersion artifactVersion) {
    Collection<Dependency> deps = dependencyManager.getDependencies(artifactVersion.getArtifact());
    if (deps.size() < 1) {
      terminalPathList.add(n);
      return;
    }
    for (Dependency dep : deps) {
      if (dep.isVersionCompatible(artifactVersion.getVersion())) {
        // Proceed to report back info.
        getNode(terminalPathList, new Node(n, dep), dep.getFromArtifactVersion());
      }
    }
  }
}
