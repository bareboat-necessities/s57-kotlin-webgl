package io.github.s57.adapter

import io.github.s57.core.S57Feature

/**
 * Phase 0 adapter boundary.
 *
 * Phase 5 will replace this neutral DTO with direct construction of s52-kotlin-webgl input features.
 */
class S57ToS52Adapter {
    fun adapt(feature: S57Feature): S52AdapterFeature = S52AdapterFeature(
        id = feature.id,
        objectClassAcronym = feature.objectClass,
        attributes = feature.attributes.keys.sorted(),
        geometryType = feature.geometry::class.simpleName ?: "Unknown"
    )
}

data class S52AdapterFeature(
    val id: Long,
    val objectClassAcronym: String,
    val attributes: List<String>,
    val geometryType: String
)
