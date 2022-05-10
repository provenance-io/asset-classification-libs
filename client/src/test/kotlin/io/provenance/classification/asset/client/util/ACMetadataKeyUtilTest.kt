package io.provenance.classification.asset.client.util

import io.provenance.classification.asset.client.provenance.ProvenanceAccountDetail
import io.provenance.client.wallet.NetworkType
import io.provenance.client.wallet.fromMnemonic
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.Security
import kotlin.test.assertEquals

class ACMetadataKeyUtilTest {
    @BeforeEach
    fun setupTest() {
        // The BouncyCastle provider must be registered for the KeyUtil to properly utilize it when leveraging
        // Provenance resources that use it
        Security.addProvider(BouncyCastleProvider())
    }

    @Test
    fun testKeyUtilProducesUsableOutput() {
        val mnemonic = "surge update round quantum script shed tissue maple minimum raw movie below prevent appear dice bullet pyramid tragic glue globe egg object era safe"
        // This uses wallet signer to verify that the correct address will be generated
        val addressFromWalletSigner = fromMnemonic(
            networkType = NetworkType.TESTNET,
            mnemonic = mnemonic,
            isMainNet = false,
        ).address()
        val privateKeyEncoded = ACMetadataKeyUtil.getBase64EncodedPrivateKey(
            mnemonic = mnemonic,
            networkType = NetworkType.TESTNET,
        )
        val addressFromAccountDetail = ProvenanceAccountDetail.fromBase64PrivateKey(privateKeyEncoded, mainNet = false).bech32Address
        assertEquals(
            expected = addressFromWalletSigner,
            actual = addressFromAccountDetail,
            message = "The WalletSigner-derived address should match the address from ProvenanceAccountDetail after decoding the produced base64 encoded private key",
        )
    }
}
