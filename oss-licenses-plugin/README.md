# OSS Licenses Gradle Plugin

This Gradle plugin scans the POM dependencies of a project at compile time.
When a Maven POM exists for a direct dependency of the app, the plugin processes
the [`<licenses>`](https://maven.apache.org/pom.html#Licenses) element and
embeds the link and title of each license in an Android asset in the final app
APK.

For Google Play services dependencies, the license info is gathered from
third_party_licenses.json and third_party_licenses.txt files in the distributed
.aar.

The plugin will generate two text files based on the gathered licenses info:

  * third_party_licenses
  * third_party_licenses_metadata

and registers them as raw resources so that it can be consumed by the
play-services-oss-licenses library.

## To Use

### Add the Gradle plugin

In your root-level `build.gradle` make sure you are using the
[Google Maven repository](https://developer.android.com/studio/build/dependencies#google-maven)
and add the oss-licenses plugin to your dependencies:

    buildscript {
      repositories {
        // ...
        google()  // or maven { url "https://maven.google.com" } for Gradle <= 3
      }
      dependencies {
        // ...
        // Add this line:
        classpath 'com.google.android.gms:oss-licenses-plugin:0.10.4'
      }

In your app-level `build.gradle`, apply the plugin by adding the following line
under the existing `apply plugin: 'com.android.application'` at the top of the
file:

    apply plugin: 'com.google.android.gms.oss-licenses-plugin'

### Add the library to your app

In the `dependencies` section of your app-level `build.gradle`, add a dependency
on the `oss-licenses` library:

    implementation 'com.google.android.gms:play-services-oss-licenses:17.0.0'

### Displaying license information

When the application builds, the Gradle plugin will process the licenses and
add them to the app resources. To easily display them you can trigger an
activity provided by the `play-services-oss-licenses` library at an appropriate
point in your app:

    import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

    // ...

    // When the user selects an option to see the licenses:
    startActivity(new Intent(this, OssLicensesMenuActivity.class));

This will display a list of open source libraries that are compiled into the
app, whether part of Google Play services or not. Tapping the library name will
display additional license information for that library.

### Setting the `Activity` title

You can also set the title of the displayed activity:

    OssLicensesMenuActivity.setActivityTitle(getString(R.string.custom_license_title));
