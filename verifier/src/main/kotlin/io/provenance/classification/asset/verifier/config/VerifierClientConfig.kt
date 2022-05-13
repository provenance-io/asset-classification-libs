package io.provenance.classification.asset.verifier.config

import io.provenance.classification.asset.client.client.base.ACClient
import io.provenance.classification.asset.util.extensions.elvisAc
import io.provenance.classification.asset.util.wallet.ProvenanceAccountDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class VerifierClientConfig <A, T: VerificationHooks<A>> private constructor(
    val acClient: ACClient,
    val verifierAccount: ProvenanceAccountDetail,
    val coroutineScope: CoroutineScope,
    val hooks: T,
    val eventStreamNode: URI,
    val streamRestartMode: StreamRestartMode,
) {

    companion object {
        fun <A, T: VerificationHooks<A>> builder(
            acClient: ACClient,
            verifierAccount: ProvenanceAccountDetail,
            hooks: T,
        ): Builder<A, T> = Builder(acClient, verifierAccount, hooks)
    }

    class Builder <A, T: VerificationHooks<A>> internal constructor(
        private val acClient: ACClient,
        private val verifierAccount: ProvenanceAccountDetail,
        private val hooks: T,
    ) {
        private var eventStreamNode: URI? = null
        private var startingBlockHeight: Long? = null
        private var streamRestartMode: StreamRestartMode? = null
        private var coroutineScopeConfig: VerifierCoroutineScopeConfig? = null

        fun withEventStreamNode(node: URI) = apply { eventStreamNode = node }
        fun withStartingBlockHeight(height: Long) = apply { startingBlockHeight = height }
        fun withStreamRestartMode(mode: StreamRestartMode) = apply { streamRestartMode = mode }
        fun withCoroutineScope(config: VerifierCoroutineScopeConfig) = apply { coroutineScopeConfig = config }

        fun build(): VerifierClientConfig<A, T> = VerifierClientConfig(
            acClient = acClient,
            verifierAccount = verifierAccount,
            coroutineScope = coroutineScopeConfig.elvisAc { VerifierCoroutineScopeConfig.ScopeDefinition() }.toCoroutineScope(),
            hooks = hooks,
            eventStreamNode = eventStreamNode ?: URI("ws://localhost:26657"),
            streamRestartMode = streamRestartMode ?: StreamRestartMode.On(),
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
