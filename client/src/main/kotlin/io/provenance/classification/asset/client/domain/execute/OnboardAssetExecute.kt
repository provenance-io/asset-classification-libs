package io.provenance.classification.asset.client.domain.execute

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.provenance.classification.asset.client.domain.execute.base.ContractExecute
import io.provenance.classification.asset.client.domain.model.AccessRoute
import io.provenance.classification.asset.client.domain.model.AssetIdentifier
import java.util.UUID

/**
 * This class is a reflection of the request body used in the Asset Classification smart contract's onboard asset
 * execution route.  Companion object functions are recommended for instantiation due to the body structure complexity.
 *
 * Sample usage:
 * ```kotlin
 * val executeForAsset = OnboardAssetExecute.withAssetUuid(assetUuid, assetType, verifierAddress, routes)
 * val txResponse = acClient.onboardAsset(executeForAsset, signer, options)
 *
 * val executeForScope = OnboardAssetExecute.withScopeAddress(scopeAddress, assetType, verifierAddress, routes)
 * val txResponse = acClient.onboardAsset(executeForScope, signer, options)
 * ```
 *
 * @param onboardAsset The body value that will be used for this request.
 */
@JsonNaming(SnakeCaseStrategy::class)
class OnboardAssetExecute<T>(val onboardAsset: OnboardAssetBody<T>) : ContractExecute {
    companion object {
        /**
         * Creates an execute using an asset uuid as the asset identifier.  This helper exists to simplify the syntax
         * needed to create a request.
         */
        fun withAssetUuid(
            assetUuid: UUID,
            assetType: String,
            verifierAddress: String,
            accessRoutes: List<AccessRoute>? = null,
        ): OnboardAssetExecute<UUID> = withIdentifier(
            identifier = AssetIdentifier.AssetUuid(assetUuid),
            assetType = assetType,
            verifierAddress = verifierAddress,
            accessRoutes = accessRoutes,
        )

        /**
         * Creates an execute using a scope address as the identifier.  This helper exists to simplify the syntax
         * needed to create a request.
         */
        fun withScopeAddress(
            scopeAddress: String,
            assetType: String,
            verifierAddress: String,
            accessRoutes: List<AccessRoute>? = null,
        ): OnboardAssetExecute<String> = withIdentifier(
            identifier = AssetIdentifier.ScopeAddress(
                scopeAddress
            ),
            assetType = assetType,
            verifierAddress = verifierAddress,
            accessRoutes = accessRoutes,
        )

        private fun <T> withIdentifier(
            identifier: AssetIdentifier<T>,
            assetType: String,
            verifierAddress: String,
            accessRoutes: List<AccessRoute>?,
        ): OnboardAssetExecute<T> = OnboardAssetExecute(
            onboardAsset = OnboardAssetBody(
                identifier = identifier,
                assetType = assetType,
                verifierAddress = verifierAddress,
                accessRoutes = accessRoutes,
            )
        )
    }
}

/**
 * The body inside the contract execution payload.  This exists as a separate class to ensure that jackson correctly
 * maps the payload for the contract's format specifications.
 *
 * @param identifier Identifies the asset by uuid or scope address.
 * @param assetType The type of asset that the scope contains.  Each type should be mapped to a specific scope specification,
 * which can be derived via queries to the contract.
 * @param verifierAddress The address of the verifier to use after onboarding. The available verifiers for each asset
 * type can be found by querying the contract.
 * @param accessRoutes Each verifier should be configured to locate the asset record data via these provided access
 * routes.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class OnboardAssetBody<T>(
    val identifier: AssetIdentifier<T>,
    val assetType: String,
    val verifierAddress: String,
    val accessRoutes: List<AccessRoute>?,
)
