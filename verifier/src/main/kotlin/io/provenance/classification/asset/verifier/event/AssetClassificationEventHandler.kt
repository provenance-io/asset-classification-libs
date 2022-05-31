package io.provenance.classification.asset.verifier.event

import io.provenance.classification.asset.client.client.base.ACClient
import io.provenance.classification.asset.util.wallet.ProvenanceAccountDetail
import io.provenance.classification.asset.verifier.client.VerificationMessage
import io.provenance.classification.asset.verifier.config.VerificationProcessor
import io.provenance.classification.asset.verifier.config.VerifierEvent
import io.provenance.classification.asset.verifier.provenance.ACContractEvent
import io.provenance.classification.asset.verifier.provenance.AssetClassificationEvent
import kotlinx.coroutines.channels.Channel

interface AssetClassificationEventHandler {
    val eventType: ACContractEvent

    suspend fun handleEvent(parameters: EventHandlerParameters)
}

data class EventHandlerParameters(
    val event: AssetClassificationEvent,
    val acClient: ACClient,
    val verifierAccount: ProvenanceAccountDetail,
    val processor: VerificationProcessor<Any>,
    val verificationChannel: Channel<VerificationMessage>,
    val eventChannel: Channel<VerifierEvent>,
)
