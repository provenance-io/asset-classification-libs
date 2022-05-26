package io.provenance.classification.asset.verifier.client

import cosmos.auth.v1beta1.Auth.BaseAccount
import cosmos.tx.v1beta1.ServiceOuterClass.BroadcastMode
import io.provenance.classification.asset.client.client.base.BroadcastOptions
import io.provenance.classification.asset.client.domain.execute.VerifyAssetExecute
import io.provenance.classification.asset.client.domain.model.AccessDefinitionType
import io.provenance.classification.asset.client.domain.model.AssetOnboardingStatus
import io.provenance.classification.asset.client.domain.model.AssetScopeAttribute
import io.provenance.classification.asset.util.extensions.alsoIfAc
import io.provenance.classification.asset.util.extensions.toProvenanceTxEventsAc
import io.provenance.classification.asset.util.wallet.AccountSigner
import io.provenance.classification.asset.verifier.config.StreamRestartMode
import io.provenance.classification.asset.verifier.config.VerificationProcessor
import io.provenance.classification.asset.verifier.config.VerifierClientConfig
import io.provenance.classification.asset.verifier.config.VerifierEvent
import io.provenance.classification.asset.verifier.config.VerifierEvent.EventIgnoredDifferentVerifierAddress
import io.provenance.classification.asset.verifier.config.VerifierEvent.EventIgnoredNoVerifierAddress
import io.provenance.classification.asset.verifier.config.VerifierEvent.EventIgnoredUnhandledEventType
import io.provenance.classification.asset.verifier.config.VerifierEvent.EventIgnoredUnknownEvent
import io.provenance.classification.asset.verifier.config.VerifierEvent.EventIgnoredUnknownWasmEvent
import io.provenance.classification.asset.verifier.config.VerifierEvent.NewBlockHeightReceived
import io.provenance.classification.asset.verifier.config.VerifierEvent.NewBlockReceived
import io.provenance.classification.asset.verifier.config.VerifierEvent.OnboardEventFailedToRetrieveAsset
import io.provenance.classification.asset.verifier.config.VerifierEvent.OnboardEventFailedToVerifyAsset
import io.provenance.classification.asset.verifier.config.VerifierEvent.OnboardEventIgnoredMissingScopeAddress
import io.provenance.classification.asset.verifier.config.VerifierEvent.OnboardEventIgnoredMissingScopeAttribute
import io.provenance.classification.asset.verifier.config.VerifierEvent.OnboardEventIgnoredPreviouslyProcessed
import io.provenance.classification.asset.verifier.config.VerifierEvent.OnboardEventPreVerifySend
import io.provenance.classification.asset.verifier.config.VerifierEvent.StreamCompleted
import io.provenance.classification.asset.verifier.config.VerifierEvent.StreamExceptionOccurred
import io.provenance.classification.asset.verifier.config.VerifierEvent.StreamExited
import io.provenance.classification.asset.verifier.config.VerifierEvent.StreamRestarted
import io.provenance.classification.asset.verifier.config.VerifierEvent.VerifyAssetSendFailed
import io.provenance.classification.asset.verifier.config.VerifierEvent.VerifyAssetSendSucceeded
import io.provenance.classification.asset.verifier.config.VerifierEvent.VerifyAssetSendSyncSequenceNumberFailed
import io.provenance.classification.asset.verifier.config.VerifierEvent.VerifyAssetSendThrewException
import io.provenance.classification.asset.verifier.config.VerifierEvent.VerifyEventChannelThrewException
import io.provenance.classification.asset.verifier.config.VerifierEvent.VerifyEventFailedOnboardingStatusStillPending
import io.provenance.classification.asset.verifier.config.VerifierEvent.VerifyEventIgnoredMissingScopeAddress
import io.provenance.classification.asset.verifier.config.VerifierEvent.VerifyEventIgnoredMissingScopeAttribute
import io.provenance.classification.asset.verifier.config.VerifierEvent.VerifyEventSuccessful
import io.provenance.classification.asset.verifier.config.VerifierEventType
import io.provenance.classification.asset.verifier.provenance.AssetClassificationEvent
import io.provenance.classification.asset.verifier.provenance.ACContractEvent
import io.provenance.client.grpc.PbClient
import io.provenance.client.protobuf.extensions.getBaseAccount
import io.provenance.client.protobuf.extensions.getTx
import io.provenance.eventstream.decoder.moshiDecoderAdapter
import io.provenance.eventstream.net.okHttpNetAdapter
import io.provenance.eventstream.stream.flows.blockDataFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class VerifierClient(private val config: VerifierClientConfig) {
    // Cast the provided processor to T of Any to make creation and usage easier on the consumer of this library
    @Suppress("UNCHECKED_CAST")
    private val verifyProcessor: VerificationProcessor<Any> = config.verificationProcessor as VerificationProcessor<Any>
    private val signer = AccountSigner.fromAccountDetail(config.verifierAccount)
    private val decoderAdapter = moshiDecoderAdapter()
    private var jobWatcher = VerifierJobWatcher()
    private val verificationChannel = Channel<VerificationMessage>(capacity = Channel.BUFFERED)
    private val tracking: AccountTrackingDetail =
        AccountTrackingDetail.lookup(config.acClient.pbClient, config.verifierAccount.bech32Address)

    fun manualVerifyHash(txHash: String) {
        val tx = config.acClient.pbClient.cosmosService.getTx(txHash)
        val events = AssetClassificationEvent.fromVerifierTxEvents(
            sourceTx = tx,
            txEvents = tx.txResponse.toProvenanceTxEventsAc(),
        )
        config.coroutineScope.launch {
            events.forEach { acEvent -> handleEvent(acEvent) }
        }
    }

    fun startVerifying(startingBlockHeight: Long): Job = config
        .coroutineScope
        .launch { verifyLoop(startingBlockHeight) }
        .also { jobWatcher.verificationJob = it }
        .also { startVerificationReceiver() }

    fun stopVerifying() {
        if (jobWatcher.verificationJob == null) {
            throw IllegalStateException("Cannot stop the verifier. It is not running")
        }
        jobWatcher.verificationJob?.cancel(CancellationException("Manual verification cancellation requested"))
        tracking.reset()
    }

    fun restartVerifier(startingBlockHeight: Long): Job {
        stopVerifying()
        return startVerifying(startingBlockHeight)
    }

    private suspend fun verifyLoop(startingBlockHeight: Long?) {
        val netAdapter = okHttpNetAdapter(node = config.eventStreamNode.toString())
        val currentHeight = netAdapter.rpcAdapter.getCurrentHeight()
        var latestBlock = startingBlockHeight?.takeIf { start -> start > 0 && currentHeight?.let { it >= start } != false }
        blockDataFlow(netAdapter, decoderAdapter, from = latestBlock)
            .catch { e -> StreamExceptionOccurred(e).emit() }
            .onCompletion { t -> StreamCompleted(t).emit() }
            .onEach { block ->
                // Record each block intercepted
                NewBlockReceived(block).emit()
                // Track new block height
                latestBlock = trackBlockHeight(latestBlock, block.height)
            }
            // Map all captured block data to AssetClassificationEvents, which will remove all non-wasm events
            // encountered
            .map(AssetClassificationEvent::fromBlockData)
            .collect { events ->
                events.forEach { event -> handleEvent(event) }
            }
        // The event stream flow should execute infinitely unless some error occurs, so this line will only be reached
        // on connection failures or other problems.
        try {
            // Attempt to shut down the net adapter before restarting or exiting the stream
            netAdapter.shutdown()
        } catch (e: Exception) {
            // Emit the exception encountered on net adapter shutdown and exit the stream entirely
            StreamExceptionOccurred(e).emit()
            // Escape the loop entirely if the adapter fails to shut down - there should never be two adapters running
            // in tandem via this client
            return
        }
        when (config.streamRestartMode) {
            is StreamRestartMode.On -> {
                if (config.streamRestartMode.restartDelayMs > 0) {
                    delay(config.streamRestartMode.restartDelayMs)
                }
                StreamRestarted(latestBlock).emit()
                // Recurse into a new event stream if the stream needs to restart
                verifyLoop(latestBlock)
            }
            is StreamRestartMode.Off -> {
                StreamExited(latestBlock).emit()
            }
        }
    }

    private suspend fun trackBlockHeight(
        latestHeight: Long?,
        newHeight: Long
    ): Long = if (latestHeight == null || latestHeight < newHeight) {
        NewBlockHeightReceived(newHeight).emit()
        newHeight
    } else {
        latestHeight
    }

    private suspend fun handleEvent(event: AssetClassificationEvent) {
        // This will be extremely common - we cannot filter events upfront in the event stream code, so this check
        // throws away everything not emitted by the asset classification smart contract
        if (event.eventType == null) {
            EventIgnoredUnknownWasmEvent(event).emit()
            return
        }
        // This will commonly happen - the contract emits events that don't target the verifier at all, but they'll
        // still pass through here
        if (event.verifierAddress == null) {
            EventIgnoredNoVerifierAddress(event).emit()
            return
        }
        // Only handle events that are relevant to the verifier
        if (event.eventType !in ACContractEvent.HANDLED_EVENTS) {
            EventIgnoredUnhandledEventType(event).emit()
            return
        }
        // Only process verifications that are targeted at the registered verifier account
        if (event.verifierAddress != config.verifierAccount.bech32Address) {
            EventIgnoredDifferentVerifierAddress(event, config.verifierAccount.bech32Address).emit()
            return
        }
        when (event.eventType) {
            ACContractEvent.ONBOARD_ASSET -> handleOnboardAsset(event)
            ACContractEvent.VERIFY_ASSET -> handleVerifyAsset(event)
            else -> EventIgnoredUnknownEvent(event).emit()
        }
    }

    private suspend fun handleOnboardAsset(event: AssetClassificationEvent) {
        val messagePrefix = "[ONBOARD_ASSET | Tx: ${event.sourceEvent.txHash} | Asset: ${event.scopeAddress}]:"
        val scopeAddress = event.scopeAddress ?: run {
            OnboardEventIgnoredMissingScopeAddress(
                event = event,
                message = "$messagePrefix Expected the onboard asset event to include a scope address, but it was missing",
            ).emit()
            return
        }
        val scopeAttribute = try {
            config.acClient.queryAssetScopeAttributeByScopeAddress(scopeAddress)
        } catch (t: Throwable) {
            OnboardEventIgnoredMissingScopeAttribute(
                event = event,
                message = "$messagePrefix Intercepted onboard asset did not point to a scope with a scope attribute",
                t = t,
            ).emit()
            return
        }

        if (scopeAttribute.onboardingStatus != AssetOnboardingStatus.PENDING) {
            OnboardEventIgnoredPreviouslyProcessed(
                event = event,
                scopeAttribute = scopeAttribute,
                message = "$messagePrefix Scope attribute indicates an onboarding status of [${scopeAttribute.onboardingStatus}], which is not actionable.  Has verification: [Verified = ${scopeAttribute.latestVerificationResult?.success} | Message = ${scopeAttribute.latestVerificationResult?.message}]",
            ).emit()
            return
        }

        val targetRoutes = scopeAttribute.accessDefinitions.singleOrNull { it.definitionType == AccessDefinitionType.REQUESTOR }
            ?.accessRoutes
            // Provide an empty list if no access routes were defined by the requestor
            ?: emptyList()

        val asset = try {
            verifyProcessor.retrieveAsset(event, scopeAttribute, targetRoutes)
        } catch(t: Throwable) {
            OnboardEventFailedToRetrieveAsset(
                event = event,
                scopeAttribute = scopeAttribute,
                t = t,
            ).emit()
            null
        } ?: return

        val verification = try {
            verifyProcessor.verifyAsset(event, scopeAttribute, asset)
        } catch (t: Throwable) {
            OnboardEventFailedToVerifyAsset(
                event = event,
                scopeAttribute = scopeAttribute,
                t = t,
            ).emit()
            null
        } ?: return

        OnboardEventPreVerifySend(event, scopeAttribute).emit()

        verificationChannel.send(
            VerificationMessage(
            messagePrefix = messagePrefix,
            event = event,
            scopeAttribute = scopeAttribute,
            verification = verification,
        )
        )
    }

    private suspend fun handleVerifyAsset(event: AssetClassificationEvent) {
        val messagePrefix = "[VERIFY ASSET | Tx: ${event.sourceEvent.txHash} | Asset ${event.scopeAddress}"
        val scopeAddress = event.scopeAddress ?: run {
            VerifyEventIgnoredMissingScopeAddress(
                event = event,
                message = "$messagePrefix Expected the verify asset event to include a scope address, but it was missing",
            ).emit()
            return
        }
        val scopeAttribute = try {
            config.acClient.queryAssetScopeAttributeByScopeAddress(scopeAddress)
        } catch (t: Throwable) {
            VerifyEventIgnoredMissingScopeAttribute(
                event = event,
                message = "$messagePrefix Intercepted verification did not point to a scope with a scope attribute",
                t = t,
            ).emit()
            return
        }
        if (scopeAttribute.onboardingStatus == AssetOnboardingStatus.PENDING) {
            VerifyEventFailedOnboardingStatusStillPending(
                event = event,
                scopeAttribute = scopeAttribute,
                message = "$messagePrefix Verification did not successfully move onboarding status from pending",
            ).emit()
            return
        }
        VerifyEventSuccessful(event, scopeAttribute).emit()
    }

    private suspend fun <E: VerifierEvent> E.emit() {
        try {
            config.eventProcessors[this.getEventTypeName()]?.invoke(this)
        } catch (t: Throwable) {
            try {
                config.eventProcessors[VerifierEventType.CustomEventProcessorFailed.getEventTypeName()]?.invoke(
                    VerifierEvent.CustomEventProcessorFailed(t)
                )
            } catch (t: Throwable) {
                // RIP worst case scenario - kill the stream with an exception
                throw IllegalStateException("Internal failure hook is misconfigured and threw an exception", t)
            }
        }
    }

    private fun startVerificationReceiver() {
        // Only one receiver channel needs to run at a time
        if (jobWatcher.receiverJob != null) {
            return
        }
        config.coroutineScope.launch {
            // A for-loop over a channel will infinitely iterate
            for (message in verificationChannel) {
                try {
                    val response = try {
                        config.acClient.verifyAsset(
                            execute = VerifyAssetExecute.withScopeAddress(
                                scopeAddress = message.scopeAttribute.scopeAddress,
                                success = message.verification.verifySuccess,
                                message = message.verification.message,
                            ),
                            signer = signer,
                            options = BroadcastOptions(
                                broadcastMode = BroadcastMode.BROADCAST_MODE_SYNC,
                                baseAccount = tracking.sequencedAccount(incrementAfterGet = true),
                            )
                        )
                    } catch (t: Throwable) {
                        VerifyAssetSendThrewException(
                            event = message.event,
                            scopeAttribute = message.scopeAttribute,
                            message = "${message.messagePrefix} Sending verification to smart contract failed",
                            t = t,
                        ).emit()
                        try {
                            tracking.reset()
                        } catch (t: Throwable) {
                            VerifyAssetSendSyncSequenceNumberFailed(
                                event = message.event,
                                scopeAttribute = message.scopeAttribute,
                                message = "${message.messagePrefix} Failed to reset account data after transaction. This may require an app restart",
                                t = t,
                            ).emit()
                        }
                        null
                    }
                    response?.also {
                        if (response.txResponse.code == 0) {
                            VerifyAssetSendSucceeded(
                                event = message.event,
                                scopeAttribute = message.scopeAttribute,
                            ).emit()
                        } else {
                            VerifyAssetSendFailed(
                                event = message.event,
                                scopeAttribute = message.scopeAttribute,
                                responseCode = response.txResponse.code,
                                rawLog = response.txResponse.rawLog,
                            )
                        }
                    }
                } catch (t: Throwable) {
                    VerifyEventChannelThrewException(t).emit()
                }
            }
        }.also { jobWatcher.receiverJob = it }
    }
}

data class AssetVerification(
    val message: String,
    val verifySuccess: Boolean,
)

private data class VerifierJobWatcher(
    var verificationJob: Job? = null,
    var receiverJob: Job? = null,
) {
    fun reset() {
        verificationJob = null
        receiverJob = null
    }
}

private data class VerificationMessage(
    val messagePrefix: String,
    val event: AssetClassificationEvent,
    val scopeAttribute: AssetScopeAttribute,
    val verification: AssetVerification,
)

private data class AccountTrackingDetail(
    val pbClient: PbClient,
    private var account: BaseAccount,
    private val sequenceNumber: AtomicLong,
) {
    companion object {
        fun lookup(pbClient: PbClient, address: String): AccountTrackingDetail = pbClient.authClient.getBaseAccount(address).let { account ->
            AccountTrackingDetail(
                pbClient = pbClient,
                account = account,
                sequenceNumber = account.sequence.let(::AtomicLong),
            )
        }
    }
    fun sequencedAccount(incrementAfterGet: Boolean = false): BaseAccount = account
        .toBuilder()
        .setSequence(sequenceNumber.get())
        .build()
        .alsoIfAc(incrementAfterGet) { addTransaction() }
    fun reset() {
        account = pbClient.authClient.getBaseAccount(account.address).also { sequenceNumber.set(it.sequence) }
    }

    fun addTransaction() {
        sequenceNumber.incrementAndGet()
    }
}
