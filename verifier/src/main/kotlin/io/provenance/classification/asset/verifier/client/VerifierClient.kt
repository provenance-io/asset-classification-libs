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
import io.provenance.classification.asset.verifier.config.VerificationHooks
import io.provenance.classification.asset.verifier.config.VerifierClientConfig
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

class VerifierClient<A, T: VerificationHooks<A>>(private val config: VerifierClientConfig<A, T>) {
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
            .catch { e -> safe("onStreamException") { onStreamException(e) } }
            .onCompletion { t -> safe("onStreamComplete") { onStreamComplete(t) } }
            .onEach { block ->
                // Record each block intercepted
                safe("onNewBlock") { onNewBlock(block) }
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
            safe("onStreamException") { onStreamException(e) }
            // Escape the loop entirely if the adapter fails to shut down - there should never be two adapters running
            // in tandem via this client
            return
        }
        when (config.streamRestartMode) {
            is StreamRestartMode.On -> {
                if (config.streamRestartMode.restartDelayMs > 0) {
                    delay(config.streamRestartMode.restartDelayMs)
                }
                safe("onStreamRestart") { onStreamRestart(latestBlock) }
                // Recurse into a new event stream if the stream needs to restart
                verifyLoop(latestBlock)
            }
            is StreamRestartMode.Off -> {
                safe("onStreamExit") { onStreamExit(latestBlock) }
            }
        }
    }

    private suspend fun trackBlockHeight(
        latestHeight: Long?,
        newHeight: Long
    ): Long = if (latestHeight == null || latestHeight < newHeight) {
        safe("onNewBlockHeight") { onNewBlockHeight(newHeight) }
        newHeight
    } else {
        latestHeight
    }

    private suspend fun handleEvent(event: AssetClassificationEvent) {
        // This will be extremely common - we cannot filter events upfront in the event stream code, so this check
        // throws away everything not emitted by the asset classification smart contract
        if (event.eventType == null) {
            safe("onIgnoredEvent") { onIgnoredEvent(event, "Unknown wasm event encountered") }
            return
        }
        // This will commonly happen - the contract emits events that don't target the verifier at all, but they'll
        // still pass through here
        if (event.verifierAddress == null) {
            safe("onIgnoredEvent") { onIgnoredEvent(event, "Event does not contain a verifier address") }
            return
        }
        // Only handle events that are relevant to the verifier
        if (event.eventType !in ACContractEvent.HANDLED_EVENTS) {
            safe("onIgnoredEvent") { onIgnoredEvent(event, "Event type is not handled by the verifier") }
            return
        }
        // Only process verifications that are targeted at the registered verifier account
        if (event.verifierAddress != config.verifierAccount.bech32Address) {
            safe("onIgnoredEvent") { onIgnoredEvent(event, "Event is for a different verifier than the registered one") }
            return
        }
        when (event.eventType) {
            ACContractEvent.ONBOARD_ASSET -> handleOnboardAsset(event)
            ACContractEvent.VERIFY_ASSET -> handleVerifyAsset(event)
            else -> safe("onBadContractExecutionSetup") { onBadContractExecutionSetup(event, "After all event checks, an unexpected event was attempted for processing. Tx hash: [${event.sourceEvent.txHash}], event type: [${event.eventType}]", null) }
        }
    }

    private suspend fun handleOnboardAsset(event: AssetClassificationEvent) {
        val messagePrefix = "[ONBOARD_ASSET | Tx: ${event.sourceEvent.txHash} | Asset: ${event.scopeAddress}]:"
        val scopeAddress = event.scopeAddress ?: run {
            safe("onBadContractExecutionSetup") { onBadContractExecutionSetup(event, "$messagePrefix Expected the onboard asset event to include a scope address, but it was missing", null) }
            return
        }
        val scopeAttribute = try {
            config.acClient.queryAssetScopeAttributeByScopeAddress(scopeAddress)
        } catch (t: Throwable) {
            safe("onBadContractExecutionSetup") { onBadContractExecutionSetup(event, "$messagePrefix Intercepted onboard asset did not point to a scope with a scope attribute", t) }
            return
        }

        if (scopeAttribute.onboardingStatus != AssetOnboardingStatus.PENDING) {
            safe("onSkippedEvent") { onSkippedEvent(event, "$messagePrefix Scope attribute indicates an onboarding status of [${scopeAttribute.onboardingStatus}], which is not actionable.  Has verification: [Verified = ${scopeAttribute.latestVerificationResult?.success} | Message = ${scopeAttribute.latestVerificationResult?.message}]") }
            return
        }

        val targetRoutes = scopeAttribute.accessDefinitions.singleOrNull { it.definitionType == AccessDefinitionType.REQUESTOR }
            ?.accessRoutes
            // Provide an empty list if no access routes were defined by the requestor
            ?: emptyList()

        val asset = safe("retrieveAsset") { retrieveAsset(event, scopeAttribute, targetRoutes) } ?: return

        val verification = safe("verifyAsset") { verifyAsset(event, scopeAttribute, asset) } ?: return

        safe("beforeVerifySend") { beforeVerifySend(event, scopeAttribute, verification) }

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
            safe("onBadContractExecutionSetup") { onBadContractExecutionSetup(event, "$messagePrefix Expected the verify asset event to include a scope address, but it was missing", null) }
            return
        }
        val scopeAttribute = try {
            config.acClient.queryAssetScopeAttributeByScopeAddress(scopeAddress)
        } catch (t: Throwable) {
            safe("onBadContractExecutionSetup") { onBadContractExecutionSetup(event, "$messagePrefix Intercepted verification did not point to a scope with a scope attribute", t) }
            return
        }
        if (scopeAttribute.onboardingStatus == AssetOnboardingStatus.PENDING) {
            safe("onVerifyCompletedFail") { onVerifyCompletedFail(event, scopeAttribute, "$messagePrefix Verification did not successfully move onboarding status from pending") }
            return
        }
        safe("onVerifyCompletedSuccess") { onVerifyCompletedSuccess(event, scopeAttribute) }
    }

    private suspend fun <R> safe(hookName: String, hookFn: suspend VerificationHooks<A>.() -> R): R? {
        return try {
            config.hooks.hookFn()
        } catch (t: Throwable) {
            try {
                config.hooks.onHookFailure("Failed to execute hook: [$hookName]", t)
                null
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
                        safe("onBadContractExecutionSetup") {
                            onBadContractExecutionSetup(
                                message.event,
                                "${message.messagePrefix} Sending verification to smart contract failed",
                                t
                            )
                        }
                        try {
                            tracking.reset()
                        } catch (t: Throwable) {
                            safe("onBadContractExecutionSetup") {
                                onBadContractExecutionSetup(
                                    message.event,
                                    "${message.messagePrefix} Failed to reset account data after transaction. This may require an app restart",
                                    t
                                )
                            }
                        }
                        null
                    }
                    response?.also {
                        safe("afterVerifySend") {
                            afterVerifySend(
                                message.event,
                                message.scopeAttribute,
                                message.verification,
                                response
                            )
                        }
                        if (response.txResponse.code != 0) {
                            safe("onVerifyCompletedFail") {
                                onVerifyCompletedFail(
                                    message.event,
                                    message.scopeAttribute,
                                    "${message.messagePrefix}: Sending event resulted in status code [${response.txResponse.code}] and had message: ${response.txResponse.rawLog}"
                                )
                            }
                        }
                    }
                } catch (t: Throwable) {
                    safe("onReceiveChannelFailure") {
                        onReceiveChannelFailure(
                            "An exception occurred when processing from receive channel for verification",
                            t
                        )
                    }
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
