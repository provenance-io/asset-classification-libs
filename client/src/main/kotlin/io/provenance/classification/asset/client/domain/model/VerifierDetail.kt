package io.provenance.classification.asset.client.domain.model

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A configuration for a verifier's interactions with the Asset Classification smart contract.
 *
 * @param address The bech32 address of the verifier. A verifier application should have full access to this address in
 * order to execute the Asset Classification smart contract with this address as the signer.
 * @param onboardingCost A numeric representation of a specified coin amount to be taken during onboarding.  This value
 * will be distributed to the verifier and its fee destinations based on those configurations.
 * @param onboardingDenom The denomination of coin required for onboarding.  This value is unsed in tandem with
 * onboarding cost to determine a coin required.
 * @param feePercent A percentage from 0-1 that determines how much should be taken from the onboarding cost to be sent
 * to the fee destinations.  The remainder goes to the verifier.
 * @param feeDestinations A collection of addresses and fee distribution amounts that dictates how the fee percentage is
 * distributed to other addresses than the verifier.
 * @param entityDetail An optional set of fields defining the validator in a human-readable way.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class VerifierDetail(
    val address: String,
    val onboardingCost: String,
    val onboardingDenom: String,
    val feePercent: String,
    val feeDestinations: List<FeeDestination>,
    val entityDetail: EntityDetail?,
) {
    companion object {
        fun new(
            address: String,
            onboardingCost: BigDecimal,
            onboardingDenom: String,
            feePercent: BigDecimal,
            feeDestinations: List<FeeDestination> = emptyList(),
            entityDetail: EntityDetail? = null,
        ): VerifierDetail = VerifierDetail(
            address = address,
            // The cost must not have any decimal places - remove them before setting the value. This represents a coin amount
            onboardingCost = onboardingCost.setScale(0, RoundingMode.DOWN).toString(),
            onboardingDenom = onboardingDenom,
            feePercent = feePercent.toString(),
            feeDestinations = feeDestinations,
            entityDetail = entityDetail,
        )
    }
}
