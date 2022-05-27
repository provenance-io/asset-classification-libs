package io.provenance.classification.asset.verifier.event

import io.provenance.classification.asset.verifier.config.VerifierEvent.EventIgnoredUnknownEvent
import io.provenance.classification.asset.verifier.event.defaults.DefaultOnboardEventHandler
import io.provenance.classification.asset.verifier.event.defaults.DefaultVerifyAssetEventHandler
import io.provenance.classification.asset.verifier.provenance.ACContractEvent

class AssetClassificationEventDelegator internal constructor(
    private val eventHandlers: Map<ACContractEvent, AssetClassificationEventHandler>
) {
    companion object {
        fun builder(): AssetClassificationEventDelegatorBuilder = AssetClassificationEventDelegatorBuilder()

        fun default(): AssetClassificationEventDelegator = builder()
            .registerEventHandler(DefaultOnboardEventHandler)
            .registerEventHandler(DefaultVerifyAssetEventHandler)
            .build()
    }

    class AssetClassificationEventDelegatorBuilder internal constructor() {
        private val eventHandlers: MutableMap<ACContractEvent, AssetClassificationEventHandler> = mutableMapOf()

        fun <T: AssetClassificationEventHandler> registerEventHandler(handler: T) = apply {
            check(handler.eventType !in eventHandlers.keys) {
                "Attempted to register more than a single handler for type [${handler.eventType}]. Previously-registered instance name: ${eventHandlers[handler.eventType]!!::class.simpleName}"
            }
            eventHandlers += handler.eventType to handler
        }

        fun build(): AssetClassificationEventDelegator = AssetClassificationEventDelegator(eventHandlers)
    }

    fun getHandledEventTypes(): Set<ACContractEvent> = eventHandlers.keys

    suspend fun delegateEvent(parameters: EventHandlerParameters) {
        eventHandlers[parameters.event.eventType]
            ?.handleEvent(parameters)
            ?: run { parameters.eventChannel.send(EventIgnoredUnknownEvent(parameters.event))}
    }
}
