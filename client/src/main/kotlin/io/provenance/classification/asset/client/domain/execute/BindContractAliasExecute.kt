package io.provenance.classification.asset.client.domain.execute

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.provenance.classification.asset.client.domain.execute.base.ContractExecute

/**
 * This class is a reflection of the request body used in the Asset Classification smart contract's bind contract alias
 * execution route.  It causes the contract to bind the supplied name value to itself, allowing the contract's address
 * to be resolved via the name module.  This should be used with an unrestricted parent name.  A restricted parent name
 * binding will always require that the address that owns the parent address make the binding itself.
 *
 * Sample usage:
 * ```kotlin
 * val bindExecute = BindContractAliasExecute.new("samplealias.pb")
 * val txResponse = acClient.bindContractAlias(bindExecute, signer, options)
 * ```
 *
 * @param bindContractAlias The body value that will be used for this request.  Translates to inner json for the contract's
 *                          execution request.
 */
@JsonNaming(SnakeCaseStrategy::class)
class BindContractAliasExecute internal constructor(val bindContractAlias: BindContractAliasBody) : ContractExecute {
    companion object {
        fun new(aliasName: String): BindContractAliasExecute = BindContractAliasExecute(
            bindContractAlias = BindContractAliasBody(aliasName = aliasName),
        )
    }
}

/**
 * The body inside the contract execution payload.  This exists as a separate class to ensure that jackson correctly
 * maps the payload for the contract's format specifications.
 *
 * @param aliasName The fully-qualified name to bind to the contract.  Must be a dot-separated name with a name qualifier
 *                  and a root name (or a chain from an existing branch from a root name that is unrestricted, ex: test.alias.pb).
 */
@JsonNaming(SnakeCaseStrategy::class)
data class BindContractAliasBody(val aliasName: String)
