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
