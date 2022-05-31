package io.provenance.classification.asset.verifier.client

import io.provenance.classification.asset.client.domain.model.AssetScopeAttribute
import io.provenance.classification.asset.client.domain.model.AssetVerificationResult
import io.provenance.classification.asset.verifier.provenance.AssetClassificationEvent

data class VerificationMessage(
    val failureMessagePrefix: String,
    val event: AssetClassificationEvent,
    val scopeAttribute: AssetScopeAttribute,
    val verification: AssetVerificationResult,
)
