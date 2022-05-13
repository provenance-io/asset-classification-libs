package io.provenance.classification.asset.localtools.tool

import io.provenance.classification.asset.util.enums.ProvenanceNetworkType
import io.provenance.classification.asset.util.wallet.ProvenanceAccountDetail
import io.provenance.client.grpc.GasEstimationMethod
import io.provenance.client.grpc.PbClient
import java.net.URI

class SetupACToolExample {
    fun exampleUsage() {
        SetupACTool.setupContract(
            config = SetupACToolConfig(
                pbClient = PbClient(
                    chainId = "chain-local",
                    channelUri = URI.create("http://localhost:9090"),
                    gasEstimationMethod = GasEstimationMethod.MSG_FEE_CALCULATION,
                ),
                assetNameAdminAccount = ProvenanceAccountDetail.fromMnemonic(
                    mnemonic = "hub error can bread person quick blur delay nation ignore tennis orphan inch ankle win grunt door turkey ball hockey bridge fragile dose cage",
                    networkType = ProvenanceNetworkType.TESTNET,
                ),
                contractAdminAccount = ProvenanceAccountDetail.fromMnemonic(
                    mnemonic = "grocery want voyage raise betray type vintage offer beach purity mercy manage debate solar blast spray grocery actor remove favorite change bargain mansion tortoise",
                    networkType = ProvenanceNetworkType.TESTNET,
                ),
                verifierBech32Address = "tp1zvkp4yzxky05tt97xce47sucdkzqgqwtza3g6n",
                logger = SetupACToolLogging.Println,
            )
        )
    }
}
