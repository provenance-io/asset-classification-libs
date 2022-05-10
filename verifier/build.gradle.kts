import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("publish-kotlin-to-nexus")
    id("com.adarshr.test-logger")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api(project(":client"))

    listOf(
        // Bundles
        libs.bundles.coroutines,
        libs.bundles.eventStream,
        libs.bundles.scarlet,

        // Libraries
        libs.okHttp3,
    ).forEach(::api)

    testImplementation(libs.bundles.test)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Always re-run tests
    outputs.upToDateWhen { false }
}
