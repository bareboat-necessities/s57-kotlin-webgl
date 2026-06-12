package io.github.s57.index

import io.github.s57.core.GeoBounds
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Dataset
import io.github.s57.core.S57Feature

interface S57IndexStore {
    fun putCell(summary: S57CellSummary)
    fun putFeatures(cellId: String, features: List<S57Feature>)
    fun importDataset(dataset: S57Dataset): S57IndexImportReport
    fun listCells(): List<S57CellSummary>
    fun getCell(cellId: String): S57CellSummary?
    fun queryFeatures(cellId: String, bounds: GeoBounds): List<S57Feature>
    fun queryFeatures(query: S57FeatureQuery): List<S57Feature>
    fun stats(): S57IndexStats
    fun clear()
}

/**
 * Phase 5 deterministic in-memory implementation. It shares the same fixed-grid
 * binning logic as the browser store and gives common/JVM tests a real query
 * target without requiring IndexedDB.
 */
class InMemoryS57IndexStore(
    private val spatialIndex: S57SpatialBinIndex = S57SpatialBinIndex(),
    private val maxCachedBinQueries: Int = 96
) : S57IndexStore {
    private val cells = linkedMapOf<String, S57CellSummary>()
    private val featuresByCell = linkedMapOf<String, LinkedHashMap<Long, StoredS57Feature>>()
    private val binsByCell = linkedMapOf<String, Map<SpatialBinId, Set<Long>>>()
    private val candidateCache = linkedMapOf<SpatialCandidateKey, List<StoredS57Feature>>()

    override fun putCell(summary: S57CellSummary) {
        cells[summary.cellId] = summary
        invalidateCellCache(summary.cellId)
    }

    override fun putFeatures(cellId: String, features: List<S57Feature>) {
        val stored = features.map { StoredS57Feature.from(cellId, it) }
        val bucket = featuresByCell.getOrPut(cellId) { linkedMapOf() }
        for (feature in stored) bucket[feature.featureId] = feature
        rebuildBins(cellId)
        invalidateCellCache(cellId)
    }

    override fun importDataset(dataset: S57Dataset): S57IndexImportReport {
        putCell(dataset.summary)
        val stored = dataset.toStoredFeatures()
        featuresByCell[dataset.summary.cellId] = stored.associateByTo(linkedMapOf()) { it.featureId }
        rebuildBins(dataset.summary.cellId)
        invalidateCellCache(dataset.summary.cellId)
        val bins = binsByCell[dataset.summary.cellId].orEmpty()
        return S57IndexImportReport(
            cellId = dataset.summary.cellId,
            featureCount = dataset.features.size,
            indexedFeatureCount = stored.count { it.bounds != null },
            emptyGeometryCount = stored.count { it.bounds == null },
            binCount = bins.size
        )
    }

    override fun listCells(): List<S57CellSummary> = cells.values.toList()

    override fun getCell(cellId: String): S57CellSummary? = cells[cellId]

    override fun queryFeatures(cellId: String, bounds: GeoBounds): List<S57Feature> =
        queryFeatures(S57FeatureQuery(cellId, bounds))

    override fun queryFeatures(query: S57FeatureQuery): List<S57Feature> {
        val byId = featuresByCell[query.cellId].orEmpty()
        if (byId.isEmpty()) return emptyList()
        val cellBins = binsByCell[query.cellId].orEmpty()
        if (cellBins.isEmpty()) return emptyList()

        val queryBins = spatialIndex.binsForBounds(query.cellId, query.bounds)
        val candidates = cachedCandidatesForBins(query.cellId, queryBins, cellBins, byId)
        if (candidates.isEmpty()) return emptyList()

        return candidates.asSequence()
            .filter { feature -> feature.bounds?.intersects(query.bounds) == true }
            .filter { feature -> query.objectClasses.isEmpty() || feature.objectClass in query.objectClasses }
            .take(query.limit)
            .map { it.toFeature() }
            .toList()
    }

    override fun stats(): S57IndexStats = S57IndexStats(
        cellCount = cells.size,
        featureCount = featuresByCell.values.sumOf { it.size },
        binCount = binsByCell.values.sumOf { it.size }
    )

    override fun clear() {
        cells.clear()
        featuresByCell.clear()
        binsByCell.clear()
        candidateCache.clear()
    }

    private fun rebuildBins(cellId: String) {
        binsByCell[cellId] = spatialIndex.indexFeatures(cellId, featuresByCell[cellId].orEmpty().values.toList())
    }

    private fun cachedCandidatesForBins(
        cellId: String,
        queryBins: Set<SpatialBinId>,
        cellBins: Map<SpatialBinId, Set<Long>>,
        byId: Map<Long, StoredS57Feature>
    ): List<StoredS57Feature> {
        if (queryBins.isEmpty()) return emptyList()
        val key = SpatialCandidateKey(cellId, queryBins.map { it.toString() }.sorted())
        candidateCache[key]?.let { return it }

        val candidateIds = queryBins.asSequence()
            .flatMap { bin -> cellBins[bin].orEmpty().asSequence() }
            .distinct()
            .toList()
        val candidates = candidateIds.mapNotNull { byId[it] }
        if (candidateCache.size >= maxCachedBinQueries) {
            val oldest = candidateCache.keys.firstOrNull()
            if (oldest != null) candidateCache.remove(oldest)
        }
        candidateCache[key] = candidates
        return candidates
    }

    private fun invalidateCellCache(cellId: String) {
        candidateCache.keys.filter { it.cellId == cellId }.forEach { candidateCache.remove(it) }
    }

    private data class SpatialCandidateKey(
        val cellId: String,
        val binIds: List<String>
    )
}
