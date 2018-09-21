package com.google.android.gms.dependencies

import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class ArtifactDependencyManagerTest {

  @Rule @JvmField var thrown: ExpectedException = ExpectedException.none()

  @Test
  fun getDependencies_SimulateConcurrencyDependencyAccess() {
    // Modify the dependencies while to iterating a retrieved set of dependencies. Since the ArtifactDependencyManager
    // is thread-safe this shouldn't cause a problem.

    val manager = ArtifactDependencyManager()
    manager.addDependency(Dependency(ARTIFACT_A_100, ARTIFACT_B_100.getArtifact(), ARTIFACT_B_100.version))
    manager.addDependency(Dependency(ARTIFACT_A_100, ARTIFACT_B_200.getArtifact(), ARTIFACT_B_200.version))

    val iterator = manager.getDependencies(ARTIFACT_B_100.getArtifact()).iterator()
    iterator.next()
    // Add a dependency and iterate again to cause a concurrency issue if the returned collection is connected to the
    // original set of dependencies in a thread-unsafe way.
    manager.addDependency(Dependency(ARTIFACT_A_100, ARTIFACT_B_300.getArtifact(), "9.9.9"))
    iterator.next()
  }
}