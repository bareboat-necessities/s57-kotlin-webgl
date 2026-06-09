package io.github.s57.core.raw

/**
 * Generated-style S-57 object/attribute lookup used by the raw decoder.
 *
 * The table is intentionally data-only and keeps unknown values stable as
 * OBJL_### / ATTL_###. It is seeded from the S-52 catalogue starter set plus
 * common NOAA ENC classes needed by the browser chart pipeline.
 */
object S57ObjectClassLookup {
    private val byCode: Map<Int, String> = mapOf(
        4 to "BCNCAR",
        7 to "BCNLAT",
        14 to "BOYCAR",
        17 to "BOYLAT",
        20 to "BOYSAW",
        30 to "COALNE",
        42 to "DEPARE",
        43 to "DEPCNT",
        71 to "LNDARE",
        75 to "LIGHTS",
        86 to "OBSTRN",
        112 to "RESARE",
        122 to "SLCONS",
        129 to "SOUNDG",
        144 to "TOPMAR",
        159 to "WRECKS",
        302 to "M_COVR",
        308 to "M_QUAL",
        312 to "M_NSYS",
        400 to "C_AGGR",
        401 to "C_ASSO"
    )

    private val byAcronym: Map<String, Int> = byCode.entries.associate { (code, acronym) -> acronym to code }

    fun acronym(code: Int): String = byCode[code] ?: "OBJL_$code"

    fun code(acronym: String): Int? = byAcronym[acronym.trim().uppercase()]

    fun isKnown(code: Int): Boolean = code in byCode
}

object S57AttributeLookup {
    private val byCode: Map<Int, String> = mapOf(
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
        142 to "SIGPER",
        156 to "TXTDSC",
        174 to "VALDCO",
        179 to "VALSOU",
        187 to "WATLEV"
    )

    private val byAcronym: Map<String, Int> = byCode.entries.associate { (code, acronym) -> acronym to code }

    fun acronym(code: Int): String = byCode[code] ?: "ATTL_$code"

    fun code(acronym: String): Int? = byAcronym[acronym.trim().uppercase()]

    fun isKnown(code: Int): Boolean = code in byCode
}
