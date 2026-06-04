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

import groovy.json.JsonSlurper
import groovy.xml.XmlSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.slf4j.LoggerFactory

import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Task to extract and bundle license information from application dependencies.
 *
 * This task is compatible with Gradle's Configuration Cache. All necessary file
 * mappings (POMs and Library artifacts) are provided as lazy input properties,
 * making the task a pure function of its inputs.
 */
@CacheableTask
abstract class LicensesTask extends DefaultTask {
    private static final String UTF_8 = "UTF-8"
    private static final byte[] LINE_SEPARATOR = System
            .getProperty("line.separator").getBytes(UTF_8)
    private static final String FAIL_READING_LICENSES_ERROR =
            "Failed to read license text."

    private static final logger = LoggerFactory.getLogger(LicensesTask.class)

    protected int start = 0
    protected Set<String> embeddedLicenses = []
    protected Map<String, String> licensesMap = [:]
    protected Map<String, String> licenseOffsets = [:]
    protected static final String ABSENT_DEPENDENCY_KEY = "Debug License Info"
    protected static final String ABSENT_DEPENDENCY_TEXT = ("Licenses are " +
            "only provided in build variants " +
            "(e.g. release) where the Android Gradle Plugin " +
            "generates an app dependency list.")

    /**
     * Library JARs/AARs keyed by "group:name:version", used to extract bundled license data
     * from Google Play Services / Firebase artifacts.
     *
     * Why {@code @Internal} instead of {@code @InputFiles}?
     * Gradle uses task input annotations to compute a cache key for up-to-date checks and build
     * cache lookups. If these maps were {@code @InputFiles}, Gradle would hash every JAR/AAR and
     * POM, which is expensive and redundant. The {@code dependenciesJson} file (which IS
     * {@code @InputFile}) already captures the full dependency set as a stable JSON list. Since
     * Maven Central artifacts are immutable per GAV coordinate (you can't re-publish the same
     * version), the physical files can only change when the dependency list itself changes —
     * which {@code dependenciesJson} already tracks. Using {@code @Internal} avoids the redundant
     * hashing while maintaining correctness.
     *
     * <p>SNAPSHOT edge case: {@code DependencyTask.snapshotHashes} tracks JAR/AAR content changes
     * for SNAPSHOT versions, invalidating {@code dependenciesJson} when the artifact content
     * changes. A re-published SNAPSHOT POM with unchanged JAR (e.g. only the {@code <licenses>}
     * block was edited) would not be detected — an acceptable gap given SNAPSHOTs are not an
     * expected distribution channel for consumers of this plugin.
     */
    @Internal
    abstract MapProperty<String, File> getLibraryFilesByGav()

    /**
     * POM files keyed by "group:name:version", for reading {@code <licenses>} URLs from Maven
     * metadata. {@code @Internal} for the same reason as {@link #getLibraryFilesByGav()}.
     */
    @Internal
    abstract MapProperty<String, File> getPomFilesByGav()

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getDependenciesJson()

    @OutputDirectory
    abstract DirectoryProperty getGeneratedDirectory()

    @Internal // output file within getGeneratedDirectory(); tracked via that @OutputDirectory
    File licenses

    @Internal // output file within getGeneratedDirectory(); tracked via that @OutputDirectory
    File licensesMetadata

    @TaskAction
    void action() {
        initOutputDir()

        Map<String, File> libraryMap = libraryFilesByGav.getOrElse([:])
        Map<String, File> pomMap = pomFilesByGav.getOrElse([:])

        File dependenciesJsonFile = dependenciesJson.asFile.get()
        Set<ArtifactInfo> artifactInfoSet = loadDependenciesJson(dependenciesJsonFile)

        if (DependencyTask.ABSENT_ARTIFACT in artifactInfoSet) {
            if (artifactInfoSet.size() > 1) {
                throw new IllegalStateException("artifactInfoSet that contains ABSENT_ARTIFACT should not contain other artifacts.")
            }
            addDebugLicense()
        } else {
            for (artifactInfo in artifactInfoSet) {
                // 1. Extract licenses from POM for all artifacts, except for side-car license artifacts.
                // Artifacts named "*-license" are containers for license data and shouldn't have
                // their own entry in the attribution list.
                if (!artifactInfo.name.endsWith("-license")) {
                    addLicensesFromPom(pomMap, artifactInfo)
                }

                // 2. For any artifact, try to extract embedded licenses if they exist.
                File libraryFile = libraryMap.get(artifactInfo.toString())
                if (libraryFile != null && libraryFile.exists()) {
                    addEmbeddedLicenses(libraryFile)
                }
            }
        }

        writeMetadata()
    }

    private static Set<ArtifactInfo> loadDependenciesJson(File jsonFile) {
        def allDependencies = new JsonSlurper().parse(jsonFile)
        def artifactInfoSet = new LinkedHashSet<ArtifactInfo>()
        // use LinkedHashSet to ensure stable output order
        for (entry in allDependencies) {
            ArtifactInfo artifactInfo = artifactInfoFromEntry(entry)
            artifactInfoSet.add(artifactInfo)
        }
        artifactInfoSet.asImmutable()
    }

    protected void addDebugLicense() {
        appendDependency(
                ABSENT_DEPENDENCY_KEY,
                ABSENT_DEPENDENCY_TEXT.getBytes(UTF_8)
        )
    }

    protected void initOutputDir() {
        File rawResourceDir = new File(getGeneratedDirectory().get().asFile, "raw")
        if (!rawResourceDir.exists()) {
            rawResourceDir.mkdirs()
        }
        licenses = new File(rawResourceDir, "third_party_licenses")
        licenses.newWriter().withWriter { w ->
            w << ''
        }
        licensesMetadata = new File(rawResourceDir, "third_party_license_metadata")
        licensesMetadata.newWriter().withWriter { w ->
            w << ''
        }
    }

    protected void addEmbeddedLicenses(File artifactFile) {
        try {
            new ZipFile(artifactFile).withCloseable { licensesZip ->
                def namespacedJsonEntries = []
                def entries = licensesZip.entries()
                while (entries.hasMoreElements()) {
                    def entry = entries.nextElement()
                    if (entry.name.startsWith("META-INF/third_party_licenses/") && entry.name.endsWith("/third_party_licenses.json")) {
                        namespacedJsonEntries.add(entry)
                    }
                }

                if (!namespacedJsonEntries.isEmpty()) {
                    for (jsonEntry in namespacedJsonEntries) {
                        String jsonPath = jsonEntry.name
                        String txtPath = jsonPath.substring(0, jsonPath.length() - 5) + ".txt"
                        ZipEntry txtEntry = licensesZip.getEntry(txtPath)
                        if (txtEntry) {
                            processLicenseEntry(licensesZip, jsonEntry, txtEntry)
                        }
                    }
                } else {
                    ZipEntry jsonFile = licensesZip.getEntry("third_party_licenses.json")
                    ZipEntry txtFile = licensesZip.getEntry("third_party_licenses.txt")

                    if (jsonFile && txtFile) {
                        processLicenseEntry(licensesZip, jsonFile, txtFile)
                    }
                }
            }
        } catch (ZipException e) {
            // Not a zip file, or malformed. Skip.
            logger.debug("Failed to open $artifactFile as a zip file: ${e.message}")
        } catch (IOException e) {
            logger.warn("Failed to read embedded licenses from $artifactFile: ${e.message}")
        }
    }

    protected void processLicenseEntry(ZipFile licensesZip, ZipEntry jsonFile, ZipEntry txtFile) {
        JsonSlurper jsonSlurper = new JsonSlurper()
        Object licensesObj = licensesZip.getInputStream(jsonFile).withCloseable {
            jsonSlurper.parse(it)
        }
        if (licensesObj == null) {
            return
        }

        for (entry in licensesObj) {
            String key = entry.key
            int startValue = entry.value.start
            int lengthValue = entry.value.length

            if (!embeddedLicenses.contains(key)) {
                licensesZip.getInputStream(txtFile).withCloseable {
                    byte[] content = getBytesFromInputStream(
                            it,
                            startValue,
                            lengthValue)
                    embeddedLicenses.add(key)
                    appendDependency(key, content)
                }
            }
        }
    }


    protected static byte[] getBytesFromInputStream(
            InputStream stream,
            long offset,
            int length) {
        try {
            byte[] buffer = new byte[1024]
            ByteArrayOutputStream textArray = new ByteArrayOutputStream()

            stream.skip(offset)
            int bytesRemaining = length > 0 ? length : Integer.MAX_VALUE
            int bytes = 0

            while (bytesRemaining > 0
                    && (bytes =
                    stream.read(
                            buffer,
                            0,
                            Math.min(bytesRemaining, buffer.length)))
                    != -1) {
                textArray.write(buffer, 0, bytes)
                bytesRemaining -= bytes
            }
            stream.close()

            return textArray.toByteArray()
        } catch (Exception e) {
            throw new RuntimeException(FAIL_READING_LICENSES_ERROR, e)
        }
    }

    protected void addLicensesFromPom(Map<String, File> pomMap, ArtifactInfo artifactInfo) {
        File pomFile = pomMap.get(artifactInfo.toString())
        addLicensesFromPom(pomFile, artifactInfo.group, artifactInfo.name)
    }

    protected void addLicensesFromPom(File pomFile, String group, String name) {
        if (pomFile == null || !pomFile.exists()) {
            logger.info("POM file $pomFile for $group:$name does not exist. This is expected for some libraries from androidx and org.jetbrains")
            return
        }

        def rootNode = new XmlSlurper().parse(pomFile)
        if (rootNode.licenses.size() == 0) {
            return
        }

        String libraryName = rootNode.name
        String licenseKey = "${group}:${name}"
        if (libraryName == null || libraryName.isBlank()) {
            libraryName = licenseKey
        }
        if (rootNode.licenses.license.size() > 1) {
            rootNode.licenses.license.each { license ->
                String licenseName = license.name
                String licenseUrl = license.url
                appendDependency(
                        new Dependency("${licenseKey} ${licenseName}", libraryName),
                        licenseUrl.getBytes(UTF_8))
            }
        } else {
            String nodeUrl = rootNode.licenses.license.url
            appendDependency(new Dependency(licenseKey, libraryName), nodeUrl.getBytes(UTF_8))
        }
    }

    protected void appendDependency(String key, byte[] license) {
        appendDependency(new Dependency(key, key), license)
    }

    protected void appendDependency(Dependency dependency, byte[] license) {
        String licenseText = new String(license, UTF_8)
        if (licensesMap.containsKey(dependency.key)) {
            return
        }

        String offsets
        if (licenseOffsets.containsKey(licenseText)) {
            offsets = licenseOffsets.get(licenseText)
        } else {
            offsets = "${start}:${license.length}"
            licenseOffsets.put(licenseText, offsets)
            appendLicenseContent(license)
            appendLicenseContent(LINE_SEPARATOR)
        }
        licensesMap.put(dependency.key, dependency.buildLicensesMetadata(offsets))
    }

    protected void appendLicenseContent(byte[] content) {
        licenses.append(content)
        start += content.length
    }

    protected void writeMetadata() {
        for (entry in licensesMap) {
            licensesMetadata.append(entry.value, UTF_8)
            licensesMetadata.append(LINE_SEPARATOR)
        }
    }

    static ArtifactInfo artifactInfoFromEntry(Object entry) {
        return new ArtifactInfo(entry.group, entry.name, entry.version)
    }

    protected static class Dependency {
        String key
        String name

        Dependency(String key, String name) {
            this.key = key
            this.name = name
        }

        String buildLicensesMetadata(String offset) {
            return "$offset $name"
        }
    }
}
