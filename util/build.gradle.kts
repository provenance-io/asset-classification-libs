dependencies {
    listOf(
        libs.bundles.jackson,
        libs.bundles.provenance,
        libs.bouncyCastleBcProv,
    ).forEach(::api)

    testImplementation(libs.bundles.test)
}
