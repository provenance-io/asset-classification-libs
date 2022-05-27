package io.provenance.classification.asset.verifier.event

import io.provenance.classification.asset.client.domain.model.AssetScopeAttribute
import io.provenance.classification.asset.verifier.client.AssetVerification
import io.provenance.classification.asset.verifier.provenance.AssetClassificationEvent

data class VerificationMessage(
    val failureMessagePrefix: String,
    val event: AssetClassificationEvent,
    val scopeAttribute: AssetScopeAttribute,
    val verification: AssetVerification,
)
