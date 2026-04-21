/**
 * Copyright 2018-2026 Google LLC
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

class ArtifactInfo {
    private String group
    private String name
    private String version
    private String hash

    ArtifactInfo(String group,
                 String name,
                 String version) {
        this(group, name, version, null)
    }

    ArtifactInfo(String group,
                 String name,
                 String version,
                 String hash) {
        this.group = group
        this.name = name
        this.version = version
        this.hash = hash
    }

    String getGroup() {
        return group
    }

    String getName() {
        return name
    }

    String getVersion() {
        return version
    }

    String getHash() {
        return hash
    }

    @Override
    boolean equals(Object obj) {
        if (obj instanceof ArtifactInfo) {
            return (group == obj.group
                    && name == obj.name
                    && version == obj.version)
        }
        return false
    }

    @Override
    int hashCode() {
        int result = group.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + version.hashCode()
        return result
    }

    @Override
    String toString() {
        return "$group:$name:$version"
    }
}
