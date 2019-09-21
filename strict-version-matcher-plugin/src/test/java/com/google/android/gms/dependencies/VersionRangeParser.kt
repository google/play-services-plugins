package com.google.android.gms.dependencies

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class VersionRangeParser{

  @Test
  fun passIfCheckTheSameVersion(){
    Assert.assertTrue(ART_A_100_TO_ART_B_100.isVersionCompatible ("1.0.0"))
  }

  @Test
  fun closeRangeVersionAreNotSupportedAndAllTheVersionAreCompatible(){
    val dep = createDependecyWithVersion("[1.0,10.0]")
    Assert.assertTrue(dep.isVersionCompatible ("20.0"))
  }

  @Test
  fun openRangeVersionAreNotSupportedAndAllTheVersionAreCompatible(){
    val dep = createDependecyWithVersion("(1.0,10.0)")
    Assert.assertTrue(dep.isVersionCompatible ("20.0"))
  }

  @Test
  fun mixedRangeVersionAreNotSupportedAndAllTheVersionAreCompatible(){
    val dep = arrayOf(
        createDependecyWithVersion("[1.0,10.0)"),
        createDependecyWithVersion("(1.0,10.0]"),
        createDependecyWithVersion("[10.0,)"),
        createDependecyWithVersion("]1.0,10.0]"),
        createDependecyWithVersion("[1.0,10.0[")
    )
    dep.forEach {
      Assert.assertTrue(it.isVersionCompatible("20.0"))
    }
  }

  private fun createDependecyWithVersion(version:String):Dependency =
    Dependency(ARTIFACT_A_100,Artifact("a","b"),version)

}