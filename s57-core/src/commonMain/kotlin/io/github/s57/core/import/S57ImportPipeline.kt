package io.github.s57.core.import

import io.github.s57.core.S57Dataset
import io.github.s57.core.geometry.S57GeometryBuildResult
import io.github.s57.core.geometry.S57GeometryBuilder
import io.github.s57.core.raw.S57RawDataset
import io.github.s57.core.raw.S57RawDecoder
import io.github.s57.core.raw.S57DatasetMetadata
import io.github.s57.core.raw.S57UpdateInstruction

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

    fun importByteSequence(payloads: List<ByteArray>): S57ImportResult {
        require(payloads.isNotEmpty()) { "At least one S-57 payload is required" }
        val decoded = payloads.map(rawDecoder::decode)
        val raw = decoded.mergeBaseWithUpdates()
        val geometry = geometryBuilder.build(raw)
        return S57ImportResult(raw = raw, geometry = geometry)
    }

    private fun List<S57RawDataset>.mergeBaseWithUpdates(): S57RawDataset {
        val base = first()
        val featuresByName = linkedMapOf(*base.features.map { it.recordName to it }.toTypedArray())
        val vectorsByName = linkedMapOf(*base.vectors.map { it.recordName to it }.toTypedArray())
        val unknownRecords = base.unknownRecords.toMutableList()
        drop(1).forEach { update ->
            update.features.forEach { feature ->
                when (feature.updateInstruction) {
                    S57UpdateInstruction.Delete -> featuresByName.remove(feature.recordName)
                    S57UpdateInstruction.Insert,
                    S57UpdateInstruction.Modify,
                    S57UpdateInstruction.Unknown -> featuresByName[feature.recordName] = feature
                }
            }
            update.vectors.forEach { vector ->
                when (vector.updateInstruction) {
                    S57UpdateInstruction.Delete -> vectorsByName.remove(vector.recordName)
                    S57UpdateInstruction.Insert,
                    S57UpdateInstruction.Modify,
                    S57UpdateInstruction.Unknown -> vectorsByName[vector.recordName] = vector
                }
            }
            unknownRecords += update.unknownRecords
        }
        val latestMetadata = fold(base.metadata) { acc, next -> acc.updatedBy(next.metadata) }
        return S57RawDataset(
            metadata = latestMetadata,
            features = featuresByName.values.toList(),
            vectors = vectorsByName.values.toList(),
            unknownRecords = unknownRecords
        )
    }

    private fun S57DatasetMetadata.updatedBy(update: S57DatasetMetadata): S57DatasetMetadata = copy(
        cellName = update.cellName.ifBlank { cellName },
        edition = update.edition ?: edition,
        updateNumber = update.updateNumber ?: updateNumber,
        issueDate = update.issueDate ?: issueDate,
        updateApplicationDate = update.updateApplicationDate ?: updateApplicationDate,
        productSpecification = update.productSpecification ?: productSpecification,
        coordinateMultiplier = update.coordinateMultiplier ?: coordinateMultiplier,
        soundingMultiplier = update.soundingMultiplier ?: soundingMultiplier,
        rawFields = rawFields + update.rawFields
    )
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
