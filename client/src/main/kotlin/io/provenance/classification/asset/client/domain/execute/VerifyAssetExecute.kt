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
 * val executeForAsset = VerifyAssetExecute.withAssetUuid(assetUuid, true, "verify success", listOf("route"))
 * val txResponse = acClient.verifyAsset(executeForAsset, signer, options)
 *
 * val executeForScope = VerifyAssetExecute.withScopeAddress(scopeAddress, true, "MAJOR SUCCESS", listOf("some-route"))
 * val txResponse = acClient.verifyAsset(executeForScope, signer, options)
 * ```
 * @param verifyAsset The body value that will be used for this request.
 */
@JsonNaming(SnakeCaseStrategy::class)
class VerifyAssetExecute<T>(val verifyAsset: VerifyAssetBody<T>) : ContractExecute {
    companion object {
        fun withAssetUuid(
            assetUuid: UUID,
            success: Boolean,
            message: String? = null,
            accessRoutes: List<AccessRoute>? = null,
        ): VerifyAssetExecute<UUID> = withIdentifier(
            identifier = AssetIdentifier.AssetUuid(assetUuid),
            success = success,
            message = message,
            accessRoutes = accessRoutes,
        )

        fun withScopeAddress(
            scopeAddress: String,
            success: Boolean,
            message: String? = null,
            accessRoutes: List<AccessRoute>? = null,
        ): VerifyAssetExecute<String> = withIdentifier(
            identifier = AssetIdentifier.ScopeAddress(scopeAddress),
            success = success,
            message = message,
            accessRoutes = accessRoutes,
        )

        private fun <T> withIdentifier(
            identifier: AssetIdentifier<T>,
            success: Boolean,
            message: String?,
            accessRoutes: List<AccessRoute>?,
        ): VerifyAssetExecute<T> = VerifyAssetExecute(
            verifyAsset = VerifyAssetBody(
                identifier = identifier,
                success = success,
                message = message,
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
 * @param success Whether or not verification succeeded.
 * @param message A custom message indicating the reason for the chosen verification result.
 * @param accessRoutes An optional field that specifies a location at which the verifier has exposed the asset data to
 * authenticated requesters.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class VerifyAssetBody<T>(
    val identifier: AssetIdentifier<T>,
    val success: Boolean,
    val message: String?,
    val accessRoutes: List<AccessRoute>?,
)
