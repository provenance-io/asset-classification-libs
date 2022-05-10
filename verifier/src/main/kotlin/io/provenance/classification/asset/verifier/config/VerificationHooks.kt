package io.provenance.classification.asset.verifier.config

import cosmos.tx.v1beta1.ServiceOuterClass.BroadcastTxResponse
import io.provenance.classification.asset.client.domain.model.AccessRoute
import io.provenance.classification.asset.client.domain.model.AssetScopeAttribute
import io.provenance.classification.asset.verifier.client.AssetVerification
import io.provenance.classification.asset.verifier.provenance.AssetClassificationEvent
import io.provenance.eventstream.stream.clients.BlockData

/**
 * This interface is the driver for the verifier client.  It defines how each specific action should be executed,
 * and exposes numerous events to the client implementor.
 */
interface VerificationHooks<T> {
    /**
     * After verifying that an asset scope attribute has been correctly written to a scope, this function is called with
     * the scope onboarding requestor's data access routes.  The verifier implementation should use this function as
     * an entrypoint to retrieve the specified asset data from an external service, given these access routes.  The
     * requestor is not required to provide access routes, so the list of routes provided herein is not guaranteed to
     * be populated.
     */
    suspend fun retrieveAsset(
        event: AssetClassificationEvent,
        scopeAttribute: AssetScopeAttribute,
        accessRoutes: List<AccessRoute>,
    ): T?

    /**
     * After the asset is retrieved, this function should verify that it is in the proper shape.  Regardless of the
     * process used herein, the resulting value should be an AssetVerification data class.  This will signify to the
     * Asset Classification Smart Contract whether or not the asset should receive an AssetOnboardingStatus of APPROVED
     * or DENIED, based on the verification's `verifySuccess` parameter.
     */
    suspend fun verifyAsset(
        event: AssetClassificationEvent,
        scopeAttribute: AssetScopeAttribute,
        asset: T,
    ): AssetVerification

    /**
     * This is a catch-all function that emits each and every block that the verifier encounters.  The verifier will
     * filter down all events to only check "wasm" event types, so only those will be encountered herein.  However,
     * this function occurs before any sanity checks in the verifier codebase, so these blocks may or may not pertain
     * to the registered verifier account in the VerifierClientConfig.
     */
    suspend fun onNewBlock(block: BlockData) {}

    /**
     * When the client registers a new block height after iterating over encountered messages in its Flow, this function
     * will be called.  The events are processed in an ordered manner, so this function will only be called with larger
     * values than a value in a previous call.  This function is useful for registering previously-encountered block
     * heights with an external registrar for maintaining app state for app restarts.
     */
    suspend fun onNewBlockHeight(newHeight: Long) {}

    /**
     * When the event stream completes processing blocks (when it reaches the current block height), the flow will be
     * marked as completed and this function will execute.  If the stream completed exceptionally, the throwable herein
     * will be non-null, but it's very common for the stream to complete normally and the throwable to be null.
     */
    suspend fun onStreamComplete(t: Throwable?) {}

    /**
     * When the event stream encounters an exception (uncommon unless the blockchain is having issues), this function
     * will be called with the encountered exception.
     */
    suspend fun onStreamException(t: Throwable) {}

    /**
     * After the stream completes, if the stream has been configured to restart, this function will be called.  This
     * function can be assumed to run immediately before a new instance of the stream starts with a new block height.
     */
    suspend fun onStreamRestart(restartHeight: Long?) {}

    /**
     * If the verifier is configured to not restart, this function will be called after the stream completes.  It
     * signifies that no more events will be processed until the verifier is manually started again.
     */
    suspend fun onStreamExit(exitHeight: Long?) {}

    /**
     * When an event is encountered that does not conform to an event type that the verifier is intended to handle, this
     * function will be called with the event details.  This can happen for a number of reasons, including, but not
     * limited to: encountering a wasm event that is not related to the Asset Classification Smart Contract,
     * encountering an event from the Asset Classification Smart Contract that does not pertain to verifier
     * functionality, encountering a verifier-based event that targets a different verifier than the account registered
     * in the VerifierClientConfig, etc.
     */
    suspend fun onIgnoredEvent(event: AssetClassificationEvent, message: String) {}

    /**
     * When an event is encountered that should not proceed, this function will be called with the event details.  This
     * can happen if the asset onboarding status is not in an acceptable state for processing.  This generally indicates
     * that the event is a historical event that has been previously processed, or processed manually perhaps.
     */
    suspend fun onSkippedEvent(event: AssetClassificationEvent, message: String) {}

    /**
     * When an event is badly-formatted or necessary data is missing, this function will be called and the event will
     * be dropped from processing.  This generally indicates some form of error, and will include an exception if the
     * exception was the cause of the issue.  This can happen for a number of reasons, including, but not limited to:
     * an event with an unexpected event type is encountered, an event is missing its expected scope address, an event
     * is emitted without the accompanying scope attribute being written to the scope, downloading the target scope
     * asset data fails, asset verification throws an exception, etc.
     */
    suspend fun onBadContractExecutionSetup(
        event: AssetClassificationEvent,
        failureMessage: String,
        t: Throwable?
    ) {}

    /**
     * This function is called immediately before the verifier sends a message to the smart contract to mark the target
     * asset as verified.  This exposes the derived result of the verification before it is actually recorded.
     */
    suspend fun beforeVerifySend(
        event: AssetClassificationEvent,
        scopeAttribute: AssetScopeAttribute,
        verification: AssetVerification,
    ) {}

    /**
     * This function is called immediately after the verifier sends a message to the smart contract to mark the target
     * asset as verified.  This exposes the sent result, as well as the broadcast response from provenance.
     */
    suspend fun afterVerifySend(
        event: AssetClassificationEvent,
        scopeAttribute: AssetScopeAttribute,
        verification: AssetVerification,
        response: BroadcastTxResponse,
    ) {}

    /**
     * This function is called when the event emitted by calling the asset classification smart contract for
     * verification is detected, and the impact of a verification is detected.  This will be called regardless of if the
     * verification marked the asset as verified or denied.
     */
    suspend fun onVerifyCompletedSuccess(event: AssetClassificationEvent, scopeAttribute: AssetScopeAttribute) {}

    /**
     * This function is called when the event emitted by calling the asset classification smart contract for
     * verification is detected, and the verification appears to have had no impact (this can occur if the scope
     * attribute indicates that the asset is still in PENDING status after a successful verification).
     */
    suspend fun onVerifyCompletedFail(
        event: AssetClassificationEvent,
        scopeAttribute: AssetScopeAttribute,
        failureMessage: String,
    ) {}

    /**
     * This function is called any time that one of the user implementations of VerificationHooks throws an exception.
     * This function should not contain any code that can throw an exception.  If it does, the verifier is coded to
     * throw a final exception and stop listening for events, due to the potential for a failure to recover.
     */
    suspend fun onHookFailure(message: String, t: Throwable) {}

    suspend fun onReceiveChannelFailure(message: String, t: Throwable) {}
}
