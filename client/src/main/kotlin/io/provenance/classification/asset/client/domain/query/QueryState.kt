package io.provenance.classification.asset.client.domain.query

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.provenance.classification.asset.client.domain.query.base.ContractQuery
import io.provenance.classification.asset.client.domain.query.base.EmptyQueryBody

/**
 * This class is a reflection of the request body used in the Asset Classification smart contract's query state route.
 * As it has an empty body, it is not required as an input parameter for the default client implementation.
 */
@JsonNaming(SnakeCaseStrategy::class)
data class QueryState(val queryState: EmptyQueryBody = EmptyQueryBody()) : ContractQuery {
    @JsonIgnore
    override val queryFailureMessage: String = "Query contract state"
}
