package io.github.s57.core.import

import io.github.s57.core.S57Dataset
import io.github.s57.core.geometry.S57GeometryBuildResult
import io.github.s57.core.geometry.S57GeometryBuilder
import io.github.s57.core.raw.S57RawDataset
import io.github.s57.core.raw.S57RawDecoder

/**
 * Phase 10 end-to-end import pipeline for one S-57/ENC byte payload.
 *
 * The pipeline deliberately stays in commonMain: it can run on JVM tests and in
 * the browser after a File/Blob has been read into a ByteArray.  It performs the
 * low-level parse/decode/geometry steps and returns a decoded dataset ready for
 * the Phase 5 index.  It does not manage chart quilting, AIS, NMEA, or live
 * chartplotter state.
 */
class S57ImportPipeline(
    private val rawDecoder: S57RawDecoder = S57RawDecoder(),
    private val geometryBuilder: S57GeometryBuilder = S57GeometryBuilder()
) {
    fun importBytes(bytes: ByteArray): S57ImportResult {
        val raw = rawDecoder.decode(bytes)
        val geometry = geometryBuilder.build(raw)
        return S57ImportResult(raw = raw, geometry = geometry)
    }
}

data class S57ImportResult(
    val raw: S57RawDataset,
    val geometry: S57GeometryBuildResult
) {
    val dataset: S57Dataset get() = geometry.dataset
    val featureCount: Int get() = dataset.features.size
    val geometryDiagnosticCount: Int get() = geometry.diagnostics.size

    fun toPlainText(): String = buildString {
        appendLine("S-57 import cell=${dataset.summary.cellId} features=$featureCount vectors=${raw.vectors.size} unknown=${raw.unknownRecords.size}")
        appendLine("bounds=${dataset.summary.bounds ?: "none"} geometryDiagnostics=$geometryDiagnosticCount")
    }
}
