package io.provenance.classification.asset.verifier.provenance

enum class ACContractEvent(val contractName: String) {
    ADD_ASSET_DEFINITION("add_asset_definition"),
    ADD_ASSET_VERIFIER("add_asset_verifier"),
    BIND_CONTRACT_ALIAS("bind_contract_alias"),
    DELETE_ASSET_DEFINITION("delete_asset_definition"),
    INSTANTIATE_CONTRACT("instantiate_contract"),
    MIGRATE_CONTRACT("migrate_contract"),
    ONBOARD_ASSET("onboard_asset"),
    TOGGLE_ASSET_DEFINITION("toggle_asset_definition"),
    UPDATE_ACCESS_ROUTES("update_access_routes"),
    UPDATE_ASSET_DEFINITION("update_asset_definition"),
    UPDATE_ASSET_VERIFIER("update_asset_verifier"),
    VERIFY_ASSET("verify_asset"),
    ;

    companion object {
        private val CONTRACT_NAME_MAP: Map<String, ACContractEvent> by lazy { values().associateBy { it.contractName } }

        fun forContractName(name: String): ACContractEvent = CONTRACT_NAME_MAP[name] ?: throw IllegalArgumentException("Unknown ContractEvent variant [$name]")
    }
}
