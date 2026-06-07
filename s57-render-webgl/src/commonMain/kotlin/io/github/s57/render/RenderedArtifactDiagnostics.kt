package io.github.s57.render

/**
 * Renderer-independent artifact diagnostics for CI and browser debug panels.
 *
 * Phase 8 deliberately checks the prepared StaticChartFrame before any browser
 * WebGL state is involved. This catches empty frames, missing geometry, missing
 * center-crosshair results, and depth-mesh plumbing regressions early.
 */
data class RenderedArtifactReport(
    val widthPx: Int,
    val heightPx: Int,
    val featureCount: Int,
    val visibleFeatureCount: Int,
    val pointFeatureCount: Int,
    val lineFeatureCount: Int,
    val polygonFeatureCount: Int,
    val emptyGeometryCount: Int,
    val centerCrosshairHitCount: Int,
    val depthMeshVertexCount: Int,
    val depthMeshTriangleCount: Int,
    val fallbackPlaceholderCount: Int = 0
) {
    val hasVisibleGeometry: Boolean get() = visibleFeatureCount > 0
    val hasDepthMesh: Boolean get() = depthMeshVertexCount > 0 && depthMeshTriangleCount > 0

    fun validateMinimum(
        minVisibleFeatures: Int = 1,
        maxFallbackPlaceholders: Int = 0
    ) {
        require(widthPx > 0 && heightPx > 0) { "Rendered artifact has invalid dimensions ${widthPx}x$heightPx" }
        require(visibleFeatureCount >= minVisibleFeatures) {
            "Rendered artifact has too few visible features: visible=$visibleFeatureCount required=$minVisibleFeatures total=$featureCount"
        }
        require(fallbackPlaceholderCount <= maxFallbackPlaceholders) {
            "Rendered artifact has too many fallback placeholders: fallback=$fallbackPlaceholderCount allowed=$maxFallbackPlaceholders"
        }
    }

    fun toPlainText(): String =
        "renderedArtifact ${widthPx}x$heightPx features=$featureCount visible=$visibleFeatureCount " +
            "points=$pointFeatureCount lines=$lineFeatureCount polygons=$polygonFeatureCount empty=$emptyGeometryCount " +
            "centerHits=$centerCrosshairHitCount depthVertices=$depthMeshVertexCount depthTriangles=$depthMeshTriangleCount " +
            "fallback=$fallbackPlaceholderCount"
}

object RenderedArtifactDiagnostics {
    fun analyze(frame: StaticChartFrame): RenderedArtifactReport {
        val geometries = frame.projectedFeatures.map { it.geometry }
        return RenderedArtifactReport(
            widthPx = frame.request.widthPx,
            heightPx = frame.request.heightPx,
            featureCount = frame.projectedFeatures.size,
            visibleFeatureCount = geometries.count { it.points().isNotEmpty() },
            pointFeatureCount = geometries.count { it is ProjectedGeometry.Point || it is ProjectedGeometry.MultiPoint },
            lineFeatureCount = geometries.count { it is ProjectedGeometry.LineString },
            polygonFeatureCount = geometries.count { it is ProjectedGeometry.Polygon || it is ProjectedGeometry.MultiPolygon },
            emptyGeometryCount = geometries.count { it is ProjectedGeometry.Empty || it.points().isEmpty() },
            centerCrosshairHitCount = frame.centerCrosshairHits.size,
            depthMeshVertexCount = frame.depthMesh?.vertices?.size ?: 0,
            depthMeshTriangleCount = (frame.depthMesh?.triangleIndices?.size ?: 0) / 3,
            fallbackPlaceholderCount = frame.projectedFeatures.count { it.displayName.contains("fallback", ignoreCase = true) || it.objectClass.equals("FALLBACK", ignoreCase = true) }
        )
    }

    fun toSvgSnapshot(frame: StaticChartFrame, includeLabels: Boolean = false): String = buildString {
        val width = frame.request.widthPx.coerceAtLeast(1)
        val height = frame.request.heightPx.coerceAtLeast(1)
        appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\" role=\"img\" aria-label=\"s57 rendered artifact snapshot\">")
        appendLine("  <rect width=\"100%\" height=\"100%\" fill=\"#dceefa\"/>")
        for (feature in frame.projectedFeatures) appendFeature(feature, includeLabels)
        if (frame.request.centerCrosshair.enabled) {
            val cx = width / 2.0
            val cy = height / 2.0
            val size = frame.request.centerCrosshair.sizePx
            appendLine("  <line x1=\"${cx - size}\" y1=\"$cy\" x2=\"${cx + size}\" y2=\"$cy\" stroke=\"#111111\" stroke-width=\"1\"/>")
            appendLine("  <line x1=\"$cx\" y1=\"${cy - size}\" x2=\"$cx\" y2=\"${cy + size}\" stroke=\"#111111\" stroke-width=\"1\"/>")
        }
        appendLine("</svg>")
    }

    private fun StringBuilder.appendFeature(feature: ProjectedFeature, includeLabels: Boolean) {
        when (val geometry = feature.geometry) {
            ProjectedGeometry.Empty -> Unit
            is ProjectedGeometry.Point -> appendPoint(geometry.point, feature)
            is ProjectedGeometry.MultiPoint -> geometry.points.forEach { appendPoint(it, feature) }
            is ProjectedGeometry.LineString -> appendPolyline(geometry.points, feature)
            is ProjectedGeometry.Polygon -> appendPolygon(geometry.rings, feature)
            is ProjectedGeometry.MultiPolygon -> geometry.polygons.forEach { appendPolygon(it.rings, feature) }
        }
        if (includeLabels) {
            feature.screenBounds?.let { bounds ->
                appendLine("  <text x=\"${bounds.minX}\" y=\"${bounds.minY - 2.0}\" font-family=\"monospace\" font-size=\"10\" fill=\"#222222\">${xml(feature.objectClass)}</text>")
            }
        }
    }

    private fun StringBuilder.appendPoint(point: ScreenPoint, feature: ProjectedFeature) {
        appendLine("  <circle cx=\"${point.x}\" cy=\"${point.y}\" r=\"4\" fill=\"${colorFor(feature.objectClass)}\" stroke=\"#000000\" stroke-width=\"0.5\"/>")
    }

    private fun StringBuilder.appendPolyline(points: List<ScreenPoint>, feature: ProjectedFeature) {
        if (points.size < 2) return
        appendLine("  <polyline points=\"${points.toSvgPoints()}\" fill=\"none\" stroke=\"${colorFor(feature.objectClass)}\" stroke-width=\"2\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>")
    }

    private fun StringBuilder.appendPolygon(rings: List<List<ScreenPoint>>, feature: ProjectedFeature) {
        val outer = rings.firstOrNull().orEmpty()
        if (outer.size < 3) return
        appendLine("  <polygon points=\"${outer.toSvgPoints()}\" fill=\"${fillFor(feature.objectClass)}\" stroke=\"${colorFor(feature.objectClass)}\" stroke-width=\"1\"/>")
        for (hole in rings.drop(1)) appendPolyline(hole, feature)
    }

    private fun List<ScreenPoint>.toSvgPoints(): String = joinToString(" ") { "${it.x},${it.y}" }

    private fun colorFor(objectClass: String): String = when (objectClass.uppercase()) {
        "DEPCNT" -> "#0040a0"
        "SOUNDG" -> "#000000"
        "BOYLAT", "BCNLAT", "LIGHTS" -> "#d02020"
        "WRECKS", "OBSTRN" -> "#5f2020"
        else -> "#14303d"
    }

    private fun fillFor(objectClass: String): String = when (objectClass.uppercase()) {
        "DEPARE" -> "#b2e0f4"
        else -> "#c8d8c8"
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
