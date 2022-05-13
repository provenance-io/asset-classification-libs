package io.provenance.classification.asset.util.models

data class ProvenanceTxEvent(val type: String, val attributes: List<ProvenanceTxEventAttribute>)
