// This plugin should be imported into subprojects via: id("asset-classification.publishing")
// It will automagically derive a group and version for the project, and publish produced kotlin files to nexus

plugins {
    id("maven-publish")
}

group = "io.provenance.classification.asset"
version = project.property("version")?.takeIf { it != "unspecified" } ?: "1.0-SNAPSHOT"

// TODO: Fix this to actually publish to the correct location
publishing {
    repositories {
        maven {
            // TODO: Maven location
        }
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }
}
