package io.provenance.classification.asset.verifier.client

data class VerifierTxEvents(
    val events: List<VerifierTxEvent>,
)

data class VerifierTxEvent(
    val type: String,
    val attributes: List<VerifierEventAttribute>,
)

data class VerifierEventAttribute(
    val key: String?,
    val value: String?,
)
