package com.google.android.gms.dependencies;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

/**
 * {Dependency} collector and analyzer for build artifacts.
 * <p>
 * Dependencies between artifacts can can be registered via register* methods.
 * Then, the dependencies that apply to a set of resolved artifacts can be
 * retrieved to understand whether dependency resolution is ignoring certain
 * declared dependencies. The dependencies that are registered can also be
 * retrieved by non-version-specific artifact type.
 * <p>
 * This class is used in the plugin to register all known dependencies between
 * version-specific artifacts and then allow post-Gradle-dependency-resolution
 * analysis to happen. An internal tree is kept that allows version paths to
 * the artifacts versions to be displayed
 * <p>
 * Thread-safety is provided via blocking and deep object copies.
 * <p>
 * TODO: Copy of depMgrMap isn't thread-safe.
 * TODO: Javadoc.
 * TODO: Unit tests.
 * TODO: Code formatting.
 * TODO: Support SemVer qualifiers.
 */
public class DependencyAnalyzer {
    private Logger logger = Logging.getLogger(DependencyAnalyzer.class);

    /**
     * Maintaining a manager per Artifact for the dependencies that get
     * registered.
     */
    private HashMap<Artifact, ArtifactDependencyManager> depMgrMap = new HashMap<>();

    /**
     * Register a {Dependency}.
     */
    public synchronized void registerDependency(@Nonnull Dependency dependency) {
        // Create a DependencyManager if it doesn't exist.
        ArtifactDependencyManager dependencyManager = depMgrMap.get(dependency.getToArtifact());
        if (dependencyManager == null) {
            dependencyManager = new ArtifactDependencyManager(dependency.getToArtifact());
            depMgrMap.put(dependencyManager.getArtifact(), dependencyManager);
        }
        dependencyManager.addDependency(dependency);
    }

    // VisibleForTesting
    void registerVersion(@Nonnull ArtifactVersion from, @Nonnull Artifact to, @Nonnull String versionString) {
        Dependency dep = new Dependency(from, to, versionString);
        registerDependency(dep);
    }

    /**
     * Returns a set of Dependencies that were registered between the
     * ArtifactVersions supplied.
     *
     * @param versionedArtifacts List of ArtifactVersions to return dependencies for.
     * @return Dependencies found or an empty collection.
     */
    @Nonnull
    public synchronized Collection<Dependency> getActiveDependencies(Collection<ArtifactVersion> versionedArtifacts) {
        // Summarize the artifacts in play.
        HashSet<Artifact> artifacts = new HashSet<>();
        HashSet<ArtifactVersion> artifactVersions = new HashSet<>();
        for (ArtifactVersion version : versionedArtifacts) {
            if (version.getGroupId().equals("com.google.android.gms")) {
                logger.debug("Getting artifact: " + version + ":" + version.getArtifact());
            }
            artifacts.add(version.getArtifact());
            artifactVersions.add(version);
        }

        HashMap<Artifact, ArtifactDependencyManager> mapClone = (HashMap<Artifact, ArtifactDependencyManager>) depMgrMap.clone();

        // Find all the dependencies that we need to enforce.
        ArrayList<Dependency> dependencies = new ArrayList<>();
        for (ArtifactDependencyManager mgr : mapClone.values()) {
            for (Dependency dep : mgr.getDependencies()) {
                if (artifactVersions.contains(dep.getFromArtifactVersion()) &&
                        artifacts.contains(dep.getToArtifact())) {
                    dependencies.add(dep);
                }
            }
        }
        return dependencies;
    }

    public synchronized Collection<Node> getPaths(Artifact artifact) {
        ArrayList<Node> l = new ArrayList<>();
        Collection<Dependency> deps = getDeps(artifact);
        if (deps != null) {
            for (Dependency d : deps) {
                // Proceed to report back info.
                getNode(l, new Node(null, d), d.getFromArtifactVersion());
            }
        }
        return l;
    }

    private synchronized void getNode(ArrayList<Node> terminalPathList, Node n, ArtifactVersion artifactVersion) {
        Collection<Dependency> deps = getDeps(artifactVersion.getArtifact());
        if (deps == null || deps.size() < 1) {
            terminalPathList.add(n);
            return;
        }
        for (Dependency d : deps) {
            if (d.isVersionCompatible(artifactVersion.getVersion())) {
                // Proceed to report back info.
                getNode(terminalPathList, new Node(n, d), d.getFromArtifactVersion());
            }
        }
    }

    private synchronized Collection<Dependency> getDeps(Artifact artifact) {
        ArtifactDependencyManager mgr = depMgrMap.get(artifact);
        if (mgr == null || mgr.getDependencies().size() < 1) {
            return null;
        }
        return mgr.getDependencies();
    }

    public synchronized Collection<Dependency> getAllDependencies(Collection<Artifact> artifactList) {
        HashMap<Artifact, ArtifactDependencyManager> mapClone = (HashMap<Artifact, ArtifactDependencyManager>) depMgrMap.clone();

        // Find all the dependencies that we need to enforce.
        ArrayList<Dependency> dependencies = new ArrayList<>();
        for (ArtifactDependencyManager mgr : mapClone.values()) {
            // logger.warn("Inspecting dep mgr: " + mgr.getExtendedToString());
            for (Dependency dep : mgr.getDependencies()) {
                if (artifactList.contains(dep.getFromArtifactVersion().getArtifact()) ||
                        artifactList.contains(dep.getToArtifact())) {
                    dependencies.add(dep);
                }
            }
        }
        return dependencies;
    }
}