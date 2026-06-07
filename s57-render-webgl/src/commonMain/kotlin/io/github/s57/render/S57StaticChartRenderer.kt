package io.github.s57.render

import io.github.s52.core.draw.S52DrawCommand
import io.github.s52.core.draw.S52DrawCommandTranscript
import io.github.s52.core.engine.S52PortrayalEngine
import io.github.s52.core.settings.MarinerSettings
import io.github.s52.core.settings.PortrayalContext
import io.github.s57.adapter.S57ToS52Adapter
import io.github.s57.index.S57FeatureQuery
import io.github.s57.index.S57IndexStore

/**
 * Phase 7 static chart render pipeline.
 *
 * It queries the Phase 5 index, adapts the result through the Phase 6 S-52
 * bridge, projects features to a fixed screen, and exposes a frame that the JS
 * WebGL shell can draw. It intentionally does not implement chartplotter UX such
 * as continuous panning, quilting, AIS, NMEA, or ownship state.
 */
class S57StaticChartRenderer(
    private val indexStore: S57IndexStore,
    private val adapter: S57ToS52Adapter = S57ToS52Adapter(),
    private val depthMeshBuilder: DepthMeshBuilder = EmptyDepthMeshBuilder()
) {
    fun prepareFrame(request: ChartRenderRequest): StaticChartFrame {
        val features = indexStore.queryFeatures(S57FeatureQuery(request.cellId, request.bounds))
        val adapted = adapter.adaptFeatures(features)
        val projection = ChartProjection.from(request)
        val projected = features.mapNotNull { feature ->
            val projectedGeometry = feature.geometry.project(projection)
            val points = projectedGeometry.points()
            val screenBounds = ScreenBounds.from(points)
            if (points.isEmpty() || screenBounds == null) {
                null
            } else {
                ProjectedFeature(
                    featureId = feature.id,
                    objectClass = feature.objectClass,
                    displayName = feature.attributes["OBJNAM"]?.toString()?.substringAfter("(")?.substringBeforeLast(")") ?: feature.objectClass,
                    geometry = projectedGeometry,
                    geoBounds = feature.bounds,
                    screenBounds = screenBounds,
                    feature = feature
                )
            }
        }
        val depthMesh = if (request.depthMesh.enabled || request.renderMode == ChartRenderMode.DepthMesh3D) {
            depthMeshBuilder.build(features.extractDepthSamples(), request)
        } else {
            null
        }
        val frameWithoutHits = StaticChartFrame(
            request = request,
            queriedFeatureCount = features.size,
            adaptedFeatureCount = adapted.features.size,
            projectedFeatures = projected,
            adapterDiagnostics = adapted.diagnostics.map { "${it.severity}: feature=${it.featureId} ${it.message}" },
            depthMesh = depthMesh
        )
        val hits = if (request.centerCrosshair.enabled && request.centerCrosshair.queryOnRender) {
            frameWithoutHits.hitTester().centerCrosshairHitTest(request.camera)
        } else {
            emptyList()
        }
        return frameWithoutHits.copy(centerCrosshairHits = hits)
    }

    fun portrayFrame(
        request: ChartRenderRequest,
        engine: S52PortrayalEngine,
        settings: MarinerSettings,
        context: PortrayalContext
    ): S57PortrayedChartFrame {
        val frame = prepareFrame(request)
        val features = indexStore.queryFeatures(S57FeatureQuery(request.cellId, request.bounds))
        val portrayed = adapter.portray(features, engine, settings, context)
        return S57PortrayedChartFrame(
            frame = frame,
            commands = portrayed.commands,
            transcript = portrayed.transcript.ifBlank { S52DrawCommandTranscript.serialize(portrayed.commands) }
        )
    }
}

data class S57PortrayedChartFrame(
    val frame: StaticChartFrame,
    val commands: List<S52DrawCommand>,
    val transcript: String
)
