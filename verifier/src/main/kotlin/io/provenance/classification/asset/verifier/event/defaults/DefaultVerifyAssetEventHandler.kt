package io.provenance.classification.asset.verifier.event.defaults

import io.provenance.classification.asset.client.domain.model.AssetOnboardingStatus
import io.provenance.classification.asset.verifier.config.VerifierEvent.EventIgnoredMissingScopeAddress
import io.provenance.classification.asset.verifier.config.VerifierEvent.EventIgnoredMissingScopeAttribute
import io.provenance.classification.asset.verifier.config.VerifierEvent.VerifyEventFailedOnboardingStatusStillPending
import io.provenance.classification.asset.verifier.config.VerifierEvent.VerifyEventSuccessful
import io.provenance.classification.asset.verifier.event.AssetClassificationEventHandler
import io.provenance.classification.asset.verifier.event.EventHandlerParameters
import io.provenance.classification.asset.verifier.provenance.ACContractEvent

object DefaultVerifyAssetEventHandler : AssetClassificationEventHandler {
    override val eventType: ACContractEvent = ACContractEvent.VERIFY_ASSET

    override suspend fun handleEvent(parameters: EventHandlerParameters) {
        val (event, acClient, _, _, eventChannel) = parameters
        val messagePrefix = "[VERIFY ASSET | Tx: ${event.sourceEvent.txHash} | Asset ${event.scopeAddress}"
        val scopeAddress = event.scopeAddress ?: run {
            eventChannel.send(
                EventIgnoredMissingScopeAddress(
                    event = event,
                    eventType = this.eventType,
                    message = "$messagePrefix Expected the verify asset event to include a scope address, but it was missing",
                )
            )
            return
        }
        val scopeAttribute = try {
            acClient.queryAssetScopeAttributeByScopeAddress(scopeAddress)
        } catch (t: Throwable) {
            eventChannel.send(
                EventIgnoredMissingScopeAttribute(
                    event = event,
                    eventType = this.eventType,
                    message = "$messagePrefix Intercepted verification did not point to a scope with a scope attribute",
                    t = t,
                )
            )
            return
        }
        if (scopeAttribute.onboardingStatus == AssetOnboardingStatus.PENDING) {
            eventChannel.send(
                VerifyEventFailedOnboardingStatusStillPending(
                    event = event,
                    scopeAttribute = scopeAttribute,
                    message = "$messagePrefix Verification did not successfully move onboarding status from pending",
                )
            )
            return
        }
        eventChannel.send(VerifyEventSuccessful(event, scopeAttribute))
    }
}
