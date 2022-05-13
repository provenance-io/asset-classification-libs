package io.provenance.classification.asset.localtools.feign

import feign.Param
import feign.RequestLine
import io.provenance.classification.asset.localtools.models.github.GitHubReleaseResponse

/**
 * Simple client to communicate with GitHub's open api to retrieve an asset for an open source project.
 */
interface GitHubApiClient {
    @RequestLine("GET /repos/{organization}/{repository}/releases/latest")
    fun getReleases(
        @Param("organization") organization: String,
        @Param("repository") repository: String,
    ): GitHubReleaseResponse

    companion object {
        fun new(): GitHubApiClient = FeignUtil.getBuilder().target(GitHubApiClient::class.java, "https://api.github.com")
    }
}
