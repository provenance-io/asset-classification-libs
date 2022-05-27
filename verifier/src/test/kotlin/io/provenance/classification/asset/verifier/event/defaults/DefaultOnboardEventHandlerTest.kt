package io.provenance.classification.asset.verifier.event.defaults

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.provenance.classification.asset.client.domain.model.AssetOnboardingStatus
import io.provenance.classification.asset.verifier.config.VerifierEvent
import io.provenance.classification.asset.verifier.config.VerifierEvent.OnboardEventIgnoredMissingScopeAddress
import io.provenance.classification.asset.verifier.config.VerifierEvent.OnboardEventIgnoredMissingScopeAttribute
import io.provenance.classification.asset.verifier.event.EventHandlerParameters
import io.provenance.classification.asset.verifier.provenance.ACContractEvent
import io.provenance.classification.asset.verifier.provenance.AssetClassificationEvent
import io.provenance.classification.asset.verifier.testhelpers.MockACAttribute
import io.provenance.classification.asset.verifier.testhelpers.MockTxEvent
import io.provenance.classification.asset.verifier.testhelpers.MockTxEvent.MockTxEventBuilder
import io.provenance.classification.asset.verifier.testhelpers.assertLastEvent
import io.provenance.classification.asset.verifier.testhelpers.getMockScopeAttribute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class DefaultOnboardEventHandlerTest {
    @Test
    fun testEmptyEventReceived() = runTest {
        val parameters = getMockParameters()
        DefaultOnboardEventHandler.handleEvent(parameters)
        assertTrue(
            actual = parameters.verificationChannel.isEmpty,
            message = "The verification channel should not receive any input",
        )
        assertFalse(
            actual = parameters.eventChannel.isEmpty,
            message = "An event should have been emitted",
        )
        assertLastEvent<OnboardEventIgnoredMissingScopeAddress>(parameters) { (event, message) ->
            assertEquals(
                expected = parameters.event,
                actual = event,
                message = "Expected the event to contain the asset classification event",
            )
            assertTrue(
                actual = "Expected the onboard asset event to include a scope address, but it was missing" in message,
                message = "Expected the correct event message to be included in the output",
            )
        }
    }

    @Test
    fun testFailureToFindScopeAttribute() = runTest {
        val parameters = getMockParameters { builder ->
            builder.addACAttribute(MockACAttribute.ScopeAddress("mock-scope-address"))
        }
        every { parameters.acClient.queryAssetScopeAttributeByScopeAddress(any()) } throws(IllegalStateException("Failed to query for scope"))
        DefaultOnboardEventHandler.handleEvent(parameters)
        assertTrue(
            actual = parameters.verificationChannel.isEmpty,
            message = "The verification channel should not receive any input",
        )
        assertLastEvent<OnboardEventIgnoredMissingScopeAttribute>(parameters) { (event, message, t) ->
            assertEquals(
                expected = parameters.event,
                actual = event,
                message = "Expected the event to be included in the error",
            )
            assertTrue(
                actual = "Intercepted onboard asset did not point to a scope with a scope attribute" in message,
                message = "Expected the event message to be formatted correctly",
            )
            assertTrue(
                actual = t is IllegalStateException,
                message = "Expected the throwable to be formatted correctly",
            )
            assertEquals(
                expected = "Failed to query for scope",
                actual = t.message,
                message = "Expected the error message to be populated correctly",
            )
        }
    }

    @Test
    fun testInvalidOnboardingStatusInScopeAttribute() = runTest {
        val parameters = getMockParameters { builder ->
            builder.addACAttribute(MockACAttribute.ScopeAddress("mock-scope-address"))
        }
        val mockScopeAttribute = getMockScopeAttribute(onboardingStatus = AssetOnboardingStatus.DENIED)
        every { parameters.acClient.queryAssetScopeAttributeByScopeAddress(any()) } returns mockScopeAttribute
        DefaultOnboardEventHandler.handleEvent(parameters)
        assertTrue(
            actual = parameters.verificationChannel.isEmpty,
            message = "The verification channel should not receive any input",
        )
        assertLastEvent<VerifierEvent.OnboardEventIgnoredPreviouslyProcessed>(parameters) { (event, scopeAttribute, message) ->
            assertEquals(
                expected = parameters.event,
                actual = event,
                message = "Expected the classification event to be included in the event",
            )
            assertEquals(
                expected = scopeAttribute,
                actual = mockScopeAttribute,
                message = "Expected the scope attribute to be included in the event",
            )
            assertTrue(
                actual = "Scope attribute indicates an onboarding status of [DENIED], which is not actionable. Has verification: [Verified = false | Message = MOCK: Denied]" in message,
                message = "Expected the proper message to be included in the event, but got message: $message",
            )
        }
    }

    @Test
    fun testFailureToRetrieveAsset() = runTest {
        val parameters = getMockParameters { builder ->
            builder.addACAttribute(MockACAttribute.ScopeAddress("mock-scope-address"))
        }
        val mockScopeAttribute = getMockScopeAttribute()
        every { parameters.acClient.queryAssetScopeAttributeByScopeAddress(any()) } returns mockScopeAttribute
        coEvery { parameters.processor.retrieveAsset(any(), any(), any()) } throws(IllegalStateException("MOCK: Failed to retrieve asset"))
        DefaultOnboardEventHandler.handleEvent(parameters)
        assertTrue(
            actual = parameters.verificationChannel.isEmpty,
            message = "The verification channel should not receive any input",
        )
        assertLastEvent<VerifierEvent.OnboardEventFailedToRetrieveAsset>(parameters) { (event, scopeAttribute, t) ->
            assertEquals(
                expected = parameters.event,
                actual = event,
                message = "Expected the event to include the classification event from the parameters",
            )
            assertEquals(
                expected = mockScopeAttribute,
                actual = scopeAttribute,
                message = "Expected the event to include the retrieved scope attribute",
            )
            assertTrue(
                actual = t is IllegalStateException,
                message = "Expected the exception to be an IllegalStateException, but was ${t::class.simpleName}",
            )
            assertEquals(
                expected = "MOCK: Failed to retrieve asset",
                actual = t.message,
                message = "Expected the exception to include the correct message",
            )
        }

    }

    private fun getMockParameters(event: AssetClassificationEvent): EventHandlerParameters = EventHandlerParameters(
        event = event,
        acClient = mockk(),
        processor = mockk(),
        verificationChannel = Channel(capacity = Channel.BUFFERED),
        eventChannel = Channel(capacity = Channel.BUFFERED),
    )

    private fun getMockParameters(
        builderFn: (MockTxEventBuilder) -> MockTxEventBuilder = { it }
    ): EventHandlerParameters = MockTxEvent
        .builder()
        .addACAttribute(MockACAttribute.EventType(ACContractEvent.ONBOARD_ASSET))
        .let(builderFn)
        .buildACEvent()
        .let(::getMockParameters)
}
