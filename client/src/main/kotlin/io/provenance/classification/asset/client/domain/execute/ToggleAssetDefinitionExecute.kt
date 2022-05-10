package io.provenance.classification.asset.client.domain.execute

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.provenance.classification.asset.client.domain.execute.base.ContractExecute

/**
 * This class is a reflection of the request body used in the Asset Classification smart contract's toggle asset
 * definition execution route.  It simply toggles the definition from enabled to disabled, or vice versa.
 *
 * Sample usage:
 * ```kotlin
 * val toggleOffFromOn = ToggleAssetDefinitionExecute.new(assetType, false)
 * val txResponse = acClient.toggleAssetDefinition(toggleOffFromOn, signer, options)
 * ```
 *
 * @param toggleAssetDefinition The body value that will be used for this request.
 */
@JsonNaming(SnakeCaseStrategy::class)
class ToggleAssetDefinitionExecute internal constructor(val toggleAssetDefinition: ToggleAssetDefinitionBody) :
    ContractExecute {
    companion object {
        /**
         * Creates an execute with the body expanded to the function parameters.  This helper exists to simplify the
         * syntax needed to create a request.
         */
        fun new(assetType: String, expectedResult: Boolean): ToggleAssetDefinitionExecute =
            ToggleAssetDefinitionExecute(
                ToggleAssetDefinitionBody(
                    assetType = assetType,
                    expectedResult = expectedResult,
                )
            )
    }
}

/**
 * The body inside the contract execution payload.  This exists as a separate class to ensure that jackson correctly
 * maps the payload for the contract's format specifications.
 *
 * @param assetType The asset type to be toggled.  This should correspond to an existing asset definition within the contract.
 * @param expectedResult The state of [AssetDefinition.enabled][io.provenance.classification.asset.client.domain.model.AssetDefinition.enabled] after execution completes.  Exists as a latch to ensure
 * that multiple requests processed simultaneously don't have unexpected results.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class ToggleAssetDefinitionBody(
    val assetType: String,
    val expectedResult: Boolean,
)
