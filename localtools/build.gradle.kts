dependencies {
    listOf(
        project(":client"),
        libs.assetSpecs,
        libs.feignJackson,
        libs.okHttp3,
    ).forEach(::api)
}
