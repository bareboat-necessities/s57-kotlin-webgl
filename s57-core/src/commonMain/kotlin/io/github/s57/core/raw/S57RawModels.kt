package io.github.s57.core.raw

import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value

/**
 * Phase 3 raw S-57 dataset decoded from ISO8211 records.
 *
 * Geometry reconstruction is intentionally not performed here.  This layer
 * identifies dataset metadata, feature records, vector records, object-class
 * codes/acronyms, raw attributes, and spatial/vector references.
 */
data class S57RawDataset(
    val metadata: S57DatasetMetadata,
    val features: List<S57RawFeatureRecord>,
    val vectors: List<S57RawVectorRecord>,
    val unknownRecords: List<S57RawRecord>
) {
    fun featureCountsByObjectClass(): Map<String, Int> = features
        .groupingBy { it.objectClassAcronym }
        .eachCount()

    fun toFeatureStubs(): List<S57Feature> = features.map { feature ->
        S57Feature(
            id = feature.id,
            objectClass = feature.objectClassAcronym,
            attributes = feature.attributes.associate { attr -> attr.acronym to S57Value.Text(attr.value) },
            geometry = S57Geometry.Empty
        )
    }

    fun summary(): S57CellSummary = S57CellSummary(
        cellId = metadata.cellName.ifBlank { "UNKNOWN" },
        name = metadata.cellName.ifBlank { "UNKNOWN" },
        edition = metadata.edition,
        updateNumber = metadata.updateNumber,
        featureCount = features.size
    )
}

data class S57DatasetMetadata(
    val cellName: String = "",
    val edition: Int? = null,
    val updateNumber: Int? = null,
    val issueDate: String? = null,
    val updateApplicationDate: String? = null,
    val productSpecification: String? = null,
    val coordinateMultiplier: Int? = null,
    val soundingMultiplier: Int? = null,
    val rawFields: Map<String, String> = emptyMap()
)

data class S57RawFeatureRecord(
    val id: Long,
    val recordName: S57RecordName,
    val primitive: S57Primitive,
    val group: Int,
    val objectClassCode: Int,
    val objectClassAcronym: String,
    val version: Int,
    val updateInstruction: S57UpdateInstruction,
    val featureObjectId: S57FeatureObjectIdentifier? = null,
    val attributes: List<S57RawAttribute> = emptyList(),
    val nationalAttributes: List<S57RawAttribute> = emptyList(),
    val spatialReferences: List<S57SpatialReference> = emptyList(),
    val rawFieldTags: Set<String> = emptySet()
)

data class S57RawVectorRecord(
    val id: Long,
    val recordName: S57RecordName,
    val version: Int,
    val updateInstruction: S57UpdateInstruction,
    val twoDimensionalCoordinateCount: Int = 0,
    val threeDimensionalCoordinateCount: Int = 0,
    val rawFieldTags: Set<String> = emptySet()
)

data class S57RawRecord(
    val index: Int,
    val tags: List<String>,
    val preview: String
)

data class S57RecordName(
    val recordName: Int,
    val recordId: Long
) {
    override fun toString(): String = "$recordName:$recordId"
}

data class S57FeatureObjectIdentifier(
    val agency: Int,
    val featureId: Long,
    val subdivision: Int
)

data class S57RawAttribute(
    val code: Int,
    val acronym: String,
    val value: String
)

data class S57SpatialReference(
    val name: S57RecordName,
    val orientation: Int,
    val usage: Int,
    val mask: Int
)

enum class S57Primitive(val code: Int) {
    Unknown(0),
    Point(1),
    Line(2),
    Area(3),
    None(255);

    companion object {
        fun fromCode(code: Int): S57Primitive = entries.firstOrNull { it.code == code } ?: Unknown
    }
}

enum class S57UpdateInstruction(val code: Int) {
    Unknown(0),
    Insert(1),
    Delete(2),
    Modify(3);

    companion object {
        fun fromCode(code: Int): S57UpdateInstruction = entries.firstOrNull { it.code == code } ?: Unknown
    }
}
