package io.github.s57.render

import io.github.s57.adapter.S57ToS52Adapter
import io.github.s57.adapter.S57ToS52DiagnosticSeverity
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.geometry.S57GeometryDiagnosticSeverity
import io.github.s57.core.import.S57ImportPipeline
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * External ENC corpus coverage test.
 *
 * Pass a ZIP containing S-57/ENC cells with:
 *   gradle :s57-render-webgl:jvmTest --tests io.github.s57.render.ChartZipCoverageTest -Ds57.chartZip=/path/to/charts.zip
 *
 * The ZIP may contain recursive folders. Files named *.000 are treated as base
 * cells and sibling *.001, *.002, ... entries with the same path/name stem are
 * applied as updates in numeric order.
 */
class ChartZipCoverageTest {
    @Test
    fun chartZipHasNoUnknownObjectsContoursAreasOrFallbacks() {
        val zipPath = System.getProperty(CHART_ZIP_PROPERTY)?.takeIf { it.isNotBlank() }
        if (zipPath == null) {
            println("Skipping chart ZIP coverage test; set -D$CHART_ZIP_PROPERTY=/path/to/charts.zip to run it.")
            return
        }

        val report = ChartZipCoverageValidator().validate(File(zipPath))

        assertTrue(report.failures.isEmpty(), report.toPlainText())
    }

    private companion object {
        const val CHART_ZIP_PROPERTY = "s57.chartZip"
    }
}

private class ChartZipCoverageValidator(
    private val importPipeline: S57ImportPipeline = S57ImportPipeline(),
    private val adapter: S57ToS52Adapter = S57ToS52Adapter()
) {
    fun validate(zipFile: File): ChartZipCoverageReport {
        require(zipFile.isFile) { "Chart ZIP does not exist: ${zipFile.absolutePath}" }
        require(zipFile.extension.equals("zip", ignoreCase = true)) { "Chart corpus must be a .zip file: ${zipFile.absolutePath}" }

        val entries = zipFile.readS57Entries()
        val groups = entries.groupBy { it.stem }
        val failures = mutableListOf<String>()
        val cellReports = mutableListOf<ChartCellCoverageReport>()

        if (entries.isEmpty()) failures += "${zipFile.name}: no S-57 cell/update files (*.000, *.001, ...) were found"

        groups.toSortedMap().forEach { (stem, groupEntries) ->
            val sorted = groupEntries.sortedBy { it.updateNumber }
            if (sorted.none { it.updateNumber == 0 }) {
                failures += "$stem: found update file(s) ${sorted.joinToString { it.name }} but no base .000 cell"
                return@forEach
            }

            val validation = validateCell(stem, sorted)
            cellReports += validation.report
            failures += validation.failures
        }

        return ChartZipCoverageReport(zipFile = zipFile, cellReports = cellReports, failures = failures)
    }

    private fun validateCell(stem: String, entries: List<ChartZipS57Entry>): ChartCellCoverageValidation {
        val failures = mutableListOf<String>()
        val imported = runCatching { importPipeline.importByteSequence(entries.map { it.bytes }) }
            .getOrElse { error ->
                val importFailure = "$stem: import failed: ${error.message ?: error::class.qualifiedName}"
                return ChartCellCoverageValidation(
                    report = ChartCellCoverageReport(
                        stem = stem,
                        updateCount = entries.size - 1,
                        featureCount = 0,
                        objectClassCount = 0,
                        failureCount = 1
                    ),
                    failures = listOf(importFailure)
                )
            }
        val dataset = imported.dataset

        imported.raw.unknownRecords.forEach { record ->
            failures += "$stem: raw decoder produced unknown record index=${record.index} tags=${record.tags.sorted()} preview=${record.preview}"
        }
        imported.geometry.diagnostics
            .filter { it.severity != S57GeometryDiagnosticSeverity.Info }
            .forEach { diagnostic ->
                failures += "$stem: geometry ${diagnostic.severity} for feature ${diagnostic.featureId}: ${diagnostic.message}"
            }

        dataset.features.forEach { feature ->
            failures += feature.catalogCoverageFailures(stem)
        }

        val adapted = adapter.adaptFeatures(dataset.features)
        adapted.diagnostics
            .filter { it.severity == S57ToS52DiagnosticSeverity.Warning }
            .forEach { diagnostic ->
                failures += "$stem: adapter ${diagnostic.kind} warning for feature ${diagnostic.featureId}: ${diagnostic.message}"
            }

        return ChartCellCoverageValidation(
            report = ChartCellCoverageReport(
                stem = stem,
                updateCount = entries.size - 1,
                featureCount = imported.featureCount,
                objectClassCount = dataset.features.map { it.objectClass.uppercase() }.toSet().size,
                failureCount = failures.size
            ),
            failures = failures
        )
    }

    private fun S57Feature.catalogCoverageFailures(stem: String): List<String> = buildList {
        val normalizedClass = objectClass.uppercase()
        if (normalizedClass.startsWith("OBJL_")) {
            add("$stem: feature $id uses unknown object class $normalizedClass")
        }
        attributes.keys
            .map { it.uppercase() }
            .filter { it.startsWith("ATTL_") }
            .forEach { attr -> add("$stem: feature $id uses unknown attribute $attr") }

        if (normalizedClass in CONTOUR_OBJECT_CLASSES && geometry !is S57Geometry.LineString) {
            add("$stem: contour feature $id ($normalizedClass) decoded as ${geometry.coverageName()} instead of LineString")
        }
        if (normalizedClass in AREA_OBJECT_CLASSES && geometry !is S57Geometry.Polygon && geometry !is S57Geometry.MultiPolygon) {
            add("$stem: area feature $id ($normalizedClass) decoded as ${geometry.coverageName()} instead of Polygon/MultiPolygon")
        }
    }

    private fun S57Geometry.coverageName(): String = when (this) {
        S57Geometry.Empty -> "Empty"
        is S57Geometry.Point -> "Point"
        is S57Geometry.MultiPoint -> "MultiPoint"
        is S57Geometry.LineString -> "LineString"
        is S57Geometry.Polygon -> "Polygon"
        is S57Geometry.MultiPolygon -> "MultiPolygon"
    }

    private fun File.readS57Entries(): List<ChartZipS57Entry> {
        val digitExtension = Regex("\\d{3}")
        val entries = mutableListOf<ChartZipS57Entry>()
        inputStream().buffered().use { fileStream ->
            ZipInputStream(fileStream).use { zipStream ->
                while (true) {
                    val entry = zipStream.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val normalizedName = entry.name.replace('\\', '/')
                        val extension = normalizedName.substringAfterLast('.', missingDelimiterValue = "")
                        if (digitExtension.matches(extension)) {
                            entries += ChartZipS57Entry(
                                name = normalizedName,
                                stem = normalizedName.substringBeforeLast('.'),
                                updateNumber = extension.toInt(),
                                bytes = zipStream.readBytes()
                            )
                        }
                    }
                    zipStream.closeEntry()
                }
            }
        }
        return entries
    }

    private companion object {
        private val CONTOUR_OBJECT_CLASSES: Set<String> = setOf("DEPCNT")

        private val AREA_OBJECT_CLASSES: Set<String> = setOf(
            "ACHARE", "ADMARE", "AIRARE", "BUAARE", "CBLARE", "CONZNE", "COSARE", "CTSARE", "CTNARE",
            "CUSZNE", "DEPARE", "DMPGRD", "DOCARE", "DRGARE", "DWRTCL", "EXEZNE", "FAIRWY",
            "FRPARE", "FSHGRD", "FSHZNE", "HRBARE", "ICEARE", "ICNARE", "ISTZNE", "LAKARE", "LNDARE",
            "LOKBSN", "MIPARE", "M_ACCY", "M_COVR", "M_HDAT", "M_HOPA", "M_NPUB", "M_NSYS", "M_PROD",
            "M_QUAL", "M_SDAT", "M_SREL", "M_UNIT", "M_VDAT", "OSPARE", "PIPARE", "PRCARE", "PRDARE",
            "RESARE", "SBDARE", "SEAARE", "SPLARE", "SWPARE", "TESARE", "TSEZNE", "TSSCRS", "TSSRON",
            "TSSLPT", "TSSBND", "UNSARE"
        )
    }
}

private data class ChartZipS57Entry(
    val name: String,
    val stem: String,
    val updateNumber: Int,
    val bytes: ByteArray
)

private data class ChartCellCoverageValidation(
    val report: ChartCellCoverageReport,
    val failures: List<String>
)

private data class ChartCellCoverageReport(
    val stem: String,
    val updateCount: Int,
    val featureCount: Int,
    val objectClassCount: Int,
    val failureCount: Int
)

private data class ChartZipCoverageReport(
    val zipFile: File,
    val cellReports: List<ChartCellCoverageReport>,
    val failures: List<String>
) {
    fun toPlainText(): String = buildString {
        appendLine("chartZip=${zipFile.absolutePath} cells=${cellReports.size} failures=${failures.size}")
        cellReports.forEach { cell ->
            appendLine("cell=${cell.stem} updates=${cell.updateCount} features=${cell.featureCount} objectClasses=${cell.objectClassCount} failures=${cell.failureCount}")
        }
        failures.take(200).forEach { appendLine(it) }
        if (failures.size > 200) appendLine("... ${failures.size - 200} additional failure(s) omitted")
    }
}
