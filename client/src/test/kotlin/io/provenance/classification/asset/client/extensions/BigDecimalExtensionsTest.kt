package io.provenance.classification.asset.client.extensions

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class BigDecimalExtensionsTest {
    @Test
    fun `test toUint128CompatibleStringAc for too low value`() {
        assertThrows<IllegalArgumentException>("Values below zero should not be allowed") {
            "-0.000000000001".toBigDecimal().toUint128CompatibleStringAc()
        }
    }

    @Test
    fun `test toUint128CompatibleStringAc for too high value`() {
        assertThrows<IllegalArgumentException>("Values above the max amount should not be allowed") {
            MAX_UINT_VALUE.plus("0.0000000000001".toBigDecimal()).toUint128CompatibleStringAc()
        }
    }

    @Test
    fun `test toUint128CompatibleStringAc displays simple values correctly`() {
        assertEquals(
            expected = "0",
            actual = "0".toBigDecimal().toUint128CompatibleStringAc(),
            message = "Expected a 1 to 1 conversion for input to output",
        )
        assertEquals(
            expected = "0",
            actual = "0000000".toBigDecimal().toUint128CompatibleStringAc(),
            message = "Expected all trailing zeroes to be trimmed from input",
        )
        assertEquals(
            expected = "1",
            actual = "1".toBigDecimal().toUint128CompatibleStringAc(),
            message = "Expected a 1 to 1 conversion for input to output",
        )
        assertEquals(
            expected = "12345",
            actual = "12345".toBigDecimal().toUint128CompatibleStringAc(),
            message = "Expected a 1 to 1 conversion for input to output",
        )
        assertEquals(
            expected = "123456789",
            actual = "000000123456789".toBigDecimal().toUint128CompatibleStringAc(),
            message = "Expected all leading zeroes to be trimmed from input",
        )
    }

    @Test
    fun `test toUint128CompatibleStringAc trims all decimal points`() {
        assertEquals(
            expected = "1",
            actual = "1.2345678901321230349832409".toBigDecimal().toUint128CompatibleStringAc(),
            message = "Expected all decimal points to be trimmed",
        )
        assertEquals(
            expected = "1234",
            actual = "00000000000001234.43928409384093284093284908320948329084".toBigDecimal().toUint128CompatibleStringAc(),
            message = "Expected all leading zeroes to be trimmed and all decimal places to be trimmed",
        )
    }

    @Test
    fun `test toUint128CompatibleStringAc properly displays very large numbers`() {
        assertEquals(
            // This value is close to but not exactly the max u128 value, so this is a decent test of sending very large
            // numbers to the smart contract and ensuring that they display as numeric strings versus scientific notation
            // or other garbled formats
            expected = "300000000000000000000000000000000000000",
            actual = "10".toBigDecimal().pow(38).times("3".toBigDecimal()).toUint128CompatibleStringAc(),
            message = "Expected the very large number to display in all its trailing zero glory",
        )
    }
}
