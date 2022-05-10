package io.provenance.classification.asset.client.domain.query

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.provenance.classification.asset.client.domain.model.AssetQualifier
import io.provenance.classification.asset.client.domain.query.base.ContractQuery

/**
 * This class is a reflection of the request body used in the Asset Classification smart contract's query asset
 * definition route.  It is internally utilized in the [ACQuerier][io.provenance.classification.asset.client.client.base.ACQuerier].
 *
 * @param queryAssetDefinition The body value that will be used for this request.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class QueryAssetDefinition(val queryAssetDefinition: QueryAssetDefinitionBody) : ContractQuery {
    @JsonIgnore
    override val queryFailureMessage: String = "Query asset definition: ${queryAssetDefinition.qualifier}"

    companion object {
        /**
         * Creates a query using an asset type as the asset qualifier.  This helper exists to simplify the syntax needed
         * to create a request.
         */
        fun byAssetType(assetType: String): QueryAssetDefinition = byQualifier(
            assetQualifier = AssetQualifier.AssetType(
                value = assetType,
            ),
        )

        /**
         * Creates a query using a scope spec address as the asset qualifier.  This helper exists to simplify the
         * syntax needed to create a request.
         */
        fun byScopeSpecAddress(scopeSpecAddress: String): QueryAssetDefinition = byQualifier(
            assetQualifier = AssetQualifier.ScopeSpecAddress(
                value = scopeSpecAddress,
            )
        )

        private fun byQualifier(assetQualifier: AssetQualifier): QueryAssetDefinition = QueryAssetDefinition(
            queryAssetDefinition = QueryAssetDefinitionBody(
                qualifier = assetQualifier,
            )
        )
    }
}

/**
 * The body inside the contract query payload.  This exists as a separate class to ensure that jackson correctly maps
 * the payload for the contract's format specifications.
 *
 * @param qualifier Qualifies the requested asset by type or scope spec address, which are both unique constraints for
 * each asset definition.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class QueryAssetDefinitionBody(val qualifier: AssetQualifier)
