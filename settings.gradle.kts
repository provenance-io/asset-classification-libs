rootProject.name = "asset-classification-libs"

include("client", "verifier")

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

gradle.rootProject {
    val libraryVersion = rootProject.property("libraryVersion") ?: error("Missing libraryVersion - check gradle.properties")
    allprojects {
        group = "io.provenance.classification.asset"
        version = libraryVersion
        description = "Various tools for interacting with the Asset Classification smart contract"
    }
}
