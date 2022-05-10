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
    listOf(
        // Bundles
        libs.bundles.grpc,
        libs.bundles.jackson,
        libs.bundles.protobuf,
        libs.bundles.provenance,

        // Libraries
        libs.bouncyCastleBcProv,
    ).forEach(::api)

    // Don't mandate a specific kotlin version.  Older versions should work fine with this lib, and
    // implementing projects will likely differ in their kotlin versions
    implementation(libs.bundles.kotlin)

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
