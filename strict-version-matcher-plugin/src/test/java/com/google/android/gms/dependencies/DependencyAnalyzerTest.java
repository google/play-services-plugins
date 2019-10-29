package com.google.android.gms.dependencies;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.google.android.gms.dependencies.TestUtilKt.*;

@RunWith(JUnit4.class)
public class DependencyAnalyzerTest {

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
            ART_A_100_TO_ART_B_100, ART_A_100_TO_ART_C_100, ART_B_100_TO_ART_D_100,
            ART_C_100_TO_ART_D_100));
    }

    static {
        simpleSemVerInValidDiamondDependency.addAll(Lists.newArrayList(
            ART_A_100_TO_ART_B_100, ART_A_100_TO_ART_C_200, ART_B_100_TO_ART_D_100,
            ART_C_200_TO_ART_D_200));
    }

    static {
        simpleExactVersionInValidDiamondDependency.addAll(Lists.newArrayList(
            ART_A_100_TO_ART_B_100, ART_A_100_TO_ART_C_200,
            new Dependency(ARTIFACT_B_100, ARTIFACT_D_100.getArtifact(), "[1.0.0]"),
            new Dependency(ARTIFACT_C_200, ARTIFACT_D_200.getArtifact(), "[2.0.0]")));
    }

    @Test
    public void testGetActiveDependencies_SimpleArtifactSelection() {
        DependencyAnalyzer dependencyAnalyzer = new DependencyAnalyzer();
        dependencyAnalyzer.registerDependency(ART_A_100_TO_ART_B_100);
        dependencyAnalyzer.registerDependency(ART_A_200_TO_ART_B_200);

        Collection<Dependency> deps = dependencyAnalyzer.getActiveDependencies(
            Lists.newArrayList(ARTIFACT_A_200, ARTIFACT_B_200));
        // Given A2 and B2 only the A2 -> B2 dep should be returned as pertinent.
        Assert.assertNotNull("No deps retrieved.", deps);
        Assert.assertEquals("Only one dependency should be active but got:\n" + deps,
            1, deps.size());
        Assert.assertEquals("The A2 declared dependency should be returned.",
            new Dependency(ARTIFACT_A_200, ARTIFACT_B_200.getArtifact(),
                ARTIFACT_B_200.getVersion()), deps.toArray()[0]);
    }

    @Test
    public void testGetActiveDependencies_SimpleValidDiamondDependencies() {
        DependencyAnalyzer dependencyAnalyzer = new DependencyAnalyzer();
        for (Dependency dep : simpleValidDiamondDependency) {
            dependencyAnalyzer.registerDependency(dep);
        }
        Collection<Dependency> deps = dependencyAnalyzer.getActiveDependencies(Lists.newArrayList(
                ARTIFACT_A_100, ARTIFACT_B_100, ARTIFACT_C_100, ARTIFACT_D_100));
        Assert.assertEquals("Exactly 4 dependencies should be active.", 4, deps.size());
    }

    @Test
    public void testGetActiveDependencies_SimpleExactVersionInvalidDependencies() {
        DependencyAnalyzer dependencyAnalyzer = new DependencyAnalyzer();
        for (Dependency dep : simpleExactVersionInValidDiamondDependency) {
            dependencyAnalyzer.registerDependency(dep);
        }
        Collection<Dependency> deps = dependencyAnalyzer.getActiveDependencies(Lists.newArrayList(
                ARTIFACT_A_100, ARTIFACT_B_100, ARTIFACT_C_200, ARTIFACT_D_100));
        Assert.assertEquals("Exactly 4 dependencies should be active.", 4, deps.size());
    }
}
