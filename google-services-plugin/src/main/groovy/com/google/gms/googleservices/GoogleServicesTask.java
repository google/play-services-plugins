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

package com.google.gms.googleservices;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** */
public class GoogleServicesTask extends DefaultTask {

  private static final String STATUS_DISABLED = "1";
  private static final String STATUS_ENABLED = "2";

  private static final String OAUTH_CLIENT_TYPE_WEB = "3";

  private File quickstartFile;
  private File intermediateDir;
  private String packageNameXOR1;
  private TextResource packageNameXOR2;
  private String searchedLocation;

  /**
   * The input is not technically optional but we want to control the error message.
   * Without @Optional, Gradle will complain itself the file is missing.
   */
  @InputFile @Optional
  public File getQuickstartFile() {
    return quickstartFile;
  }

  @OutputDirectory
  public File getIntermediateDir() {
    return intermediateDir;
  }

  /**
   * Either packageNameXOR1 or packageNameXOR2 must be present, but both must be marked as @Optional or Gradle
   * will throw an exception if one is missing.
   */
  @Input @Optional
  public String getPackageNameXOR1() {
    return packageNameXOR1;
  }

  @Input @Optional
  public TextResource getPackageNameXOR2() {
    return packageNameXOR2;
  }

  @Input
  public String getSearchedLocation() {
    return searchedLocation;
  }

  public void setQuickstartFile(File quickstartFile) {
    this.quickstartFile = quickstartFile;
  }

  public void setIntermediateDir(File intermediateDir) {
    this.intermediateDir = intermediateDir;
  }

  public void setPackageNameXOR1(String packageNameXOR1) {
    this.packageNameXOR1 = packageNameXOR1;
  }

  public void setPackageNameXOR2(TextResource packageNameXOR2) {
    this.packageNameXOR2 = packageNameXOR2;
  }

  public void setSearchedLocation(String searchedLocation) {
    this.searchedLocation = searchedLocation;
  }


  @TaskAction
  public void action() throws IOException {
    if (!quickstartFile.isFile()) {
      throw new GradleException(
          String.format(
              "File %s is missing. "
                  + "The Google Services Plugin cannot function without it. %n Searched Location: %s",
              quickstartFile.getName(), searchedLocation));
    }
    if (packageNameXOR1 == null && packageNameXOR2 == null) {
      throw new GradleException(
              String.format(
                      "One of packageNameXOR1 or packageNameXOR2 are required: "
                              + "packageNameXOR1: %s, packageNameXOR2: %s",
                      packageNameXOR1, packageNameXOR2));
    }

    getProject().getLogger().warn("Parsing json file: " + quickstartFile.getPath());

    // delete content of outputdir.
    deleteFolder(intermediateDir);
    if (!intermediateDir.mkdirs()) {
      throw new GradleException("Failed to create folder: " + intermediateDir);
    }

    JsonElement root = new JsonParser().parse(Files.newReader(quickstartFile, Charsets.UTF_8));

    if (!root.isJsonObject()) {
      throw new GradleException("Malformed root json");
    }

    JsonObject rootObject = root.getAsJsonObject();

    Map<String, String> resValues = new TreeMap<>();
    Map<String, Map<String, String>> resAttributes = new TreeMap<>();

    handleProjectNumberAndProjectId(rootObject, resValues);
    handleFirebaseUrl(rootObject, resValues);

    JsonObject clientObject = getClientForPackageName(rootObject);

    if (clientObject != null) {
      handleAnalytics(clientObject, resValues);
      handleMapsService(clientObject, resValues);
      handleGoogleApiKey(clientObject, resValues);
      handleGoogleAppId(clientObject, resValues);
      handleWebClientId(clientObject, resValues);
    } else {
      throw new GradleException("No matching client found for package name '" + getPackageName() + "'");
    }

    // write the values file.
    File values = new File(intermediateDir, "values");
    if (!values.exists() && !values.mkdirs()) {
      throw new GradleException("Failed to create folder: " + values);
    }

    Files.asCharSink(new File(values, "values.xml"), Charsets.UTF_8)
        .write(getValuesContent(resValues, resAttributes));
  }

  private void handleFirebaseUrl(JsonObject rootObject, Map<String, String> resValues)
      throws IOException {
    JsonObject projectInfo = rootObject.getAsJsonObject("project_info");
    if (projectInfo == null) {
      throw new GradleException("Missing project_info object");
    }

    JsonPrimitive firebaseUrl = projectInfo.getAsJsonPrimitive("firebase_url");
    if (firebaseUrl != null) {
      resValues.put("firebase_database_url", firebaseUrl.getAsString());
    }
  }

  /**
   * Handle project_info/project_number for @string/gcm_defaultSenderId, and fill the res map with
   * the read value.
   *
   * @param rootObject the root Json object.
   * @throws IOException
   */
  private void handleProjectNumberAndProjectId(JsonObject rootObject, Map<String, String> resValues)
      throws IOException {
    JsonObject projectInfo = rootObject.getAsJsonObject("project_info");
    if (projectInfo == null) {
      throw new GradleException("Missing project_info object");
    }

    JsonPrimitive projectNumber = projectInfo.getAsJsonPrimitive("project_number");
    if (projectNumber == null) {
      throw new GradleException("Missing project_info/project_number object");
    }

    resValues.put("gcm_defaultSenderId", projectNumber.getAsString());

    JsonPrimitive projectId = projectInfo.getAsJsonPrimitive("project_id");

    if (projectId == null) {
      throw new GradleException("Missing project_info/project_id object");
    }
    resValues.put("project_id", projectId.getAsString());

    JsonPrimitive bucketName = projectInfo.getAsJsonPrimitive("storage_bucket");
    if (bucketName != null) {
      resValues.put("google_storage_bucket", bucketName.getAsString());
    }
  }

  private void handleWebClientId(JsonObject clientObject, Map<String, String> resValues) {
    JsonArray array = clientObject.getAsJsonArray("oauth_client");
    if (array != null) {
      final int count = array.size();
      for (int i = 0; i < count; i++) {
        JsonElement oauthClientElement = array.get(i);
        if (oauthClientElement == null || !oauthClientElement.isJsonObject()) {
          continue;
        }
        JsonObject oauthClientObject = oauthClientElement.getAsJsonObject();
        JsonPrimitive clientType = oauthClientObject.getAsJsonPrimitive("client_type");
        if (clientType == null) {
          continue;
        }
        String clientTypeStr = clientType.getAsString();
        if (!OAUTH_CLIENT_TYPE_WEB.equals(clientTypeStr)) {
          continue;
        }
        JsonPrimitive clientId = oauthClientObject.getAsJsonPrimitive("client_id");
        if (clientId == null) {
          continue;
        }
        resValues.put("default_web_client_id", clientId.getAsString());
        return;
      }
    }
  }

  /**
   * Handle a client object for analytics (@xml/global_tracker)
   *
   * @param clientObject the client Json object.
   * @throws IOException
   */
  private void handleAnalytics(JsonObject clientObject, Map<String, String> resValues)
      throws IOException {
    JsonObject analyticsService = getServiceByName(clientObject, "analytics_service");
    if (analyticsService == null) return;

    JsonObject analyticsProp = analyticsService.getAsJsonObject("analytics_property");
    if (analyticsProp == null) return;

    JsonPrimitive trackingId = analyticsProp.getAsJsonPrimitive("tracking_id");
    if (trackingId == null) return;

    resValues.put("ga_trackingId", trackingId.getAsString());

    File xml = new File(intermediateDir, "xml");
    if (!xml.exists() && !xml.mkdirs()) {
      throw new GradleException("Failed to create folder: " + xml);
    }

    Files.asCharSink(new File(xml, "global_tracker.xml"), Charsets.UTF_8)
        .write(getGlobalTrackerContent(trackingId.getAsString()));
  }

  /**
   * Handle a client object for maps (@string/google_maps_key).
   *
   * @param clientObject the client Json object.
   * @throws IOException
   */
  private void handleMapsService(JsonObject clientObject, Map<String, String> resValues)
      throws IOException {
    JsonObject mapsService = getServiceByName(clientObject, "maps_service");
    if (mapsService == null) return;

    String apiKey = getAndroidApiKey(clientObject);
    if (apiKey != null) {
      resValues.put("google_maps_key", apiKey);
      return;
    }
    throw new GradleException("Missing api_key/current_key object");
  }

  private void handleGoogleApiKey(JsonObject clientObject, Map<String, String> resValues) {
    String apiKey = getAndroidApiKey(clientObject);
    if (apiKey != null) {
      resValues.put("google_api_key", apiKey);
      // TODO: remove this once SDK starts to use google_api_key.
      resValues.put("google_crash_reporting_api_key", apiKey);
      return;
    }

    // if google_crash_reporting_api_key is missing.
    // throw new GradleException("Missing api_key/current_key object");
    throw new GradleException("Missing api_key/current_key object");
  }

  private String getAndroidApiKey(JsonObject clientObject) {
    JsonArray array = clientObject.getAsJsonArray("api_key");
    if (array != null) {
      final int count = array.size();
      for (int i = 0; i < count; i++) {
        JsonElement apiKeyElement = array.get(i);
        if (apiKeyElement == null || !apiKeyElement.isJsonObject()) {
          continue;
        }
        JsonObject apiKeyObject = apiKeyElement.getAsJsonObject();
        JsonPrimitive currentKey = apiKeyObject.getAsJsonPrimitive("current_key");
        if (currentKey == null) {
          continue;
        }
        return currentKey.getAsString();
      }
    }
    return null;
  }

  private static void findStringByName(
      JsonObject jsonObject, String stringName, Map<String, String> resValues) {
    JsonPrimitive id = jsonObject.getAsJsonPrimitive(stringName);
    if (id != null) {
      resValues.put(stringName, id.getAsString());
    }
  }

  /**
   * find an item in the "client" array that match the package name of the app
   *
   * @param jsonObject the root json object.
   * @return a JsonObject representing the client entry or null if no match is found.
   */
  private JsonObject getClientForPackageName(JsonObject jsonObject) {
    JsonArray array = jsonObject.getAsJsonArray("client");
    if (array != null) {
      final int count = array.size();
      for (int i = 0; i < count; i++) {
        JsonElement clientElement = array.get(i);
        if (clientElement == null || !clientElement.isJsonObject()) {
          continue;
        }

        JsonObject clientObject = clientElement.getAsJsonObject();

        JsonObject clientInfo = clientObject.getAsJsonObject("client_info");
        if (clientInfo == null) continue;

        JsonObject androidClientInfo = clientInfo.getAsJsonObject("android_client_info");
        if (androidClientInfo == null) continue;

        JsonPrimitive clientPackageName = androidClientInfo.getAsJsonPrimitive("package_name");
        if (clientPackageName == null) continue;

        if (getPackageName().equals(clientPackageName.getAsString())) {
          return clientObject;
        }
      }
    }

    return null;
  }

  /** Handle a client object for Google App Id. */
  private void handleGoogleAppId(JsonObject clientObject, Map<String, String> resValues)
      throws IOException {
    JsonObject clientInfo = clientObject.getAsJsonObject("client_info");
    if (clientInfo == null) {
      // Should not happen
      throw new GradleException("Client does not have client info");
    }

    JsonPrimitive googleAppId = clientInfo.getAsJsonPrimitive("mobilesdk_app_id");

    String googleAppIdStr = googleAppId == null ? null : googleAppId.getAsString();
    if (Strings.isNullOrEmpty(googleAppIdStr)) {
      throw new GradleException(
          "Missing Google App Id. "
              + "Please follow instructions on https://firebase.google.com/ to get a valid "
              + "config file that contains a Google App Id");
    }

    resValues.put("google_app_id", googleAppIdStr);
  }

  /**
   * Finds a service by name in the client object. Returns null if the service is not found or if
   * the service is disabled.
   *
   * @param clientObject the json object that represents the client.
   * @param serviceName the service name
   * @return the service if found.
   */
  private JsonObject getServiceByName(JsonObject clientObject, String serviceName) {
    JsonObject services = clientObject.getAsJsonObject("services");
    if (services == null) return null;

    JsonObject service = services.getAsJsonObject(serviceName);
    if (service == null) return null;

    JsonPrimitive status = service.getAsJsonPrimitive("status");
    if (status == null) return null;

    String statusStr = status.getAsString();

    if (STATUS_DISABLED.equals(statusStr)) return null;
    if (!STATUS_ENABLED.equals(statusStr)) {
      getLogger()
          .warn(
              String.format(
                  "Status with value '%1$s' for service '%2$s' is unknown",
                  statusStr, serviceName));
      return null;
    }

    return service;
  }

  private static String getGlobalTrackerContent(String ga_trackingId) {
    return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        + "<resources>\n"
        + "    <string name=\"ga_trackingId\" translatable=\"false\">"
        + ga_trackingId
        + "</string>\n"
        + "</resources>\n";
  }

  private static String getValuesContent(
      Map<String, String> values, Map<String, Map<String, String>> attributes) {
    StringBuilder sb = new StringBuilder(256);

    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + "<resources>\n");

    for (Map.Entry<String, String> entry : values.entrySet()) {
      String name = entry.getKey();
      sb.append("    <string name=\"").append(name).append("\" translatable=\"false\"");
      if (attributes.containsKey(name)) {
        for (Map.Entry<String, String> attr : attributes.get(name).entrySet()) {
          sb.append(" ").append(attr.getKey()).append("=\"").append(attr.getValue()).append("\"");
        }
      }
      sb.append(">").append(entry.getValue()).append("</string>\n");
    }

    sb.append("</resources>\n");

    return sb.toString();
  }

  private static void deleteFolder(final File folder) {
    if (!folder.exists()) {
      return;
    }
    File[] files = folder.listFiles();
    if (files != null) {
      for (final File file : files) {
        if (file.isDirectory()) {
          deleteFolder(file);
        } else {
          if (!file.delete()) {
            throw new GradleException("Failed to delete: " + file);
          }
        }
      }
    }
    if (!folder.delete()) {
      throw new GradleException("Failed to delete: " + folder);
    }
  }
  
  private String getPackageName() {
    if (packageNameXOR1 == null) {
      return packageNameXOR2.asString();
    }
    return packageNameXOR1;
  }
}
