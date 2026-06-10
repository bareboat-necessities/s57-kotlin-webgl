package io.github.s57.core.geometry

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Dataset
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value
import io.github.s57.core.raw.S57Primitive
import io.github.s57.core.raw.S57RawCoordinate
import io.github.s57.core.raw.S57RawDataset
import io.github.s57.core.raw.S57RawFeatureRecord
import io.github.s57.core.raw.S57RawVectorRecord
import io.github.s57.core.raw.S57SpatialReference

/**
 * Phase 17 geometry reconstruction from S-57 raw feature/vector records.
 *
 * This resolves FSPT feature references, applies COMF/SOMF scaling, honors edge
 * orientation, expands VRPT connected-node endpoints, and stitches multi-edge
 * area rings where possible.
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
            val attrs = (feature.attributes + feature.nationalAttributes).associate { attr ->
                attr.acronym to S57Value.Text(attr.value)
            } + feature.soundingAttribute(context)
            S57Feature(
                id = feature.id,
                objectClass = feature.objectClassAcronym,
                attributes = attrs,
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

        return when (feature.effectivePrimitive(referenced, context, diagnostics)) {
            S57Primitive.Point -> pointGeometry(referenced, context)
            S57Primitive.Line -> lineGeometry(referenced, context)
            S57Primitive.Area -> areaGeometry(feature.id, referenced, context, diagnostics)
            S57Primitive.Unknown,
            S57Primitive.None -> lineOrPointFallback(referenced, context)
        }
    }

    private fun S57RawFeatureRecord.effectivePrimitive(
        referenced: List<Pair<S57SpatialReference, S57RawVectorRecord>>,
        context: BuildContext,
        diagnostics: MutableList<S57GeometryDiagnostic>
    ): S57Primitive {
        val hasEdgeReference = referenced.any { (_, vector) -> vector.recordName.recordName == EDGE_RECORD_NAME }
        if (!hasEdgeReference) return primitive

        val shouldInfer = primitive == S57Primitive.Point || primitive == S57Primitive.Unknown || primitive == S57Primitive.None
        if (!shouldInfer) return primitive

        val inferred = if (objectClassAcronym in EDGE_AREA_OBJECT_CLASSES || referenced.formsClosedEdgeChain(context)) {
            S57Primitive.Area
        } else {
            S57Primitive.Line
        }
        diagnostics += S57GeometryDiagnostic(
            id,
            S57GeometryDiagnosticSeverity.Warning,
            "Corrected primitive ${primitive.name} to ${inferred.name} because feature references edge vector(s)"
        )
        return inferred
    }

    private fun List<Pair<S57SpatialReference, S57RawVectorRecord>>.formsClosedEdgeChain(context: BuildContext): Boolean {
        val segments = map { (ref, vector) -> vector.segmentPoints(context).oriented(ref.orientation) }
        return stitchSegments(segments, closeRing = false).any { chain ->
            chain.size >= 4 && chain.first() == chain.last()
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
        val points = stitchSegments(referenced.map { (ref, vector) -> vector.segmentPoints(context).oriented(ref.orientation) }, closeRing = false).flattenJoined()
        return if (points.size >= 2) S57Geometry.LineString(points) else pointGeometry(referenced, context)
    }

    private fun areaGeometry(
        featureId: Long,
        referenced: List<Pair<S57SpatialReference, S57RawVectorRecord>>,
        context: BuildContext,
        diagnostics: MutableList<S57GeometryDiagnostic>
    ): S57Geometry {
        val segments = referenced.map { (ref, vector) -> vector.segmentPoints(context).oriented(ref.orientation) }
        val rings = stitchSegments(segments, closeRing = true).filter { it.size >= 4 && it.first() == it.last() }
        if (rings.isEmpty()) {
            diagnostics += S57GeometryDiagnostic(featureId, S57GeometryDiagnosticSeverity.Warning, "Could not assemble closed area ring")
            return lineGeometry(referenced, context)
        }
        val openCount = segments.size - rings.size
        if (openCount > 0) diagnostics += S57GeometryDiagnostic(featureId, S57GeometryDiagnosticSeverity.Info, "Assembled ${rings.size} ring(s) from ${segments.size} edge segment(s)")
        return S57Geometry.Polygon(rings)
    }

    private fun lineOrPointFallback(referenced: List<Pair<S57SpatialReference, S57RawVectorRecord>>, context: BuildContext): S57Geometry {
        val points = stitchSegments(referenced.map { (ref, vector) -> vector.segmentPoints(context).oriented(ref.orientation) }, closeRing = false).flattenJoined()
        return when {
            points.size >= 2 -> S57Geometry.LineString(points)
            points.size == 1 -> S57Geometry.Point(points.single())
            else -> S57Geometry.Empty
        }
    }

    private fun S57RawVectorRecord.segmentPoints(context: BuildContext): List<GeoPoint> {
        val own = coordinates.map { it.toGeoPoint(context) }
        if (vectorReferences.isEmpty()) return own
        val nodePoints = vectorReferences.mapNotNull { ref -> context.vectorByName[ref.name]?.coordinates?.firstOrNull()?.toGeoPoint(context) }
        if (nodePoints.isEmpty()) return own
        val first = nodePoints.first()
        val last = nodePoints.last()
        return buildList {
            add(first)
            own.forEach { point -> if (lastOrNull() != point) add(point) }
            if (lastOrNull() != last) add(last)
        }
    }

    private fun stitchSegments(segments: List<List<GeoPoint>>, closeRing: Boolean): List<List<GeoPoint>> {
        val remaining = segments.filter { it.isNotEmpty() }.map { it.toMutableList() }.toMutableList()
        val chains = mutableListOf<List<GeoPoint>>()
        while (remaining.isNotEmpty()) {
            val chain = remaining.removeAt(0).toMutableList()
            var changed = true
            while (changed) {
                changed = false
                val i = remaining.indexOfFirst { it.first() == chain.last() || it.last() == chain.last() || it.last() == chain.first() || it.first() == chain.first() }
                if (i >= 0) {
                    val next = remaining.removeAt(i)
                    when {
                        next.first() == chain.last() -> chain.addWithoutDuplicate(next)
                        next.last() == chain.last() -> chain.addWithoutDuplicate(next.asReversed())
                        next.last() == chain.first() -> chain.prependWithoutDuplicate(next)
                        next.first() == chain.first() -> chain.prependWithoutDuplicate(next.asReversed())
                    }
                    changed = true
                }
            }
            if (closeRing && chain.size >= 3 && chain.first() != chain.last()) chain += chain.first()
            chains += chain
        }
        return chains
    }

    private fun MutableList<GeoPoint>.addWithoutDuplicate(points: List<GeoPoint>) {
        points.forEach { point -> if (lastOrNull() != point) add(point) }
    }

    private fun MutableList<GeoPoint>.prependWithoutDuplicate(points: List<GeoPoint>) {
        val copy = points.toMutableList()
        if (copy.lastOrNull() == firstOrNull()) copy.removeAt(copy.lastIndex)
        addAll(0, copy)
    }

    private fun List<List<GeoPoint>>.flattenJoined(): List<GeoPoint> {
        val out = mutableListOf<GeoPoint>()
        for (chain in this) chain.forEach { point -> if (out.lastOrNull() != point) out += point }
        return out
    }

    private fun List<GeoPoint>.oriented(orientation: Int): List<GeoPoint> =
        if (orientation == ORIENTATION_REVERSE) asReversed() else this

    private fun S57RawCoordinate.toGeoPoint(context: BuildContext): GeoPoint = GeoPoint(
        lon = xRaw.toDouble() / context.coordinateMultiplier.toDouble(),
        lat = yRaw.toDouble() / context.coordinateMultiplier.toDouble()
    )

    private fun S57RawFeatureRecord.soundingAttribute(context: BuildContext): Map<String, S57Value> {
        if (objectClassAcronym != "SOUNDG") return emptyMap()
        val values = spatialReferences.flatMap { ref -> context.vectorByName[ref.name]?.coordinates.orEmpty() }
            .mapNotNull { it.zRaw }
            .map { raw -> S57Value.Decimal(raw.toDouble() / context.soundingMultiplier.toDouble()) }
        return when (values.size) {
            0 -> emptyMap()
            1 -> mapOf("VALSOU" to values.single())
            else -> mapOf("VALSOU" to S57Value.ListValue(values))
        }
    }

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
        private const val EDGE_RECORD_NAME: Int = 130

        private val EDGE_AREA_OBJECT_CLASSES: Set<String> = setOf(
            "ACHARE", "ADMARE", "AIRARE", "CBLARE", "CONZNE", "COSARE", "CTNARE",
            "DEPARE", "DMPGRD", "DOCARE", "DRGARE", "DWRTCL", "EXEZNE", "FAIRWY",
            "FSHGRD", "FSHZNE", "HRBARE", "ICNARE", "ISTZNE", "LAKARE", "LNDARE",
            "MIPARE", "OSPARE", "PIPARE", "PRCARE", "PRDARE", "RESARE", "SBDARE",
            "SEAARE", "SPLARE", "TESARE", "TSEZNE", "TSSCRS", "TSSRON", "TSSLPT",
            "TSSBND", "UNSARE"
        )
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
