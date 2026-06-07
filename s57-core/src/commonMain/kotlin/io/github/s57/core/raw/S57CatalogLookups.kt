package io.github.s57.core.raw

/** Small built-in S-57 object/attribute lookup used by the Phase 3 raw decoder.
 *
 * The full catalogue should be generated/imported in a later hardening phase.
 * Unknown values are deliberately preserved as stable OBJL_### / ATTL_### keys.
 */
object S57ObjectClassLookup {
    private val byCode = mapOf(
        7 to "BCNLAT",
        17 to "BOYLAT",
        42 to "DEPARE",
        43 to "DEPCNT",
        75 to "LIGHTS",
        86 to "OBSTRN",
        129 to "SOUNDG",
        159 to "WRECKS"
    )

    fun acronym(code: Int): String = byCode[code] ?: "OBJL_$code"
}

object S57AttributeLookup {
    private val byCode = mapOf(
        34 to "CATOBS",
        37 to "CATLAM",
        56 to "CATREA",
        71 to "CATWRK",
        75 to "COLOUR",
        76 to "COLPAT",
        87 to "DRVAL1",
        88 to "DRVAL2",
        98 to "HEIGHT",
        102 to "INFORM",
        107 to "LITCHR",
        116 to "OBJNAM",
        141 to "SIGGRP",
        179 to "VALSOU"
    )

    fun acronym(code: Int): String = byCode[code] ?: "ATTL_$code"
}
