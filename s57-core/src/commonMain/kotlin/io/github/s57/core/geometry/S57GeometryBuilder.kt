package io.github.s57.core.geometry

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Dataset
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.raw.S57Primitive
import io.github.s57.core.raw.S57RawCoordinate
import io.github.s57.core.raw.S57RawDataset
import io.github.s57.core.raw.S57RawFeatureRecord
import io.github.s57.core.raw.S57RawVectorRecord
import io.github.s57.core.raw.S57SpatialReference

/**
 * Phase 4 geometry reconstruction from S-57 raw feature/vector records.
 *
 * This class deliberately stays small and deterministic: it resolves FSPT
 * feature-to-spatial references, applies simple orientation handling, scales
 * SG2D/SG3D raw coordinates using COMF/SOMF, and returns immutable geometry
 * objects.  It does not perform chart quilting, live viewport management, or
 * any WebGL rendering.
 */
class S57GeometryBuilder(
    private val defaultCoordinateMultiplier: Int = DEFAULT_COMF,
    private val defaultSoundingMultiplier: Int = DEFAULT_SOMF
) {
    fun build(raw: S57RawDataset): S57GeometryBuildResult {
        val vectorByName = raw.vectors.associateBy { it.recordName }
        val context = BuildContext(
            coordinateMultiplier = raw.metadata.coordinateMultiplier?.takeIf { it > 0 } ?: defaultCoordinateMultiplier,
            soundingMultiplier = raw.metadata.soundingMultiplier?.takeIf { it > 0 } ?: defaultSoundingMultiplier,
            vectorByName = vectorByName
        )
        val diagnostics = mutableListOf<S57GeometryDiagnostic>()
        val features = raw.features.map { feature ->
            val geometry = buildFeatureGeometry(feature, context, diagnostics)
            S57Feature(
                id = feature.id,
                objectClass = feature.objectClassAcronym,
                attributes = (feature.attributes + feature.nationalAttributes).associate { attr ->
                    attr.acronym to io.github.s57.core.S57Value.Text(attr.value)
                },
                geometry = geometry
            )
        }
        val bounds = mergeBounds(features.mapNotNull { it.bounds })
        val summary = S57CellSummary(
            cellId = raw.metadata.cellName.ifBlank { "UNKNOWN" },
            name = raw.metadata.cellName.ifBlank { "UNKNOWN" },
            edition = raw.metadata.edition,
            updateNumber = raw.metadata.updateNumber,
            bounds = bounds,
            featureCount = features.size
        )
        return S57GeometryBuildResult(
            dataset = S57Dataset(summary, features),
            diagnostics = diagnostics
        )
    }

    private fun buildFeatureGeometry(
        feature: S57RawFeatureRecord,
        context: BuildContext,
        diagnostics: MutableList<S57GeometryDiagnostic>
    ): S57Geometry {
        if (feature.spatialReferences.isEmpty()) {
            diagnostics += S57GeometryDiagnostic(feature.id, S57GeometryDiagnosticSeverity.Info, "Feature has no spatial references")
            return S57Geometry.Empty
        }
        val referenced = feature.spatialReferences.mapNotNull { ref ->
            val vector = context.vectorByName[ref.name]
            if (vector == null) {
                diagnostics += S57GeometryDiagnostic(feature.id, S57GeometryDiagnosticSeverity.Warning, "Missing vector ${ref.name}")
                null
            } else {
                ref to vector
            }
        }
        if (referenced.isEmpty()) return S57Geometry.Empty

        return when (feature.primitive) {
            S57Primitive.Point -> pointGeometry(referenced, context)
            S57Primitive.Line -> lineGeometry(referenced, context)
            S57Primitive.Area -> areaGeometry(referenced, context)
            S57Primitive.Unknown,
            S57Primitive.None -> lineOrPointFallback(referenced, context)
        }
    }

    private fun pointGeometry(referenced: List<Pair<S57SpatialReference, S57RawVectorRecord>>, context: BuildContext): S57Geometry {
        val points = referenced.flatMap { (_, vector) -> vector.coordinates.map { it.toGeoPoint(context) } }
        return when (points.size) {
            0 -> S57Geometry.Empty
            1 -> S57Geometry.Point(points.single())
            else -> S57Geometry.MultiPoint(points)
        }
    }

    private fun lineGeometry(referenced: List<Pair<S57SpatialReference, S57RawVectorRecord>>, context: BuildContext): S57Geometry {
        val points = concatenateSegments(referenced, context, closeRing = false)
        return if (points.size >= 2) S57Geometry.LineString(points) else pointGeometry(referenced, context)
    }

    private fun areaGeometry(referenced: List<Pair<S57SpatialReference, S57RawVectorRecord>>, context: BuildContext): S57Geometry {
        val ring = concatenateSegments(referenced, context, closeRing = true)
        return if (ring.size >= 4) S57Geometry.Polygon(listOf(ring)) else lineGeometry(referenced, context)
    }

    private fun lineOrPointFallback(referenced: List<Pair<S57SpatialReference, S57RawVectorRecord>>, context: BuildContext): S57Geometry {
        val points = concatenateSegments(referenced, context, closeRing = false)
        return when {
            points.size >= 2 -> S57Geometry.LineString(points)
            points.size == 1 -> S57Geometry.Point(points.single())
            else -> S57Geometry.Empty
        }
    }

    private fun concatenateSegments(
        referenced: List<Pair<S57SpatialReference, S57RawVectorRecord>>,
        context: BuildContext,
        closeRing: Boolean
    ): List<GeoPoint> {
        val out = mutableListOf<GeoPoint>()
        for ((ref, vector) in referenced) {
            val segment = vector.coordinates.map { it.toGeoPoint(context) }.oriented(ref.orientation)
            for (point in segment) {
                if (out.lastOrNull() != point) out += point
            }
        }
        if (closeRing && out.size >= 3 && out.first() != out.last()) out += out.first()
        return out
    }

    private fun List<GeoPoint>.oriented(orientation: Int): List<GeoPoint> =
        if (orientation == ORIENTATION_REVERSE) asReversed() else this

    private fun S57RawCoordinate.toGeoPoint(context: BuildContext): GeoPoint = GeoPoint(
        lon = xRaw.toDouble() / context.coordinateMultiplier.toDouble(),
        lat = yRaw.toDouble() / context.coordinateMultiplier.toDouble()
    )

    private fun mergeBounds(bounds: List<GeoBounds>): GeoBounds? {
        if (bounds.isEmpty()) return null
        var minLon = bounds.first().minLon
        var minLat = bounds.first().minLat
        var maxLon = bounds.first().maxLon
        var maxLat = bounds.first().maxLat
        for (box in bounds.drop(1)) {
            minLon = kotlin.math.min(minLon, box.minLon)
            minLat = kotlin.math.min(minLat, box.minLat)
            maxLon = kotlin.math.max(maxLon, box.maxLon)
            maxLat = kotlin.math.max(maxLat, box.maxLat)
        }
        return GeoBounds(minLon, minLat, maxLon, maxLat)
    }

    private data class BuildContext(
        val coordinateMultiplier: Int,
        val soundingMultiplier: Int,
        val vectorByName: Map<io.github.s57.core.raw.S57RecordName, S57RawVectorRecord>
    )

    companion object {
        const val DEFAULT_COMF: Int = 10_000_000
        const val DEFAULT_SOMF: Int = 10
        const val ORIENTATION_REVERSE: Int = 2
    }
}

data class S57GeometryBuildResult(
    val dataset: S57Dataset,
    val diagnostics: List<S57GeometryDiagnostic>
) {
    val features: List<S57Feature> get() = dataset.features

    fun report(): S57GeometryReport = S57GeometryReport.from(dataset, diagnostics)
}

data class S57GeometryDiagnostic(
    val featureId: Long,
    val severity: S57GeometryDiagnosticSeverity,
    val message: String
)

enum class S57GeometryDiagnosticSeverity {
    Info,
    Warning,
    Error
}

data class S57GeometryReport(
    val featureCount: Int,
    val pointCount: Int,
    val multiPointCount: Int,
    val lineCount: Int,
    val polygonCount: Int,
    val emptyCount: Int,
    val bounds: GeoBounds?,
    val diagnostics: List<S57GeometryDiagnostic>
) {
    fun toPlainText(): String = buildString {
        appendLine("S-57 geometry report features=$featureCount point=$pointCount multipoint=$multiPointCount line=$lineCount polygon=$polygonCount empty=$emptyCount")
        appendLine("bounds=${bounds ?: "none"}")
        diagnostics.take(32).forEach { appendLine("${it.severity}: feature=${it.featureId} ${it.message}") }
    }

    companion object {
        fun from(dataset: S57Dataset, diagnostics: List<S57GeometryDiagnostic>): S57GeometryReport {
            var points = 0
            var multiPoints = 0
            var lines = 0
            var polygons = 0
            var empty = 0
            dataset.features.forEach { feature ->
                when (feature.geometry) {
                    S57Geometry.Empty -> empty++
                    is S57Geometry.Point -> points++
                    is S57Geometry.MultiPoint -> multiPoints++
                    is S57Geometry.LineString -> lines++
                    is S57Geometry.Polygon -> polygons++
                    is S57Geometry.MultiPolygon -> polygons++
                }
            }
            return S57GeometryReport(
                featureCount = dataset.features.size,
                pointCount = points,
                multiPointCount = multiPoints,
                lineCount = lines,
                polygonCount = polygons,
                emptyCount = empty,
                bounds = dataset.summary.bounds,
                diagnostics = diagnostics
            )
        }
    }
}
