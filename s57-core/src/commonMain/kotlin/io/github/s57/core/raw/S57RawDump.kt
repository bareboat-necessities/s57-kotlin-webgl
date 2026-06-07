package io.github.s57.core.raw

object S57RawDumper {
    fun summarize(dataset: S57RawDataset): String = buildString {
        appendLine("S-57 raw dataset cell=${dataset.metadata.cellName.ifBlank { "UNKNOWN" }} features=${dataset.features.size} vectors=${dataset.vectors.size} unknown=${dataset.unknownRecords.size}")
        val counts = dataset.featureCountsByObjectClass().entries.sortedBy { it.key }
        appendLine("Feature counts:")
        for ((name, count) in counts) appendLine("  $name=$count")
        if (dataset.vectors.isNotEmpty()) {
            appendLine("Vectors:")
            for (vector in dataset.vectors.take(16)) {
                appendLine("  ${vector.recordName} v${vector.version} sg2d=${vector.twoDimensionalCoordinateCount} sg3d=${vector.threeDimensionalCoordinateCount}")
            }
        }
        if (dataset.features.isNotEmpty()) {
            appendLine("Features:")
            for (feature in dataset.features.take(16)) {
                val attrs = feature.attributes.joinToString(",") { it.acronym + "=" + it.value }.take(96)
                appendLine("  ${feature.recordName} ${feature.objectClassAcronym} prim=${feature.primitive} attrs=[$attrs] refs=${feature.spatialReferences.size}")
            }
        }
    }
}
