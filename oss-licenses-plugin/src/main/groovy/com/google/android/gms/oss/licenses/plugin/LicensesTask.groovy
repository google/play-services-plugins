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

    /**
     * Extracts embedded licenses from the dependency artifact (JAR/AAR ZIP).
     *
     * This method scans the artifact for license metadata files packaged either at the root
     * (the legacy location) or under namespaced paths in META-INF. The namespaced layout
     * (META-INF/third_party_licenses/&lt;group&gt;/&lt;artifact&gt;/) is a specialized format used by a
     * subset of libraries built by Google (such as Google Play services and Firebase) to prevent
     * resource merging conflicts in downstream client builds. It processes all valid license
     * metadata and text pairs it finds and aggregates their records.
     */
    protected void addEmbeddedLicenses(File artifactFile) {
        try {
            new ZipFile(artifactFile).withCloseable { licensesZip ->
                // Explicitly find metadata files either at the root (legacy) or under the META-INF
                // namespaced prefix (specialized format for a subset of Google-built libraries)
                // to avoid accidentally processing unrelated resources or assets.
                def jsonEntries = licensesZip.entries().toList().findAll { entry ->
                    entry.name == "third_party_licenses.json" ||
                        (entry.name.startsWith("META-INF/third_party_licenses/") && entry.name.endsWith("/third_party_licenses.json"))
                }

                jsonEntries.each { jsonEntry ->
                    String txtPath = jsonEntry.name.replace(".json", ".txt")
                    ZipEntry txtEntry = licensesZip.getEntry(txtPath)
                    if (!txtEntry) {
                        logger.info("Missing license text file: ${txtPath}")
                        return
                    }
                    processLicenseEntry(licensesZip, jsonEntry, txtEntry)
                }
            }
        } catch (ZipException e) {
            logger.debug("Failed to open $artifactFile as a zip file: ${e.message}")
        } catch (IOException e) {
            logger.warn("Failed to read embedded licenses from $artifactFile: ${e.message}")
        }
    }

    /**
     * Parses the license metadata JSON file within the dependency ZIP, extracts
     * license texts at the specified offsets from the corresponding license text file,
     * and registers them with the task's aggregated license tracker.
     *
     * A record whose key or byte range is malformed is logged and skipped rather than aborting the
     * build. The caller, {@link #addEmbeddedLicenses(File)}, deliberately tolerates unreadable
     * third-party artifacts, and an unchecked exception thrown from here would escape that handler
     * and fail a consumer's build over a single bad entry.
     *
     * @param licensesZip the ZipFile representation of the dependency archive
     * @param jsonFile the ZipEntry for the third-party license JSON metadata file
     * @param txtFile the ZipEntry for the third-party license text file (.txt)
     */
    protected void processLicenseEntry(ZipFile licensesZip, ZipEntry jsonFile, ZipEntry txtFile) {
        JsonSlurper jsonSlurper = new JsonSlurper()
        Object licensesObj = licensesZip.getInputStream(jsonFile).withCloseable {
            jsonSlurper.parse(it)
        }
        if (licensesObj == null) {
            return
        }

        for (entry in licensesObj) {
            // A malformed entry must not fail the build: addEmbeddedLicenses() deliberately
            // tolerates unreadable third-party artifacts, so skip the record and keep going.
            try {
                String key = entry.key
                int startValue = entry.value.start
                int lengthValue = entry.value.length
                Dependency dependency = new Dependency(key, key)

                if (!embeddedLicenses.contains(dependency.key)) {
                    licensesZip.getInputStream(txtFile).withCloseable {
                        byte[] content = getBytesFromInputStream(
                                it,
                                startValue,
                                lengthValue)
                        embeddedLicenses.add(dependency.key)
                        appendDependency(dependency, content)
                    }
                }
            } catch (IllegalArgumentException | NullPointerException e) {
                logger.warn("Skipping malformed license entry in ${jsonFile.name}: ${e.message}")
            }
        }
    }


    /**
     * Reads the license text occupying {@code length} bytes at {@code offset}, always closing
     * {@code stream} before returning.
     *
     * Why is a {@code length} of zero read to the end of the stream rather than treated as an
     * empty range? Groovy coerces an absent JSON {@code length} field to {@code 0}, so zero means
     * "the artifact did not say", not "no bytes". Returning an empty array would silently drop the
     * attribution text for every artifact with incomplete metadata.
     *
     * @param stream the license text stream, closed on every return path including failures
     * @param offset the byte offset at which this dependency's license text begins
     * @param length the number of bytes to read, or zero to read to the end of the stream
     * @return the license text bytes
     * @throws IllegalArgumentException if {@code offset} or {@code length} is negative
     * @throws RuntimeException if the stream cannot be read
     */
    protected static byte[] getBytesFromInputStream(
            InputStream stream,
            long offset,
            int length) {
        return stream.withCloseable { InputStream is ->
            if (offset < 0 || length < 0) {
                throw new IllegalArgumentException("offset and length must be non-negative: offset=$offset, length=$length")
            }
            try {
                byte[] buffer = new byte[1024]
                ByteArrayOutputStream textArray = new ByteArrayOutputStream()

                is.skip(offset)
                // A length of 0 means the license metadata omitted the field. Read to the end
                // of the stream, as before, rather than silently shipping an empty attribution.
                int bytesRemaining = length > 0 ? length : Integer.MAX_VALUE
                int bytes = 0

                while (bytesRemaining > 0
                        && (bytes =
                        is.read(
                                buffer,
                                0,
                                Math.min(bytesRemaining, buffer.length)))
                        != -1) {
                    textArray.write(buffer, 0, bytes)
                    bytesRemaining -= bytes
                }

                return textArray.toByteArray()
            } catch (Exception e) {
                throw new RuntimeException(FAIL_READING_LICENSES_ERROR, e)
            }
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

    /**
     * One attribution record: the key that identifies a dependency for deduplication, and the
     * display name shown in the consuming app's license menu.
     *
     * Both values originate from dependency-authored metadata — a Maven POM {@code <name>} element
     * or a key in an AAR's third_party_licenses.json — and are sanitized here, at the only point
     * where an instance can be created. The metadata file these records are written to is newline
     * delimited, so an unsanitized line break would terminate a record early and let the remainder
     * forge a second, fully attacker-controlled entry.
     *
     * Why are the fields {@code final}? A non-final Groovy property generates a public setter and
     * enables the map constructor, either of which would write an unsanitized value straight past
     * the constructor.
     */
    protected static class Dependency {
        final String key
        final String name

        /**
         * @param key identifies the dependency for deduplication; rejected when blank, as there is
         *     no substitute for it and a blank key silently collapses distinct dependencies
         * @param name the display name, falling back to the sanitized key when blank, because a
         *     record with no display name attributes nothing; every current caller already
         *     supplies a non-blank name, so the fallback is defensive only
         * @throws IllegalArgumentException if {@code key} is blank once sanitized
         * @throws NullPointerException if {@code key} or {@code name} is null
         */
        Dependency(String key, String name) {
            this.key = sanitize(key, "key")
            if (this.key.isEmpty()) {
                throw new IllegalArgumentException("key cannot be empty")
            }
            String sanitizedName = sanitize(name, "name")
            this.name = sanitizedName.isEmpty() ? this.key : sanitizedName
        }

        /**
         * Collapses each run of line breaks in {@code value} to a single space, then trims.
         *
         * {@code \R} matches every Unicode line break — LF, CR, CRLF, vertical tab, form feed,
         * NEL, and the U+2028 and U+2029 separators — so an unusual encoding cannot evade this.
         * {@code strip} handles the resulting edges and, unlike {@code trim}, is Unicode-aware.
         *
         * @param value the raw, dependency-authored string
         * @param fieldName the field being sanitized, used in the null-check message
         * @return the single-line, trimmed value, empty only if {@code value} was blank
         */
        private static String sanitize(String value, String fieldName) {
            return Objects.requireNonNull(value, "$fieldName cannot be null")
                    .replaceAll(/\R+/, ' ')
                    .strip()
        }

        /**
         * Renders this dependency's line in the newline-delimited metadata file.
         *
         * @param offset the "start:length" pair locating this dependency's license text
         * @return one record; the name is sanitized, so the line cannot be split
         */
        String buildLicensesMetadata(String offset) {
            return "$offset $name"
        }
    }
}
