package io.github.s57.core.raw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Phase18CatalogLookupTest {
    @Test
    fun resolvesCommonNoaaObjectClassesToAcronyms() {
        val expected = mapOf(
            42 to "DEPARE",
            43 to "DEPCNT",
            129 to "SOUNDG",
            17 to "BOYLAT",
            7 to "BCNLAT",
            75 to "LIGHTS",
            159 to "WRECKS",
            86 to "OBSTRN",
            71 to "LNDARE",
            302 to "M_COVR",
            30 to "COALNE",
            122 to "SLCONS"
        )

        expected.forEach { (code, acronym) ->
            val resolved = S57ObjectClassLookup.acronym(code)
            assertEquals(acronym, resolved)
            assertFalse(resolved.startsWith("OBJL_"), "object code $code should resolve to $acronym")
            assertEquals(code, S57ObjectClassLookup.code(acronym))
        }
    }

    @Test
    fun preservesUnknownObjectClassesAsStableNumericKeys() {
        assertEquals("OBJL_9999", S57ObjectClassLookup.acronym(9999))
    }

    @Test
    fun resolvesCommonPortrayalAttributesToAcronyms() {
        val expected = mapOf(
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

        expected.forEach { (code, acronym) ->
            val resolved = S57AttributeLookup.acronym(code)
            assertEquals(acronym, resolved)
            assertFalse(resolved.startsWith("ATTL_"), "attribute code $code should resolve to $acronym")
            assertEquals(code, S57AttributeLookup.code(acronym))
        }
    }

    @Test
    fun preservesUnknownAttributesAsStableNumericKeys() {
        assertEquals("ATTL_9999", S57AttributeLookup.acronym(9999))
    }
}
