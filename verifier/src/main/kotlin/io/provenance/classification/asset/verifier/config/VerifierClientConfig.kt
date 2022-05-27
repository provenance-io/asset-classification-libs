package io.provenance.classification.asset.verifier.config

import io.provenance.classification.asset.client.client.base.ACClient
import io.provenance.classification.asset.util.extensions.elvisAc
import io.provenance.classification.asset.util.wallet.ProvenanceAccountDetail
import io.provenance.classification.asset.verifier.event.AssetClassificationEventDelegator
import io.provenance.classification.asset.verifier.event.AssetClassificationEventDelegator.AssetClassificationEventDelegatorBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class VerifierClientConfig private constructor(
    val acClient: ACClient,
    val verifierAccount: ProvenanceAccountDetail,
    val coroutineScope: CoroutineScope,
    val verificationProcessor: VerificationProcessor<*>,
    val eventStreamNode: URI,
    val streamRestartMode: StreamRestartMode,
    val eventDelegator: AssetClassificationEventDelegator,
    val eventProcessors: Map<String, suspend (VerifierEvent) -> Unit>,
) {

    companion object {
        fun builder(
            acClient: ACClient,
            verifierAccount: ProvenanceAccountDetail,
            verificationProcessor: VerificationProcessor<*>,
        ): Builder = Builder(acClient, verifierAccount, verificationProcessor)
    }

    class Builder internal constructor(
        private val acClient: ACClient,
        private val verifierAccount: ProvenanceAccountDetail,
        private val verificationProcessor: VerificationProcessor<*>,
    ) {
        private var eventStreamNode: URI? = null
        private var startingBlockHeight: Long? = null
        private var streamRestartMode: StreamRestartMode? = null
        private var coroutineScopeConfig: VerifierCoroutineScopeConfig? = null
        private var eventDelegator: AssetClassificationEventDelegator? = null
        private val eventProcessors: MutableMap<String, suspend (VerifierEvent) -> Unit> = mutableMapOf()

        fun withEventStreamNode(node: URI) = apply { eventStreamNode = node }

        fun withStartingBlockHeight(height: Long) = apply { startingBlockHeight = height }

        fun withStreamRestartMode(mode: StreamRestartMode) = apply { streamRestartMode = mode }

        fun withCoroutineScope(config: VerifierCoroutineScopeConfig) = apply { coroutineScopeConfig = config }

        fun withEventDelegator(delegator: AssetClassificationEventDelegator) = apply { eventDelegator = delegator }

        fun buildEventDelegator(
            builderFn: (AssetClassificationEventDelegatorBuilder) -> AssetClassificationEventDelegatorBuilder,
        ) = apply {
            AssetClassificationEventDelegator.builder().let(builderFn).build()
        }

        // All instances of E must be able to cast to VerifierEvent due to type constraints on VerifierEventType, so this
        // cast will always succeed, despite compiler anger
        @Suppress("UNCHECKED_CAST")
        fun <E: VerifierEvent> addEventProcessor(eventType: VerifierEventType<E>, processor: suspend (E) -> Unit) = apply {
            eventProcessors += eventType.getEventTypeName() to processor as suspend (VerifierEvent) -> Unit
        }

        fun build(): VerifierClientConfig = VerifierClientConfig(
            acClient = acClient,
            verifierAccount = verifierAccount,
            coroutineScope = coroutineScopeConfig.elvisAc { VerifierCoroutineScopeConfig.ScopeDefinition() }.toCoroutineScope(),
            verificationProcessor = verificationProcessor,
            eventStreamNode = eventStreamNode ?: URI("ws://localhost:26657"),
            streamRestartMode = streamRestartMode ?: StreamRestartMode.On(),
            eventDelegator = eventDelegator ?: AssetClassificationEventDelegator.default(),
            eventProcessors = eventProcessors,
        )
    }
}

sealed interface VerifierCoroutineScopeConfig {
    fun toCoroutineScope(): CoroutineScope

    class ProvidedScope(private val scope: CoroutineScope) : VerifierCoroutineScopeConfig {
        override fun toCoroutineScope(): CoroutineScope = scope
    }

    class ScopeDefinition(
        private val scopeName: String = "verifier-scope",
        private val threadCount: Int = 10,
    ) : VerifierCoroutineScopeConfig {
        override fun toCoroutineScope(): CoroutineScope = Executors.newFixedThreadPool(threadCount, NamedThreadFactory(scopeName))
            .asCoroutineDispatcher()
            .plus(SupervisorJob())
            .let(::CoroutineScope)
    }
}

private class NamedThreadFactory(private val prefix: String) : ThreadFactory {
    private val sequence = AtomicInteger(1)

    override fun newThread(r: Runnable?): Thread {
        val thread = Thread(r)
        val seq = sequence.getAndIncrement()
        thread.name = "$prefix${if (seq > 1) "-$seq" else ""}"
        thread.isDaemon = true
        return thread
    }
}
