/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.gms.googleservices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.util.TreeMap

@CacheableTask
abstract class GoogleServicesTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    val intermediateDir: File
        /** Reintroduced for binary compatiblity with the crashlytics plugin  */
        get() = outputDirectory.asFile.get()

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val googleServicesJsonFiles: Property<Collection<Directory>>

    @get:Input
    abstract val applicationId: Property<String>

    @Throws(GradleException::class)
    @TaskAction
    fun action() {
        val jsonFiles = googleServicesJsonFiles.get().map { it.file(JSON_FILE_NAME).asFile }
            .filter { it.isFile }
        if (jsonFiles.size > 1) {
            throw GradleException(
                """
                More than one $JSON_FILE_NAME found: ${jsonFiles.joinToString { it.path }}. 
                Please remove the ambiguity by making sure there is one file for each variant.
                """.trimIndent()
            )
        }

        if (jsonFiles.isEmpty()) {
            throw GradleException(
                """
                File $JSON_FILE_NAME is missing. 
                The Google Services Plugin cannot function without it. 
                Searched locations: ${googleServicesJsonFiles.get().joinToString { it.asFile.path }}
                """.trimIndent()
            )
        }

        val quickstartFile = jsonFiles.single()
        logger.info("Parsing json file: " + quickstartFile.path)

        // delete content of outputdir.
        val intermediateDir = outputDirectory.get().asFile
        intermediateDir.deleteRecursively()

        if (!intermediateDir.mkdirs()) {
            throw GradleException("Failed to create folder: $intermediateDir")
        }

        val root = JsonParser().parse(quickstartFile.bufferedReader(Charsets.UTF_8))
        if (!root.isJsonObject) {
            throw GradleException("Malformed root json")
        }
        val rootObject = root.asJsonObject
        val resValues: MutableMap<String, String?> =
            TreeMap() // TreeMap to preserve order with previous plugin versions
        handleProjectNumberAndProjectId(rootObject, resValues)
        handleFirebaseUrl(rootObject, resValues)
        val clientObject = getClientForPackageName(rootObject)
        if (clientObject != null) {
            handleAnalytics(clientObject, resValues)
            handleMapsService(clientObject, resValues)
            handleGoogleApiKey(clientObject, resValues)
            handleGoogleAppId(clientObject, resValues)
            handleWebClientId(clientObject, resValues)
        } else {
            throw GradleException("No matching client found for package name '${applicationId.get()}' in ${quickstartFile.path}")
        }

        // write the values file.
        val values = File(intermediateDir, "values")
        if (!values.exists() && !values.mkdirs()) {
            throw GradleException("Failed to create folder: $values")
        }
        File(values, "values.xml").writeText(
            getValuesContent(resValues), Charsets.UTF_8
        )
    }

    @Throws(IOException::class)
    private fun handleFirebaseUrl(rootObject: JsonObject, resValues: MutableMap<String, String?>) {
        val projectInfo = rootObject.getAsJsonObject("project_info")
            ?: throw GradleException("Missing project_info object")
        val firebaseUrl = projectInfo.getAsJsonPrimitive("firebase_url")
        if (firebaseUrl != null) {
            resValues["firebase_database_url"] = firebaseUrl.asString
        }
    }

    /**
     * Handle project_info/project_number for @string/gcm_defaultSenderId, and fill the res map with
     * the read value.
     *
     * @param rootObject the root Json object.
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun handleProjectNumberAndProjectId(
        rootObject: JsonObject, resValues: MutableMap<String, String?>
    ) {
        val projectInfo = rootObject.getAsJsonObject("project_info")
            ?: throw GradleException("Missing project_info object")
        val projectNumber = projectInfo.getAsJsonPrimitive("project_number")
            ?: throw GradleException("Missing project_info/project_number object")
        resValues["gcm_defaultSenderId"] = projectNumber.asString
        val projectId = projectInfo.getAsJsonPrimitive("project_id")
            ?: throw GradleException("Missing project_info/project_id object")
        resValues["project_id"] = projectId.asString
        val bucketName = projectInfo.getAsJsonPrimitive("storage_bucket")
        if (bucketName != null) {
            resValues["google_storage_bucket"] = bucketName.asString
        }
    }

    private fun handleWebClientId(
        clientObject: JsonObject, resValues: MutableMap<String, String?>
    ) {
        val array = clientObject.getAsJsonArray("oauth_client")
        if (array != null) {
            val count = array.size()
            for (i in 0 until count) {
                val oauthClientElement = array[i]
                if (oauthClientElement == null || !oauthClientElement.isJsonObject) {
                    continue
                }
                val oauthClientObject = oauthClientElement.asJsonObject
                val clientType = oauthClientObject.getAsJsonPrimitive("client_type") ?: continue
                val clientTypeStr = clientType.asString
                if (OAUTH_CLIENT_TYPE_WEB != clientTypeStr) {
                    continue
                }
                val clientId = oauthClientObject.getAsJsonPrimitive("client_id") ?: continue
                resValues["default_web_client_id"] = clientId.asString
                return
            }
        }
    }

    /**
     * Handle a client object for analytics (@xml/global_tracker)
     *
     * @param clientObject the client Json object.
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun handleAnalytics(clientObject: JsonObject, resValues: MutableMap<String, String?>) {
        val analyticsService = getServiceByName(clientObject, "analytics_service") ?: return
        val analyticsProp = analyticsService.getAsJsonObject("analytics_property") ?: return
        val trackingId = analyticsProp.getAsJsonPrimitive("tracking_id") ?: return
        resValues["ga_trackingId"] = trackingId.asString
        val xml = File(outputDirectory.get().asFile, "xml")
        if (!xml.exists() && !xml.mkdirs()) {
            throw GradleException("Failed to create folder: $xml")
        }
        File(xml, "global_tracker.xml").writeText(
            getGlobalTrackerContent(trackingId.asString), Charsets.UTF_8
        )
    }

    /**
     * Handle a client object for maps (@string/google_maps_key).
     *
     * @param clientObject the client Json object.
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun handleMapsService(
        clientObject: JsonObject, resValues: MutableMap<String, String?>
    ) {
        val mapsService = getServiceByName(clientObject, "maps_service") ?: return
        val apiKey = getAndroidApiKey(clientObject)
        if (apiKey != null) {
            resValues["google_maps_key"] = apiKey
            return
        }
        throw GradleException("Missing api_key/current_key object")
    }

    private fun handleGoogleApiKey(
        clientObject: JsonObject, resValues: MutableMap<String, String?>
    ) {
        val apiKey = getAndroidApiKey(clientObject)
        if (apiKey != null) {
            resValues["google_api_key"] = apiKey
            // TODO: remove this once SDK starts to use google_api_key.
            resValues["google_crash_reporting_api_key"] = apiKey
            return
        }
        throw GradleException("Missing api_key/current_key object")
    }

    private fun getAndroidApiKey(clientObject: JsonObject): String? {
        val array = clientObject.getAsJsonArray("api_key")
        if (array != null) {
            val count = array.size()
            for (i in 0 until count) {
                val apiKeyElement = array[i]
                if (apiKeyElement == null || !apiKeyElement.isJsonObject) {
                    continue
                }
                val apiKeyObject = apiKeyElement.asJsonObject
                val currentKey = apiKeyObject.getAsJsonPrimitive("current_key") ?: continue
                return currentKey.asString
            }
        }
        return null
    }

    /**
     * find an item in the "client" array that match the package name of the app
     *
     * @param jsonObject the root json object.
     * @return a JsonObject representing the client entry or null if no match is found.
     */
    private fun getClientForPackageName(jsonObject: JsonObject): JsonObject? {
        val array = jsonObject.getAsJsonArray("client")
        if (array != null) {
            val count = array.size()
            for (i in 0 until count) {
                val clientElement = array[i]
                if (clientElement == null || !clientElement.isJsonObject) {
                    continue
                }
                val clientObject = clientElement.asJsonObject
                val clientInfo = clientObject.getAsJsonObject("client_info") ?: continue
                val androidClientInfo =
                    clientInfo.getAsJsonObject("android_client_info") ?: continue
                val clientPackageName =
                    androidClientInfo.getAsJsonPrimitive("package_name") ?: continue
                if (applicationId.get() == clientPackageName.asString) {
                    return clientObject
                }
            }
        }
        return null
    }

    /** Handle a client object for Google App Id.  */
    @Throws(IOException::class)
    private fun handleGoogleAppId(
        clientObject: JsonObject, resValues: MutableMap<String, String?>
    ) {
        val clientInfo = clientObject.getAsJsonObject("client_info") ?: // Should not happen
        throw GradleException("Client does not have client info")
        val googleAppId = clientInfo.getAsJsonPrimitive("mobilesdk_app_id")
        val googleAppIdStr = googleAppId?.asString
        if (googleAppIdStr.isNullOrEmpty()) {
            throw GradleException(
                "Missing Google App Id. " + "Please follow instructions on https://firebase.google.com/ to get a valid " + "config file that contains a Google App Id"
            )
        }
        resValues["google_app_id"] = googleAppIdStr
    }

    /**
     * Finds a service by name in the client object. Returns null if the service is not found or if
     * the service is disabled.
     *
     * @param clientObject the json object that represents the client.
     * @param serviceName the service name
     * @return the service if found.
     */
    private fun getServiceByName(clientObject: JsonObject, serviceName: String): JsonObject? {
        val services = clientObject.getAsJsonObject("services") ?: return null
        val service = services.getAsJsonObject(serviceName) ?: return null
        val status = service.getAsJsonPrimitive("status") ?: return null
        val statusStr = status.asString
        if (STATUS_DISABLED == statusStr) return null
        if (STATUS_ENABLED != statusStr) {
            logger.warn(
                String.format(
                    "Status with value '%1\$s' for service '%2\$s' is unknown",
                    statusStr,
                    serviceName
                )
            )
            return null
        }
        return service
    }

    companion object {
        const val JSON_FILE_NAME = "google-services.json"
        private const val STATUS_DISABLED = "1"
        private const val STATUS_ENABLED = "2"
        private const val OAUTH_CLIENT_TYPE_WEB = "3"

        private fun getGlobalTrackerContent(ga_trackingId: String): String {
            return """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="ga_trackingId" translatable="false">$ga_trackingId</string>
</resources>
"""
        }

        private fun getValuesContent(
            values: Map<String, String?>
        ): String {
            val sb = StringBuilder(256)
            sb.append(
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <resources>
                    
                """.trimIndent()
            )
            for ((name, value) in values) {
                sb.append("    <string name=\"").append(name).append("\" translatable=\"false\"")
                sb.append(">").append(value).append("</string>\n")
            }
            sb.append("</resources>\n")
            return sb.toString()
        }
    }
}