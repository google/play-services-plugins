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

For detailed instructions on how to add the plugin to your project, configure dependencies, and display license information using the SDK, please refer to the official documentation:

👉 **[Include open source notices](https://developers.google.com/android/guides/opensource)**

## Source Code and Contributing

The source code for this plugin is hosted in this repository. For contributing guidelines or to report issues, please see the root project documentation.
