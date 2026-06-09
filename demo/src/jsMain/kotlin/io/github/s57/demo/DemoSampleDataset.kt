package io.github.s57.demo

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Dataset
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value

internal fun sampleDataset(): S57Dataset = S57Dataset(
    summary = S57CellSummary(
        cellId = "PHASE12-S52-SAMPLE",
        name = "PHASE12-S52-SAMPLE",
        bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0),
        featureCount = 4
    ),
    features = listOf(
        S57Feature(
            id = 1,
            objectClass = "DEPARE",
            attributes = mapOf("DRVAL1" to S57Value.Decimal(0.0), "DRVAL2" to S57Value.Decimal(10.0)),
            geometry = S57Geometry.Polygon(listOf(listOf(
                GeoPoint(-74.8, 39.2),
                GeoPoint(-73.2, 39.2),
                GeoPoint(-73.2, 40.8),
                GeoPoint(-74.8, 40.8),
                GeoPoint(-74.8, 39.2)
            )))
        ),
        S57Feature(
            id = 2,
            objectClass = "DEPCNT",
            geometry = S57Geometry.LineString(listOf(GeoPoint(-74.8, 40.0), GeoPoint(-73.2, 40.0)))
        ),
        S57Feature(
            id = 3,
            objectClass = "SOUNDG",
            attributes = mapOf("VALSOU" to S57Value.Decimal(4.2)),
            geometry = S57Geometry.MultiPoint(listOf(GeoPoint(-74.1, 40.18), GeoPoint(-73.85, 40.28), GeoPoint(-74.0, 40.0)))
        ),
        S57Feature(
            id = 4,
            objectClass = "BOYLAT",
            geometry = S57Geometry.Point(GeoPoint(-73.65, 40.52))
        )
    )
)
