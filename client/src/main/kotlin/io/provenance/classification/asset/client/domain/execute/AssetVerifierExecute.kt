package io.provenance.classification.asset.client.domain.execute

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.provenance.classification.asset.client.domain.execute.base.ContractExecute
import io.provenance.classification.asset.client.domain.model.VerifierDetail

/**
 * This class is a reflection of the request body used in the Asset Classification smart contract's add or update asset
 * verifier execution routes.
 *
 * This request body should be built via its helper function, and then converted to an add or update execution command:
 * ```kotlin
 * val execute = AssetVerifierExecute.new(assetType, verifier)
 * // An add will only execute correctly if the verifier does not exist on the target asset definition
 * val addRequest = execute.toAdd()
 * val txResponse = acClient.addAssetVerifier(addRequest, signer, options)
 * // An update will only execute correctly if it targets an existing verifier on an existing asset definition
 * val updateRequest = execute.toUpdate()
 * val txResponse = acClient.updateAssetVerifier(updateRequest, signer, options)
 * ```
 *
 * @param assetType The type of asset definition that this verifier will belong/belongs to.
 * @param verifier The verifier definition that will be established / replace the updated verifier.  In the case of an
 * update, this value will attempt to find an existing verifier on the asset definition that matches the verifier's
 * address field.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class AssetVerifierExecute internal constructor(
    val assetType: String,
    val verifier: VerifierDetail,
) {
    companion object {
        /**
         * Creates an execute using the required parameters.  This helper exists to simplify the syntax needed to
         * create a request.
         */
        fun new(assetType: String, verifier: VerifierDetail): AssetVerifierExecute = AssetVerifierExecute(
            assetType = assetType,
            verifier = verifier,
        )
    }

    /**
     * Terminal function to the functional chain for creating a request for the [ACExecutor][io.provenance.classification.asset.client.client.base.ACExecutor].
     * Targets this request as an "add asset verifier."
     */
    fun toAdd(): AddAssetVerifierExecute = AddAssetVerifierExecute(this)

    /**
     * Terminal function to the functional chain for creating a request for the [ACExecutor][io.provenance.classification.asset.client.client.base.ACExecutor].
     * Targets this request as an "update asset verifier."
     */
    fun toUpdate(): UpdateAssetVerifierExecute = UpdateAssetVerifierExecute(this)
}

@JsonNaming(SnakeCaseStrategy::class)
class AddAssetVerifierExecute internal constructor(val addAssetVerifier: AssetVerifierExecute) : ContractExecute

@JsonNaming(SnakeCaseStrategy::class)
class UpdateAssetVerifierExecute internal constructor(val updateAssetVerifier: AssetVerifierExecute) : ContractExecute
