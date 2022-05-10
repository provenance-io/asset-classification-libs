rootProject.name = "asset-classification-libs"

include("client", "verifier")

pluginManagement {
    plugins {
        kotlin("jvm") version "1.6.10"
        id("com.adarshr.test-logger") version "3.2.0"
    }
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
