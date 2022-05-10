package io.provenance.classification.asset.client.util

import io.provenance.classification.asset.client.extension.base64EncodeString
import io.provenance.classification.asset.client.extension.isTestNet
import io.provenance.client.wallet.NetworkType
import io.provenance.hdwallet.bip39.MnemonicWords
import io.provenance.hdwallet.ec.extensions.toJavaECPrivateKey
import io.provenance.hdwallet.wallet.Wallet
import io.provenance.scope.encryption.ecies.ECUtils
import java.security.PrivateKey

object ACMetadataKeyUtil {
    fun getBase64EncodedPrivateKey(
        mnemonic: String,
        networkType: NetworkType = NetworkType.TESTNET,
    ): String =
        Wallet.fromMnemonic(
            hrp = networkType.prefix,
            passphrase = "",
            mnemonicWords = MnemonicWords.of(mnemonic),
            testnet = networkType.isTestNet(),
        )[networkType.path]
            .keyPair
            .privateKey
            .let(::getBase64EncodedPrivateKey)

    fun getBase64EncodedPrivateKey(
        privateKey: io.provenance.hdwallet.ec.PrivateKey
    ): String = getBase64EncodedPrivateKey(privateKey.toJavaECPrivateKey())

    fun getBase64EncodedPrivateKey(
        privateKey: PrivateKey,
    ): String = ECUtils.convertPrivateKeyToBytes(privateKey).base64EncodeString()
}
