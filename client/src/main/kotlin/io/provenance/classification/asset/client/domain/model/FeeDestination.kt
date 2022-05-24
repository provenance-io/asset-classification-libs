package io.provenance.classification.asset.client.domain.model

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.provenance.classification.asset.client.extensions.toUint128CompatibleStringAc
import java.math.BigDecimal

/**
 * Defines a collector for fees for a validator.  All fee destinations should have fee percents totaling 100%, and the
 * contract runs validation to ensure that this is the case, so it can be assumed true in all cases.
 *
 * @param address The bech32 address of the recipient of fees.
 * @param feeAmount A value without decimal places that indicates how much coin this fee destination receives during
 * the onboarding process.
 * @param entityDetail A node of additional values that defines human-readable information about the fee destination.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class FeeDestination(
    val address: String,
    val feeAmount: String,
    val entityDetail: EntityDetail?,
) {
    companion object {
        fun new(
            address: String,
            feeAmount: BigDecimal,
            entityDetail: EntityDetail? = null,
        ): FeeDestination = FeeDestination(
            address = address,
            // The fee amount must not have any decimal places - remove them before setting the value. This represents a coin amount
            feeAmount = feeAmount.toUint128CompatibleStringAc(),
            entityDetail = entityDetail,
        )
    }
}
