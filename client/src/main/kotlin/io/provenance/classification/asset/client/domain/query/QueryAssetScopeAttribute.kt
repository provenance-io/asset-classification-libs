package io.provenance.classification.asset.client.domain.query

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.provenance.classification.asset.client.domain.model.AssetIdentifier
import io.provenance.classification.asset.client.domain.query.base.ContractQuery
import java.util.UUID

/**
 * This class is a reflection of the request body used in the Asset Classification smart contract's query asset scope
 * attribute route.  It is internally utilized in the ACQuerier.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class QueryAssetScopeAttribute<T>(val queryAssetScopeAttribute: QueryAssetScopeAttributeBody<T>) : ContractQuery {
    @JsonIgnore
    override val queryFailureMessage: String = "Query asset scope attribute by ${queryAssetScopeAttribute.identifier}"

    companion object {
        /**
         * Creates a query using an asset uuid as the asset identifier.  This helper exists to simplify the syntax
         * needed to create a request.
         */
        fun byAssetUuid(assetUuid: UUID): QueryAssetScopeAttribute<UUID> = byIdentifier(
            assetIdentifier = AssetIdentifier.AssetUuid(
                assetUuid
            ),
        )

        /**
         * Creates a query using a scope address as the asset identifier.  This helper exists to simplify the syntax
         * needed to create a request.
         */
        fun byScopeAddress(scopeAddress: String): QueryAssetScopeAttribute<String> = byIdentifier(
            assetIdentifier = AssetIdentifier.ScopeAddress(
                scopeAddress
            ),
        )

        private fun <T> byIdentifier(assetIdentifier: AssetIdentifier<T>): QueryAssetScopeAttribute<T> =
            QueryAssetScopeAttribute(
                queryAssetScopeAttribute = QueryAssetScopeAttributeBody(
                    identifier = assetIdentifier,
                )
            )
    }
}

/**
 * The body inside the contract query payload.  This exists as a separate class to ensure that jackson correctly maps
 * the payload for the contract's format specifications.
 *
 * @param identifier Identifiers the asset by uuid or scope address, which are both interchangeable within the smart
 * contract.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class QueryAssetScopeAttributeBody<T>(val identifier: AssetIdentifier<T>)
