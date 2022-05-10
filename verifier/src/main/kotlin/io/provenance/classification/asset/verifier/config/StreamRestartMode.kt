package io.provenance.classification.asset.verifier.config

sealed interface StreamRestartMode {
    class On(val restartDelayMs: Long = 5000L) : StreamRestartMode
    object Off : StreamRestartMode
}
