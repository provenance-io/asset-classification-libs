package io.provenance.classification.asset.localtools.tool

import cosmos.tx.v1beta1.ServiceOuterClass.BroadcastMode
import cosmwasm.wasm.v1.Tx
import cosmwasm.wasm.v1.Types
import io.provenance.classification.asset.client.client.base.ACClient
import io.provenance.classification.asset.client.client.base.ContractIdentifier
import io.provenance.classification.asset.client.domain.execute.AssetDefinitionExecute
import io.provenance.classification.asset.client.domain.execute.BindContractAliasExecute
import io.provenance.classification.asset.client.domain.model.EntityDetail
import io.provenance.classification.asset.client.domain.model.VerifierDetail
import io.provenance.classification.asset.localtools.extensions.broadcastTxAc
import io.provenance.classification.asset.localtools.extensions.checkNotNullAc
import io.provenance.classification.asset.localtools.extensions.getCodeIdAc
import io.provenance.classification.asset.localtools.extensions.getContractAddressAc
import io.provenance.classification.asset.localtools.extensions.gzipAc
import io.provenance.classification.asset.localtools.feign.GitHubApiClient
import io.provenance.classification.asset.localtools.models.contract.AssetClassificationContractInstantiate
import io.provenance.classification.asset.util.extensions.isErrorAc
import io.provenance.classification.asset.util.extensions.wrapListAc
import io.provenance.classification.asset.util.objects.ACObjectMapperUtil
import io.provenance.classification.asset.util.wallet.ProvenanceAccountDetail
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.grpc.PbClient
import io.provenance.client.protobuf.extensions.toAny
import io.provenance.client.protobuf.extensions.toTxBody
import io.provenance.name.v1.MsgBindNameRequest
import io.provenance.name.v1.NameRecord
import io.provenance.scope.util.toByteString
import io.provenance.spec.AssetSpecifications
import java.math.BigDecimal
import java.net.URL

object SetupACTool {
    private val OBJECT_MAPPER by lazy { ACObjectMapperUtil.getObjectMapper() }

    fun setupContract(config: SetupACToolConfig) {
        val contractAddress = downloadAndInstantiateSmartContract(config)
        setupAssetDefinitions(config, contractAddress)
    }

    private fun downloadWasmBytes(config: SetupACToolConfig): ByteArray {
        config.logger("Querying GitHub for a download link to the asset classification smart contract's WASM file")
        val contractDownloadUrl = GitHubApiClient.new().getReleases(
            organization = "provenance-io",
            repository = "asset-classification-smart-contract",
        ).assets
            .singleOrNull { it.name == "asset_classification_smart_contract.wasm" }
            .checkNotNullAc { "Expected an asset in the asset-classification-smart-contract repository to be the WASM file for the contract" }
            .browserDownloadUrl
            .let(::URL)
        config.logger("Found WASM file at browser download link [$contractDownloadUrl]. Downloading...")
        return contractDownloadUrl
            .readBytes()
            // Incredibly important step: The upstream server requires that all stored wasm bytes are 500K or less, but
            // contracts themselves can be larger than that.  The server supports receipt of GZipped WASM payloads, so this
            // compression allows the Asset Classification Smart Contract (502Kish at the time of writing) to be uploaded
            // without server rejection.
            // Note: Future versions of wasmd (where this 500K check is made) support up to 800K, which should make this
            // compression irrelevant.
            .gzipAc()
            .also { bytes -> println("Successfully downloaded and compressed wasm bytes. Total size: [${bytes.size}]") }
    }

    private fun downloadAndInstantiateSmartContract(config: SetupACToolConfig): String {
        val wasmBytes = downloadWasmBytes(config)
        config.logger("Storing code on Provenance Blockchain using address [${config.contractAdminAccount.bech32Address}] as the contract admin")
        val codeId = config.pbClient.broadcastTxAc(
            messages = Tx.MsgStoreCode.newBuilder().also { storeCode ->
                storeCode.instantiatePermission = Types.AccessConfig.newBuilder().also { accessConfig ->
                    accessConfig.address = config.contractAdminAccount.bech32Address
                    accessConfig.permission = Types.AccessType.ACCESS_TYPE_ONLY_ADDRESS
                }.build()
                storeCode.sender = config.contractAdminAccount.bech32Address
                storeCode.wasmByteCode = wasmBytes.toByteString()
            }.build().wrapListAc(),
            account = config.contractAdminAccount,
            gasAdjustment = 1.1,
        ).getCodeIdAc()
        config.logger("Successfully stored WASM and got code id [$codeId]")
        config.logger("Instantiating asset classification smart contract at code id [$codeId]")
        val contractAddress = config.pbClient.broadcastTxAc(
            messages = Tx.MsgInstantiateContract.newBuilder().also { instantiate ->
                instantiate.admin = config.contractAdminAccount.bech32Address
                instantiate.codeId = codeId
                instantiate.label = "asset-classification"
                instantiate.sender = config.contractAdminAccount.bech32Address
                instantiate.msg = AssetClassificationContractInstantiate(
                    baseContractName = "asset",
                    bindBaseName = false,
                    assetDefinitions = emptyList(),
                    isTest = true,
                ).toBase64Msg(OBJECT_MAPPER)
            }.build().wrapListAc(),
            account = config.contractAdminAccount,
            gasAdjustment = 1.1,
        ).getContractAddressAc()
        config.logger("Successfully instantiated the asset classification smart contract with address [$contractAddress]")
        ACClient.getDefault(
            contractIdentifier = ContractIdentifier.Address(contractAddress),
            pbClient = config.pbClient,
        ).also { acClient ->
            config.contractAliasNames.map { alias ->
                config.logger("Generating restricted contract lookup alias [$alias] using contract admin address [${config.contractAdminAccount.bech32Address}]")
                acClient.generateBindContractAliasMsg(
                    execute = BindContractAliasExecute.new(alias),
                    signerAddress = config.contractAdminAccount.bech32Address,
                )
            }.let { aliasMessages ->
                config.logger("Binding all alias names in a single transaction using contract admin address [${config.contractAdminAccount.bech32Address}]")
                config.pbClient.broadcastTxAc(messages = aliasMessages, account = config.contractAdminAccount)
            }
        }
        return contractAddress
    }

    private fun setupAssetDefinitions(config: SetupACToolConfig, contractAddress: String) {
        val messages = AssetSpecifications.flatMap { specification ->
            val specType = specification.recordSpecConfigs.single().name
            config.logger("Generating create scope spec messages for type [$specType]")
            val messages = specification.specificationMsgs(config.contractAdminAccount.bech32Address).toMutableList()
            config.logger("Generating add asset definition message to asset classification contract for type [$specType]")
            messages += ACClient.getDefault(
                contractIdentifier = ContractIdentifier.Address(contractAddress),
                pbClient = config.pbClient,
            ).generateAddAssetDefinitionMsg(
                execute = AssetDefinitionExecute.withScopeSpecUuid(
                    scopeSpecUuid = specification.scopeSpecConfig.id,
                    assetType = specType,
                    verifiers = VerifierDetail.new(
                        address = config.verifierBech32Address,
                        onboardingCost = "100000".toBigDecimal(),
                        onboardingDenom = "nhash",
                        feePercent = BigDecimal.ZERO,
                        feeDestinations = emptyList(),
                        entityDetail = EntityDetail(
                            name = "Provenance Blockchain Verifier: $specType",
                            description = "The standard asset classification verifier provided by the Provenance Blockchain Foundation",
                            homeUrl = "https://provenance.io",
                            sourceUrl = "https://github.com/provenance-io/asset-classification-libs",
                        )
                    ).wrapListAc(),
                    bindName = false,
                ).toAdd(),
                signerAddress = config.contractAdminAccount.bech32Address,
            )
            config.logger("Generating bind name message of type [${specType}.asset] to contract address [$contractAddress] for future attribute writes")
            messages += MsgBindNameRequest.newBuilder().also { bindName ->
                bindName.parent = NameRecord.newBuilder().also { nameRecord ->
                    nameRecord.name = "asset"
                    nameRecord.address = config.assetNameAdminAccount.bech32Address
                }.build()
                bindName.record = NameRecord.newBuilder().also { nameRecord ->
                    nameRecord.name = specType
                    nameRecord.address = contractAddress
                    nameRecord.restricted = true
                }.build()
            }.build()
            messages
        }
        config.logger("Broadcasting all generated messages...")
        config.pbClient.estimateAndBroadcastTx(
            txBody = messages.map { it.toAny() }.toTxBody(),
            signers = listOf(
                BaseReqSigner(config.contractAdminAccount.toAccountSigner()),
                BaseReqSigner(config.assetNameAdminAccount.toAccountSigner()),
            ),
            mode = BroadcastMode.BROADCAST_MODE_BLOCK,
            gasAdjustment = 1.2,
        ).also { response ->
            if (response.isErrorAc()) {
                throw IllegalStateException("FAILED to fully create all scope specifications, add asset definitions, and bind asset names to the smart contract. Got raw log: ${response.txResponse.rawLog}")
            }
        }
    }
}

data class SetupACToolConfig(
    val pbClient: PbClient,
    // Your local Provenance Blockchain instance should have the "asset" root name created in its genesis file and the
    // account it is bound to should be known to the point where a ProvenanceAccountDetail can be established from its
    // private key or mnemonic details
    val assetNameAdminAccount: ProvenanceAccountDetail,
    val contractAdminAccount: ProvenanceAccountDetail,
    val verifierBech32Address: String,
    val contractAliasNames: List<String> = listOf("assetclassificationalias.pb", "testassets.pb"),
    // Logging is disabled by default.  To use a standard implementation, simply provide SetupACToolLogging.Println
    val logger: SetupACToolLogging = SetupACToolLogging.Disabled,
)

/**
 * Defines how logging should be handled for the underlying setup process.
 */
sealed interface SetupACToolLogging {
    val log: (message: String) -> Unit

    // Operator function override to allow instances to be used functionality, like SetupACToolLogging.Println("message")
    operator fun invoke(message: String) {
        log(message)
    }

    // Prints out all logs via std println function
    object Println : SetupACToolLogging {
        override val log: (message: String) -> Unit = { println(it) }
    }

    // Disregards all logs. Only exceptions will be displayed, if they occur.
    object Disabled : SetupACToolLogging {
        override val log: (message: String) -> Unit = {}
    }

    // Allows a custom logging process to be specified.  For instance, if using KotlinLogging, it might look something
    // like this:
    // SetupACToolLogging.Custom { message -> logger.info(message) }
    class Custom(override val log: (message: String) -> Unit) : SetupACToolLogging
}
