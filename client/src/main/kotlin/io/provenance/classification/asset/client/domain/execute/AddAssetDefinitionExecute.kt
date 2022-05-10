package io.provenance.classification.asset.client.domain.execute

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.provenance.classification.asset.client.domain.execute.base.ContractExecute
import io.provenance.classification.asset.client.domain.model.ScopeSpecIdentifier
import io.provenance.classification.asset.client.domain.model.VerifierDetail
import java.util.UUID

/**
 * This class is a reflection of the request body used in the Asset Classification smart contract's add or update asset
 * definition execution routes.
 *
 * This request body is relatively complex, and re-uses its internal body across two calls (add/update).
 * To use it, simply create the execute class, and then choose add or update:
 * ```kotlin
 * val execute = AssetDefinitionExecute.withAssetUuid(uuid, assetType, verifiers, enabled = true)
 * // An add will only execute correctly if it is a brand new asset definition
 * val addRequest = execute.toAdd()
 * val txResponse = acClient.addAssetDefinition(addRequest, signer, options)
 * // An update will only execute correctly if it is an existing asset definition
 * val updateRequest = execute.toUpdate()
 * val txResponse = acClient.updateAssetDefinition(updateRequest, signer, options)
 * ```
 *
 * @param assetType The type of asset that will be added or updated. This value is a unique key in the contract.
 * @param scopeSpecIdentifier Identifies the scope spec that this asset definition is associated with. This value is a unique constraint and can only be mapped to one asset definition.
 * @param verifiers All verifiers that are allowed to do verification for this specific asset type.
 * @param enabled Whether or not this asset type will accept incoming onboard requests.  If left null, the default value used will be `true`
 * @param bindName Whether or not to bind the name value creating an asset definition. Only affects the result when being used with the "add" functionality.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class AssetDefinitionExecute<T> internal constructor(
    val assetType: String,
    val scopeSpecIdentifier: ScopeSpecIdentifier<T>,
    val verifiers: List<VerifierDetail>,
    val enabled: Boolean?,
    val bindName: Boolean?,
) {
    companion object {
        /**
         * Creates an execute using a scope specification uuid as the scope spec identifier.  This helper exists to
         * simplify the syntax needed to create a request.
         *
         * Reminder: The bindName property only affects the result if it is used to ADD an asset definition.
         */
        fun withScopeSpecUuid(
            scopeSpecUuid: UUID,
            assetType: String,
            verifiers: List<VerifierDetail>,
            enabled: Boolean? = null,
            bindName: Boolean? = null,
        ): AssetDefinitionExecute<UUID> = AssetDefinitionExecute(
            assetType = assetType,
            scopeSpecIdentifier = ScopeSpecIdentifier.Uuid(scopeSpecUuid),
            verifiers = verifiers,
            enabled = enabled,
            bindName = bindName,
        )

        /**
         * Creates an execute using a scope specification address as the scope spec identifier.  This helper exists to
         * simplify the syntax needed to create a request.
         *
         * Reminder: The bindName property only affects the result if it is used to ADD an asset definition.
         */
        fun withScopeSpecAddress(
            scopeSpecAddress: String,
            assetType: String,
            verifiers: List<VerifierDetail>,
            enabled: Boolean? = null,
            bindName: Boolean? = null,
        ): AssetDefinitionExecute<String> = AssetDefinitionExecute(
            assetType = assetType,
            scopeSpecIdentifier = ScopeSpecIdentifier.Address(scopeSpecAddress),
            verifiers = verifiers,
            enabled = enabled,
            bindName = bindName,
        )
    }

    /**
     * Terminal function to the functional chain for creating a request for the [ACExecutor][io.provenance.classification.asset.client.client.base.ACExecutor].
     * Targets this request as an "add asset definition."
     */
    fun toAdd(): AddAssetDefinitionExecute<T> = AddAssetDefinitionExecute(AssetDefinitionBody(this))

    /**
     * Terminal function to the functional chain for creating a request for the [ACExecutor][io.provenance.classification.asset.client.client.base.ACExecutor].
     * Targets this request as an "update asset definition."
     */
    fun toUpdate(): UpdateAssetDefinitionExecute<T> = UpdateAssetDefinitionExecute(AssetDefinitionBody(this))
}

@JsonNaming(SnakeCaseStrategy::class)
class AddAssetDefinitionExecute<T> internal constructor(val addAssetDefinition: AssetDefinitionBody<T>) :
    ContractExecute

@JsonNaming(SnakeCaseStrategy::class)
class UpdateAssetDefinitionExecute<T> internal constructor(val updateAssetDefinition: AssetDefinitionBody<T>) :
    ContractExecute

@JsonNaming(SnakeCaseStrategy::class)
class AssetDefinitionBody<T> internal constructor(val assetDefinition: AssetDefinitionExecute<T>)
