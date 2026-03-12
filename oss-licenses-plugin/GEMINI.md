# Gemini Developer Guide: OSS Licenses Plugin

This document provides essential information for AI agents and developers working on the `oss-licenses-plugin` and its tests.

## Test App Architecture

The project uses a single test application to test the plugin.

### Directory Structure
*   `oss-licenses-plugin/testapp/`: The build environment.
    *   **Gradle:** 9.4.0
    *   **AGP:** 9.0.0
    *   **SDK:** 36
    *   **Target:** Current stable/bleeding-edge versions.
*   `oss-licenses-plugin/testapp/app/`: The Source Code.
    *   Contains the actual Android application and Robolectric tests used by the environment.

### Test App End-to-End Suite
The `TestAppEndToEndTest.kt` file in the main plugin source executes the `testapp` across a matrix of AGP and Gradle versions. 
**Important:** When updating dependencies or AGP, you must verify and maintain the versions defined in this matrix to ensure broad compatibility.

### Running Tests Standalone

```bash
cd oss-licenses-plugin/testapp
./gradlew clean :app:test
```

### Local Repository Injection
The test app is configured to automatically pick up the locally built plugin if it has been published to the project's internal repository.
1.  **Publish:** `cd oss-licenses-plugin && ./gradlew publish`
2.  **Usage:** The app's `settings.gradle.kts` looks for `../build/repo`.
3.  **Library Override:** To test with a local version of the `play-services-oss-licenses` runtime library, pass the `libraryRepoPath` property:
    `./gradlew :app:test -PlibraryRepoPath=/path/to/your/m2repo`

### Test Separation (V1 vs V2)
Tests are split:
*   `OssLicensesV1Test.kt`: Standard Espresso tests for the original activity.
*   `OssLicensesV2Test.kt`: Compose tests for the V2 activity.