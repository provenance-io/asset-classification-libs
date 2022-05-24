package io.provenance.classification.asset.client.extensions

import java.math.BigDecimal
import java.math.RoundingMode

// Max uint128 value is 2^128 - 1
internal val MAX_UINT_VALUE: BigDecimal = BigDecimal.valueOf(2).pow(128).minus(BigDecimal.ONE)

/**
 * Certain routes allow larger numbers than the Long data type supports, but require that no decimal places be provided.
 * This extension for the BigDecimal type ensures that values used that do provide decimal places will have those decimals
 * trimmed, as well as not being too large for the smart contract to handle (max values are set at u128).
 */
internal fun BigDecimal.toUint128CompatibleStringAc(): String {
    require(this in BigDecimal.ZERO..MAX_UINT_VALUE) { "Value [$this] is not supported by the Asset Classification smart contract.  Allowed value range: [0 -> $MAX_UINT_VALUE]" }
    return this.setScale(0, RoundingMode.DOWN).toString()
}
