package io.provenance.classification.asset.client.domain.execute

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.provenance.classification.asset.client.domain.execute.base.ContractExecute
import io.provenance.classification.asset.client.domain.model.AssetQualifier

/**
 * This class is a reflection of the request body used in the Asset Classification smart contract's delete asset
 * definition execution route.  It completely removes the target asset definition, and can cause errors if the
 * definition is currently in use for actively onboarding assets.  In other words: THIS IS DANGEROUS AND SHOULD ONLY BE
 * USED TO REMOVE AN ERRONEOUSLY-ADDED DEFINITION.
 *
 * Sample usage:
 * ```kotlin
 * val deleteByTypeExecute = DeleteAssetDefinitionExecute.forAssetType(assetType)
 * val txResponse = acClient.deleteAssetDefinition(deleteByTypeExecute, signer, options)
 *
 * val deleteByScopeSpecExecute = DeleteAssetDefinitionExecute.forScopeSpecAddress(scopeSpecAddress)
 * val txResponse = acClient.deleteAssetDefinition(deleteByScopeSpecExecute, signer, options)
 * ```
 */
@JsonNaming(SnakeCaseStrategy::class)
class DeleteAssetDefinitionExecute internal constructor(val deleteAssetDefinition: DeleteAssetDefinitionBody) :
    ContractExecute {
    companion object {
        fun forAssetType(assetType: String): DeleteAssetDefinitionExecute = DeleteAssetDefinitionExecute(
            deleteAssetDefinition = DeleteAssetDefinitionBody(
                qualifier = AssetQualifier.AssetType(assetType),
            )
        )

        fun forScopeSpecAddress(scopeSpecAddress: String): DeleteAssetDefinitionExecute = DeleteAssetDefinitionExecute(
            deleteAssetDefinition = DeleteAssetDefinitionBody(
                qualifier = AssetQualifier.ScopeSpecAddress(scopeSpecAddress),
            )
        )
    }
}

/**
 * The body inside the contract execute payload.  This exists as a separate class to ensure that jackson correctly maps
 * the payload for the contract's format specifications.
 *
 * @param qualifier The asset qualifier used to identify the asset definition to delete.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class DeleteAssetDefinitionBody(val qualifier: AssetQualifier)
