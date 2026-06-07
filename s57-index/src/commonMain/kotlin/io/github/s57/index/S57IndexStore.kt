package io.github.s57.index

import io.github.s57.core.GeoBounds
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Feature

interface S57IndexStore {
    fun putCell(summary: S57CellSummary)
    fun putFeatures(cellId: String, features: List<S57Feature>)
    fun listCells(): List<S57CellSummary>
    fun queryFeatures(cellId: String, bounds: GeoBounds): List<S57Feature>
}

/** Phase 0 in-memory implementation. A real IndexedDB-backed store starts in the indexing phase. */
class InMemoryS57IndexStore : S57IndexStore {
    private val cells = linkedMapOf<String, S57CellSummary>()
    private val featuresByCell = linkedMapOf<String, MutableList<S57Feature>>()

    override fun putCell(summary: S57CellSummary) {
        cells[summary.cellId] = summary
    }

    override fun putFeatures(cellId: String, features: List<S57Feature>) {
        featuresByCell.getOrPut(cellId) { mutableListOf() }.addAll(features)
    }

    override fun listCells(): List<S57CellSummary> = cells.values.toList()

    override fun queryFeatures(cellId: String, bounds: GeoBounds): List<S57Feature> =
        featuresByCell[cellId].orEmpty().filter { feature -> feature.bounds?.intersects(bounds) == true }
}
