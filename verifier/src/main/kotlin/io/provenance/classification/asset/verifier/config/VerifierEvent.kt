package io.provenance.classification.asset.verifier.config

import io.provenance.classification.asset.client.domain.model.AssetScopeAttribute
import io.provenance.classification.asset.verifier.client.AssetVerification
import io.provenance.classification.asset.verifier.provenance.AssetClassificationEvent
import io.provenance.eventstream.stream.clients.BlockData

sealed interface VerifierEvent {
    fun getEventTypeName(): String = this::class.simpleName!!

    /**
     * When the event stream encounters an exception (uncommon unless the blockchain is having issues), this function
     * will be called with the encountered exception.
     */
    data class StreamExceptionOccurred(val t: Throwable) : VerifierEvent

    /**
     * When the event stream completes processing blocks (when it reaches the current block height), the flow will be
     * marked as completed and this function will execute.  If the stream completed exceptionally, the throwable herein
     * will be non-null, but it's very common for the stream to complete normally and the throwable to be null.
     */
    data class StreamCompleted(val t: Throwable?) : VerifierEvent

    /**
     * This is a catch-all function that emits each and every block that the verifier encounters.  The verifier will
     * filter down all events to only check "wasm" event types, so only those will be encountered herein.  However,
     * this function occurs before any sanity checks in the verifier codebase, so these blocks may or may not pertain
     * to the registered verifier account in the VerifierClientConfig.
     */
    data class NewBlockReceived(val block: BlockData) : VerifierEvent

    /**
     * After the stream completes, if the stream has been configured to restart, this function will be called.  This
     * function can be assumed to run immediately before a new instance of the stream starts with a new block height.
     */
    data class StreamRestarted(val restartHeight: Long?): VerifierEvent

    /**
     * If the verifier is configured to not restart, this function will be called after the stream completes.  It
     * signifies that no more events will be processed until the verifier is manually started again.
     */
    data class StreamExited(val exitHeight: Long?): VerifierEvent

    /**
     * When the client registers a new block height after iterating over encountered messages in its Flow, this function
     * will be called.  The events are processed in an ordered manner, so this function will only be called with larger
     * values than a value in a previous call.  This function is useful for registering previously-encountered block
     * heights with an external registrar for maintaining app state for app restarts.
     */
    data class NewBlockHeightReceived(val newHeight: Long) : VerifierEvent

    /**
     * This will be extremely common - we cannot filter events upfront in the event stream code, so this check
     * throws away everything not emitted by the asset classification smart contract.  This can be safely ignored, but
     * is available for debugging.
     */
    data class EventIgnoredUnknownWasmEvent(val event: AssetClassificationEvent) : VerifierEvent {
        val message: String = "Unknown wasm event encountered"
    }

    /**
     * This will commonly happen - the contract emits events that don't target the verifier at all, but they'll
     * still pass through here.  This can be safely ignored, but is available for debugging.
     */
    data class EventIgnoredNoVerifierAddress(val event: AssetClassificationEvent): VerifierEvent {
        val message: String = "Event does not contain a verifier address"
    }

    /**
     * Only handle events that are relevant to the verifier.  The asset classification smart contract emits many more
     * events than the verifier client needs to handle, and this event is triggered when one of those events occur.
     * This can be safely ignored, but is available for debugging.
     */
    data class EventIgnoredUnhandledEventType(val event: AssetClassificationEvent) : VerifierEvent {
        val message: String = "Event type is not handled by the verifier"
    }

    /**
     * Only process verifications that are targeted at the registered verifier account.  Other verifiers can be chosen,
     * and those events will still pass through the verifier client.  This can be safely ignored, but is available for
     * debugging.
     */
    data class EventIgnoredDifferentVerifierAddress(
        val event: AssetClassificationEvent,
        private val registeredVerifierAddress: String,
    ): VerifierEvent {
        val message: String = "Event is for a different verifier [${event.verifierAddress}] than the registered verifier account [$registeredVerifierAddress]"
    }

    /**
     * This should never happen.  It indicates a change was made to the asset classification smart contract and/or the
     * underlying event processing that allowed an event that was not a handled type (ACContractEvent.HANDLED_EVENTS)
     * to pass into the event type when switch.  This event should be considered an error case and any external error
     * handling should be used to alert of this error.
     */
    data class EventIgnoredUnknownEvent(val event: AssetClassificationEvent) : VerifierEvent {
        val message: String = "After all event checks, an unexpected event was attempted for processing. Tx hash: [${event.sourceEvent.txHash}], event type: [${event.eventType}]"
    }

    /**
     * This event is emitted when an asset classification smart contract "onboard asset" event is detected, but it does
     * not contain the expected scope address attribute.  This is an error case and indicates bad smart contract code.
     */
    data class OnboardEventIgnoredMissingScopeAddress(val event: AssetClassificationEvent, val message: String) :
        VerifierEvent

    /**
     * This event is emitted when an asset classification smart contract "onboard asset" event is detected, but the scope
     * that was onboarded does not yet have an AssetScopeAttribute on it.  This should only ever occur due to a
     * blockchain error, or indicates a smart contract bug.
     */
    data class OnboardEventIgnoredMissingScopeAttribute(
        val event: AssetClassificationEvent,
        val message: String,
        val t: Throwable,
    ) : VerifierEvent

    /**
     * This event is emitted when the verifier client picks up an event that has already completed being verified.  This
     * should only happen when events are re-run by crawling old data in the event stream.  This can safely be ignored
     * unless interactions need to occur when encountering a completed event.
     */
    data class OnboardEventIgnoredPreviouslyProcessed(
        val event: AssetClassificationEvent,
        val scopeAttribute: AssetScopeAttribute,
        val message: String,
    ) : VerifierEvent

    /**
     * This event is emitted when the VerificationProcessor's defined method of fetching an asset from a scope attribute's
     * data fails and throws an exception.
     */
    data class OnboardEventFailedToRetrieveAsset(
        val event: AssetClassificationEvent,
        val scopeAttribute: AssetScopeAttribute,
        val t: Throwable,
    ) : VerifierEvent

    /**
     * This event is emitted when the VerificationProcessor's defined method of verifying asset data from a fetched
     * asset fails and throws an exception.
     */
    data class OnboardEventFailedToVerifyAsset(
        val event: AssetClassificationEvent,
        val scopeAttribute: AssetScopeAttribute,
        val t: Throwable,
    ) : VerifierEvent

    /**
     * This event always triggers before verification a verification transaction is sent to the smart contract for a
     * scope.  This can safely be ignored unless a pre verification hook is needed.
     */
    data class OnboardEventPreVerifySend(
        val event: AssetClassificationEvent,
        val scopeAttribute: AssetScopeAttribute,
        val verification: AssetVerification,
    ): VerifierEvent

    /**
     * This event is emitted when an asset classification smart contract "verify asset" event is detected, but it does
     * not contain the expected scope address attribute.  This is an error case and indicates bad smart contract code.
     */
    data class VerifyEventIgnoredMissingScopeAddress(
        val event: AssetClassificationEvent,
        val message: String,
    ) : VerifierEvent

    /**
     * This event is emitted when an asset classification smart contract "verify asset" event is detected, but the scope
     * that was onboarded does not have an AssetScopeAttribute on it.  This is an error case and indicates bad smart
     * contract code.
     */
    data class VerifyEventIgnoredMissingScopeAttribute(
        val event: AssetClassificationEvent,
        val message: String,
        val t: Throwable,
    ) : VerifierEvent

    /**
     * This event is emitted when an asset classification smart contract "verify asset" event is detected and its scope
     * attribute has been fetched.  This is an error case that indicates that although a verification event was run,
     * the resulting scope attribute is now in pending state.  This can theoretically happen if verification has been
     * denied and then onboarding has been once again invoked, but the likelihood of that occurrence versus bad data
     * is low.
     */
    data class VerifyEventFailedOnboardingStatusStillPending(
        val event: AssetClassificationEvent,
        val scopeAttribute: AssetScopeAttribute,
        val message: String,
    ) : VerifierEvent

    /**
     * This event is emitted when an asset classification smart contract "verify asset" event is processed and has
     * successfully moved the scope attribute's status to either approved or denied.  This event should be considered
     * the ideal ending process for all scopes that pass through the asset classification smart contract.
     */
    data class VerifyEventSuccessful(val event: AssetClassificationEvent, val scopeAttribute: AssetScopeAttribute) :
        VerifierEvent

    /**
     * This event is emitted when an exception occurs attempting to send an asset verification to the smart contract
     * with the ACClient instance.
     */
    data class VerifyAssetSendThrewException(
        val event: AssetClassificationEvent,
        val scopeAttribute: AssetScopeAttribute,
        val message: String,
        val t: Throwable,
    ) : VerifierEvent

    /**
     * This event is emitted when the verifier attempts to re-synchronize its sequence number with the registered
     * verifier account's values on the blockchain, and that process throws an exception.  This indicates that blockchain
     * communication is currently failing and that the verifier will be unable to successfully submit transactions.
     */
    data class VerifyAssetSendSyncSequenceNumberFailed(
        val event: AssetClassificationEvent,
        val scopeAttribute: AssetScopeAttribute,
        val message: String,
        val t: Throwable,
    ) : VerifierEvent

    /**
     * This event is emitted when the asset classification smart contract receives and processes a verification.  The
     * "verify asset" event processing must still collect the event and verify the status of the asset scope attribute
     * before considering the verification process to be complete for the target asset, but this event at least
     * signifies that the transaction to verify the asset was processed.
     */
    data class VerifyAssetSendSucceeded(
        val event: AssetClassificationEvent,
        val scopeAttribute: AssetScopeAttribute,
    ) : VerifierEvent

    /**
     * This event is emitted when attempting to verify the asset in a transaction fails.  The response includes the
     * information returned via the Provenance Blockchain about the problem.
     */
    data class VerifyAssetSendFailed(
        val event: AssetClassificationEvent,
        val scopeAttribute: AssetScopeAttribute,
        val responseCode: Int,
        val rawLog: String,
    ) : VerifierEvent

    /**
     * This event is emitted when the coroutine channel used to process verifier events sequentially throws an exception.
     * Any number of coroutine/async things can occur that can cause this error when the environment is having difficulties
     * and it should be watched in a robust setup.
     */
    data class VerifyEventChannelThrewException(val t: Throwable) : VerifierEvent

    /**
     * This event is emitted when an event processor is registered in the VerifierClientConfig that can throw an exception.
     * The VerifierClient will catch any exception thrown by custom processor code and emit this error with the
     * exception thrown contained as `t`.
     */
    data class CustomEventProcessorFailed(val t: Throwable) : VerifierEvent
}

sealed interface VerifierEventType<E: VerifierEvent> {
    fun getEventTypeName(): String = this::class.simpleName!!

    object StreamExceptionOccurred : VerifierEventType<VerifierEvent.StreamExceptionOccurred>
    object StreamCompleted : VerifierEventType<VerifierEvent.StreamCompleted>
    object NewBlockReceived : VerifierEventType<VerifierEvent.NewBlockReceived>
    object StreamRestarted : VerifierEventType<VerifierEvent.StreamRestarted>
    object StreamExited : VerifierEventType<VerifierEvent.StreamExited>
    object NewBlockHeightReceived: VerifierEventType<VerifierEvent.NewBlockHeightReceived>
    object EventIgnoredUnknownWasmEvent : VerifierEventType<VerifierEvent.EventIgnoredUnknownWasmEvent>
    object EventIgnoredNoVerifierAddress : VerifierEventType<VerifierEvent.EventIgnoredNoVerifierAddress>
    object EventIgnoredUnhandledEventType : VerifierEventType<VerifierEvent.EventIgnoredUnhandledEventType>
    object EventIgnoredDifferentVerifierAddress : VerifierEventType<VerifierEvent.EventIgnoredDifferentVerifierAddress>
    object EventIgnoredUnknownEvent : VerifierEventType<VerifierEvent.EventIgnoredUnknownEvent>
    object OnboardEventIgnoredMissingScopeAddress : VerifierEventType<VerifierEvent.OnboardEventIgnoredMissingScopeAddress>
    object OnboardEventIgnoredMissingScopeAttribute : VerifierEventType<VerifierEvent.OnboardEventIgnoredMissingScopeAttribute>
    object OnboardEventIgnoredPreviouslyProcessed : VerifierEventType<VerifierEvent.OnboardEventIgnoredPreviouslyProcessed>
    object OnboardEventFailedToRetrieveAsset : VerifierEventType<VerifierEvent.OnboardEventFailedToRetrieveAsset>
    object OnboardEventFailedToVerifyAsset : VerifierEventType<VerifierEvent.OnboardEventFailedToVerifyAsset>
    object OnboardEventPreVerifySend : VerifierEventType<VerifierEvent.OnboardEventPreVerifySend>
    object VerifyEventIgnoredMissingScopeAddress : VerifierEventType<VerifierEvent.VerifyEventIgnoredMissingScopeAddress>
    object VerifyEventIgnoredMissingScopeAttribute : VerifierEventType<VerifierEvent.VerifyEventIgnoredMissingScopeAttribute>
    object VerifyEventFailedOnboardingStatusStillPending : VerifierEventType<VerifierEvent.VerifyEventFailedOnboardingStatusStillPending>
    object VerifyEventSuccessful : VerifierEventType<VerifierEvent.VerifyEventSuccessful>
    object VerifyAssetSendThrewException : VerifierEventType<VerifierEvent.VerifyAssetSendThrewException>
    object VerifyAssetSendSyncSequenceNumberFailed : VerifierEventType<VerifierEvent.VerifyAssetSendSyncSequenceNumberFailed>
    object VerifyAssetSendSucceeded : VerifierEventType<VerifierEvent.VerifyAssetSendSucceeded>
    object VerifyAssetSendFailed : VerifierEventType<VerifierEvent.VerifyAssetSendFailed>
    object VerifyEventChannelThrewException : VerifierEventType<VerifierEvent.VerifyEventChannelThrewException>
    object CustomEventProcessorFailed : VerifierEventType<VerifierEvent.CustomEventProcessorFailed>
}
