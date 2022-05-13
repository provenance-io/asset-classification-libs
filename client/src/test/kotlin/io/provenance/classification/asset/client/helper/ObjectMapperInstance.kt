package io.provenance.classification.asset.client.helper

import io.provenance.classification.asset.util.objects.ACObjectMapperUtil

internal val OBJECT_MAPPER by lazy { ACObjectMapperUtil.getObjectMapper() }
