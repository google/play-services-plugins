/**
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gms.oss.licenses.plugin

import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.Serializable

/**
 * Data class to hold the resolved physical files for a single dependency.
 */
class ArtifactFiles implements Serializable {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    File pomFile

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    File libraryFile

    ArtifactFiles(File pomFile, File libraryFile) {
        this.pomFile = pomFile
        this.libraryFile = libraryFile
    }
}
