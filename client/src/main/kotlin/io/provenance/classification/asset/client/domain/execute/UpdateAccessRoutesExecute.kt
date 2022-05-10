package io.provenance.classification.asset.client.domain.execute

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.provenance.classification.asset.client.domain.execute.base.ContractExecute
import io.provenance.classification.asset.client.domain.model.AccessRoute
import io.provenance.classification.asset.client.domain.model.AssetIdentifier
import java.util.UUID

/**
 * This class is a reflection of the request body used in the Asset Classification smart contract's update access routes
 * execution route.  Companion object functions are recommended for instantiation due to the body structure complexity.
 *
 * Sample usage:
 * ```kotlin
 * val executeForAsset = UpdateAccessRoutesExecute.withAssetUuid(assetUuid, ownerAddress, routes)
 * val txResponse = acClient.updateAccessRoutes(executeForAsset, signer, options)
 *
 * val executeForScope = UpdateAccessRoutesExecute.withScopeAddress(scopeAddress, ownerAddress, routes)
 * val txResponse = acClient.updateAccessRoutes(executeForAsset, signer, options)
 * ```
 */
@JsonNaming(SnakeCaseStrategy::class)
class UpdateAccessRoutesExecute<T>(val updateAccessRoutes: UpdateAccessRoutesBody<T>) : ContractExecute {
    companion object {
        /**
         * Creates an execute using an asset uuid as the asset identifier.  This helper exists to simplify the syntax
         * needed to create a request.
         */
        fun withAssetUuid(
            assetUuid: UUID,
            ownerAddress: String,
            accessRoutes: List<AccessRoute>,
        ): UpdateAccessRoutesExecute<UUID> = withIdentifier(
            identifier = AssetIdentifier.AssetUuid(assetUuid),
            ownerAddress = ownerAddress,
            accessRoutes = accessRoutes,
        )

        /**
         * Creates an execute using a scope address as the identifier.  This helper exists to simplify the syntax
         * needed to create a request.
         */
        fun withScopeAddress(
            scopeAddress: String,
            ownerAddress: String,
            accessRoutes: List<AccessRoute>,
        ): UpdateAccessRoutesExecute<String> = withIdentifier(
            identifier = AssetIdentifier.ScopeAddress(scopeAddress),
            ownerAddress = ownerAddress,
            accessRoutes = accessRoutes,
        )

        private fun <T> withIdentifier(
            identifier: AssetIdentifier<T>,
            ownerAddress: String,
            accessRoutes: List<AccessRoute>,
        ): UpdateAccessRoutesExecute<T> = UpdateAccessRoutesExecute(
            updateAccessRoutes = UpdateAccessRoutesBody(
                identifier = identifier,
                ownerAddress = ownerAddress,
                accessRoutes = accessRoutes,
            )
        )
    }
}

/**
 * The body inside the contract execution payload.  This exists as a separate class to ensure that jackson correctly
 * maps the payload for the contract's format specifications.
 *
 * @param identifier Identifiers the asset containing the access routes by uuid or scope address.
 * @param ownerAddress The bech32 address listed on an [AccessDefinition][io.provenance.classification.asset.client.domain.model.AccessDefinition] on the target [AssetScopeAttribute][io.provenance.classification.asset.client.domain.model.AssetScopeAttribute].
 * @param accessRoutes All the new access routes to include in the [AccessDefinition][io.provenance.classification.asset.client.domain.model.AccessDefinition].  Note: All existing routes will be
 *                     completely removed and replaced with these values.  Additionally, this list can be empty, which
 *                     is accepted input and will cause the existing access routes for the target definition to be
 *                     deleted.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class UpdateAccessRoutesBody<T>(
    val identifier: AssetIdentifier<T>,
    val ownerAddress: String,
    val accessRoutes: List<AccessRoute>,
)
