package io.github.s57.index

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Dataset
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value

/** Stored chart-cell metadata in the Phase 5 index layer. */
data class StoredS57Cell(
    val summary: S57CellSummary,
    val importedAtEpochMillis: Long = 0L
)

/** Stored feature metadata and geometry payload. */
data class StoredS57Feature(
    val cellId: String,
    val featureId: Long,
    val objectClass: String,
    val attributes: Map<String, S57Value>,
    val geometry: S57Geometry,
    val bounds: GeoBounds?
) {
    fun toFeature(): S57Feature = S57Feature(
        id = featureId,
        objectClass = objectClass,
        attributes = attributes,
        geometry = geometry
    )

    companion object {
        fun from(cellId: String, feature: S57Feature): StoredS57Feature = StoredS57Feature(
            cellId = cellId,
            featureId = feature.id,
            objectClass = feature.objectClass,
            attributes = feature.attributes,
            geometry = feature.geometry,
            bounds = feature.bounds
        )
    }
}

data class SpatialBinId(
    val cellId: String,
    val x: Int,
    val y: Int
) {
    override fun toString(): String = "$cellId/$x/$y"
}

data class SpatialBinConfig(
    val lonStepDegrees: Double = 0.01,
    val latStepDegrees: Double = 0.01
) {
    init {
        require(lonStepDegrees > 0.0) { "lonStepDegrees must be > 0" }
        require(latStepDegrees > 0.0) { "latStepDegrees must be > 0" }
    }
}

data class SpatialBinEntry(
    val id: SpatialBinId,
    val featureIds: Set<Long>
)

data class S57IndexImportReport(
    val cellId: String,
    val featureCount: Int,
    val indexedFeatureCount: Int,
    val emptyGeometryCount: Int,
    val binCount: Int
) {
    fun toPlainText(): String =
        "S-57 index import cell=$cellId features=$featureCount indexed=$indexedFeatureCount empty=$emptyGeometryCount bins=$binCount"
}

data class S57IndexStats(
    val cellCount: Int,
    val featureCount: Int,
    val binCount: Int
)

data class S57FeatureQuery(
    val cellId: String,
    val bounds: GeoBounds,
    val objectClasses: Set<String> = emptySet(),
    val limit: Int = Int.MAX_VALUE
) {
    init {
        require(limit > 0) { "limit must be > 0" }
    }
}

/** Deterministic fixed-grid spatial bin index. Small first, easy to debug. */
class S57SpatialBinIndex(
    private val config: SpatialBinConfig = SpatialBinConfig()
) {
    fun binsForBounds(cellId: String, bounds: GeoBounds): Set<SpatialBinId> {
        val minX = floorDiv(bounds.minLon, config.lonStepDegrees)
        val maxX = floorDiv(bounds.maxLon, config.lonStepDegrees)
        val minY = floorDiv(bounds.minLat, config.latStepDegrees)
        val maxY = floorDiv(bounds.maxLat, config.latStepDegrees)
        val result = linkedSetOf<SpatialBinId>()
        for (x in minX..maxX) for (y in minY..maxY) result += SpatialBinId(cellId, x, y)
        return result
    }

    fun indexFeatures(cellId: String, features: List<StoredS57Feature>): Map<SpatialBinId, Set<Long>> {
        val bins = linkedMapOf<SpatialBinId, MutableSet<Long>>()
        for (feature in features) {
            val bounds = feature.bounds ?: continue
            for (bin in binsForBounds(cellId, bounds)) {
                bins.getOrPut(bin) { linkedSetOf() } += feature.featureId
            }
        }
        return bins.mapValues { it.value.toSet() }
    }

    private fun floorDiv(value: Double, step: Double): Int = kotlin.math.floor(value / step).toInt()
}

fun S57Dataset.toStoredFeatures(): List<StoredS57Feature> = features.map { StoredS57Feature.from(summary.cellId, it) }

fun S57Geometry.pointCount(): Int = when (this) {
    S57Geometry.Empty -> 0
    is S57Geometry.Point -> 1
    is S57Geometry.MultiPoint -> points.size
    is S57Geometry.LineString -> points.size
    is S57Geometry.Polygon -> rings.sumOf { it.size }
    is S57Geometry.MultiPolygon -> polygons.sumOf { polygon -> polygon.rings.sumOf { it.size } }
}

fun GeoBounds.center(): GeoPoint = GeoPoint((minLon + maxLon) / 2.0, (minLat + maxLat) / 2.0)
