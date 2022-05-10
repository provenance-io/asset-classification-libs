import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    `maven-publish`
    `java-library`
    signing
    id("io.github.gradle-nexus.publish-plugin")
}

val projectGroup = rootProject.group
val projectVersion = project.property("version")?.takeIf { it != "unspecified" }?.toString() ?: "1.0-SNAPSHOT"

val nexusUser = findProperty("nexusUser")?.toString() ?: System.getenv("NEXUS_USER")
val nexusPass = findProperty("nexusPass")?.toString() ?: System.getenv("NEXUS_PASS")

configure<io.github.gradlenexus.publishplugin.NexusPublishExtension> {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(findProject("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME"))
            password.set(findProject("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD"))
            stagingProfileId.set("3180ca260b82a7") // prevents querying for the staging profile id, performance optimization
        }
    }
    // 3180ca260b82a7
    // 3180ca260b82a7
}

subprojects {
    apply {
        plugin("maven-publish")
        plugin("kotlin")
        plugin("java-library")
        plugin("signing")
        plugin("core-config")
    }

    java {
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlin.RequiresOptIn")
            jvmTarget = "11"
        }
    }


    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    val artifactName = name
    val artifactVersion = projectVersion.toString()

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                artifactId = artifactName
                version = artifactVersion

                from(components["java"])

                pom {
                    name.set("Provenance Asset Classification Kotlin Libraries")
                    description.set("Various tools for interacting with the Asset Classification smart contract")
                    url.set("https://provenance.io")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("hyperschwartz")
                            name.set("Jacob Schwartz")
                            email.set("jschwartz@figure.com")
                        }
                    }

                    scm {
                        developerConnection.set("git@github.com:provenance.io/asset-classification-libs.git")
                        connection.set("https://github.com/provenance-io/asset-classification-libs.git")
                        url.set("https://github.com/provenance-io/asset-classification-libs")
                    }
                }
            }
        }

        configure<SigningExtension> {
            sign(publications["maven"])
        }
    }
}

object Repos {
    private object sonatype {
        const val snapshots = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
        const val releases = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
    }

    fun RepositoryHandler.sonatypeOss(projectVersion: String): MavenArtifactRepository {
        val murl =
            if (projectVersion == "1.0-SNAPSHOT") sonatype.snapshots
            else sonatype.releases

        return maven {
            name = "Sonatype"
            url = URI.create(murl)
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}