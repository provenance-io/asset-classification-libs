package io.provenance.classification.asset.verifier.testhelpers

import io.provenance.classification.asset.client.domain.model.AccessRoute
import io.provenance.classification.asset.client.domain.model.AssetScopeAttribute
import io.provenance.classification.asset.client.domain.model.AssetVerificationResult
import io.provenance.classification.asset.verifier.config.VerificationProcessor
import io.provenance.classification.asset.verifier.provenance.AssetClassificationEvent

class MockVerificationProcessor : VerificationProcessor<String> {
    var errorOnRetrieveAsset: Boolean = false
    var errorOnVerifyAsset: Boolean = false
    var verifySuccess: Boolean = true

    override suspend fun retrieveAsset(
        event: AssetClassificationEvent,
        scopeAttribute: AssetScopeAttribute,
        accessRoutes: List<AccessRoute>,
    ): String = if (errorOnRetrieveAsset) {
        throw IllegalStateException("MOCK: Producing error as requested during retrieveAsset")
    } else {
        "MOCK: Successful asset retrieval"
    }

    override suspend fun verifyAsset(
        event: AssetClassificationEvent,
        scopeAttribute: AssetScopeAttribute,
        asset: String,
    ): AssetVerificationResult = if (errorOnVerifyAsset) {
        throw IllegalStateException("MOCK: Producing error as requested during verifyAsset")
    } else {
        AssetVerificationResult(
            message = if (verifySuccess) "MOCK: Successful verification" else "MOCK: Failed verification",
            success = verifySuccess,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun toAny(): VerificationProcessor<Any> = this as VerificationProcessor<Any>
}
