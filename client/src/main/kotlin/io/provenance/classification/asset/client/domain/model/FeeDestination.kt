package io.provenance.classification.asset.client.domain.model

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.math.BigDecimal

/**
 * Defines a collector for fees for a validator.  All fee destinations should have fee percents totaling 100%, and the
 * contract runs validation to ensure that this is the case, so it can be assumed true in all cases.
 *
 * @param address The bech32 address of the recipient of fees.
 * @param feePercent A decimal from 0-1, indicating a percentage.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class FeeDestination(
    val address: String,
    val feePercent: String,
) {
    companion object {
        fun new(
            address: String,
            feePercent: BigDecimal,
        ): FeeDestination = FeeDestination(
            address = address,
            feePercent = feePercent.toString(),
        )
    }
}
