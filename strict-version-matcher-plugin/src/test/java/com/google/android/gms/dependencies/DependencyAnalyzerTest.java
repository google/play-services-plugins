package com.google.android.gms.dependencies;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DependencyAnalyzerTest {
    private static final ArtifactVersion artifactA_v1_0_0 =
            ArtifactVersion.Companion.fromGradleRef("c.g.a:artA:1.0.0");
    private static final ArtifactVersion artifactA_v2_0_0 =
            ArtifactVersion.Companion.fromGradleRef("c.g.a:artA:2.0.0");
    private static final ArtifactVersion artifactB_1 = new ArtifactVersion(
            "c.g.b", "artB", "1.0.0");
    private static final ArtifactVersion artifactB_2 = new ArtifactVersion(
            "c.g.b", "artB", "2.0.0");
    private static final ArtifactVersion artifactC_1 = new ArtifactVersion(
            "c.g.b", "artC", "1.0.0");
    private static final ArtifactVersion artifactC_2 = new ArtifactVersion(
            "c.g.b", "artC", "2.0.0");
    private static final ArtifactVersion artifactD_1 = new ArtifactVersion(
            "c.g.b", "artD", "1.0.0");
    private static final ArtifactVersion artifactD_2 = new ArtifactVersion(
            "c.g.b", "artD", "2.0.0");

    private static final Dependency artA_1_0_0_to_artB_1_0_0 =
            Dependency.Companion.fromArtifactVersions(artifactA_v1_0_0, artifactB_1);

    /**
     * Diamond dependency: A deps to B and C, then B and C dep to D.
     * <p>
     * (A and D are the top of the diamond and B and C are the sides.)
     */
    private static final List<Dependency> simpleValidDiamondDependency = new ArrayList<>();
    /**
     * C depends on D at a different major version than B. Crossing the SemVer Major version means
     * there are backward incompatible things changes therefore this dependency isn't considered
     * valid without somehow verifying the incompatible changes that caused the Major version increment.
     */
    private static final List<Dependency> simpleSemVerInValidDiamondDependency = new ArrayList<>();
    /**
     * C depends on D at a different exact version (using "[" and "]") than B.
     */
    private static final List<Dependency> simpleExactVersionInValidDiamondDependency = new ArrayList<>();

    static {
        simpleValidDiamondDependency.addAll(Lists.newArrayList(
                artA_1_0_0_to_artB_1_0_0,
                new Dependency(artifactA_v1_0_0, artifactC_1.getArtifact(), artifactC_1.getVersion()),
                new Dependency(artifactB_1, artifactD_1.getArtifact(), artifactD_1.getVersion()),
                new Dependency(artifactC_1, artifactD_1.getArtifact(), artifactD_1.getVersion())));
    }

    static {
        simpleSemVerInValidDiamondDependency.addAll(Lists.newArrayList(
                artA_1_0_0_to_artB_1_0_0,
                new Dependency(artifactA_v1_0_0, artifactC_2.getArtifact(), artifactC_2.getVersion()),
                new Dependency(artifactB_1, artifactD_1.getArtifact(), artifactD_1.getVersion()),
                new Dependency(artifactC_2, artifactD_2.getArtifact(), artifactD_2.getVersion())));
    }

    static {
        simpleExactVersionInValidDiamondDependency.addAll(Lists.newArrayList(
                artA_1_0_0_to_artB_1_0_0,
                new Dependency(artifactA_v1_0_0, artifactC_2.getArtifact(), artifactC_2.getVersion()),
                new Dependency(artifactB_1, artifactD_1.getArtifact(), "[1.0.0]"),
                new Dependency(artifactC_2, artifactD_2.getArtifact(), "[2.0.0]")));
    }

    @Test
    public void testGetActiveDependencies_SimpleArtifactSelection() {
        DependencyAnalyzer dependencyAnalyzer = new DependencyAnalyzer();
        // A1 -> B1
        dependencyAnalyzer.registerVersion(artifactA_v1_0_0, artifactB_1.getArtifact(), artifactB_1.getVersion());
        // A2 -> B2
        dependencyAnalyzer.registerVersion(artifactA_v2_0_0, artifactB_2.getArtifact(), artifactB_2.getVersion());

        Collection<Dependency> deps = dependencyAnalyzer.getActiveDependencies(Lists.newArrayList(artifactA_v2_0_0, artifactB_2));
        // Given A2 and B2 only the A2 -> B2 dep should be returned as pertinent.
        Assert.assertNotNull("No deps retrieved.", deps);
        Assert.assertEquals("Only one dependency should be active but got:\n" + deps , 1, deps.size());
        Assert.assertEquals("The A2 declared dependency should be returned.", new Dependency(artifactA_v2_0_0, artifactB_2.getArtifact(), artifactB_2.getVersion()), deps.toArray()[0]);
    }

    @Test
    public void testGetActiveDependencies_SimpleValidDiamondDependencies() {
        DependencyAnalyzer dependencyAnalyzer = new DependencyAnalyzer();
        for (Dependency dep : simpleValidDiamondDependency) {
            dependencyAnalyzer.registerDependency(dep);
        }
        Collection<Dependency> deps = dependencyAnalyzer.getActiveDependencies(Lists.newArrayList(
                artifactA_v1_0_0, artifactB_1, artifactC_1, artifactD_1));
        Assert.assertEquals("Exactly 4 dependencies should be active.", 4, deps.size());
    }

    @Test
    public void testGetActiveDependencies_SimpleExactVersionInvalidDependencies() {
        DependencyAnalyzer dependencyAnalyzer = new DependencyAnalyzer();
        for (Dependency dep : simpleExactVersionInValidDiamondDependency) {
            dependencyAnalyzer.registerDependency(dep);
        }
        Collection<Dependency> deps = dependencyAnalyzer.getActiveDependencies(Lists.newArrayList(
                artifactA_v1_0_0, artifactB_1, artifactC_2, artifactD_1));
        Assert.assertEquals("Exactly 4 dependencies should be active.", 4, deps.size());
    }
}
