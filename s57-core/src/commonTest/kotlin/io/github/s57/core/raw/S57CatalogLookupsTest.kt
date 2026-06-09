package io.github.s57.core.raw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class S57CatalogLookupsTest {
    @Test
    fun coversCommonBaseObjectClassesAndMetaClasses() {
        assertTrue(S57ObjectClassLookup.knownCount() >= 170)
        assertEquals("ACHARE", S57ObjectClassLookup.acronym(4))
        assertEquals("FAIRWY", S57ObjectClassLookup.acronym(51))
        assertEquals("HRBARE", S57ObjectClassLookup.acronym(63))
        assertEquals("PILPNT", S57ObjectClassLookup.acronym(90))
        assertEquals("SBDARE", S57ObjectClassLookup.acronym(121))
        assertEquals("M_COVR", S57ObjectClassLookup.acronym(302))
        assertEquals("C_AGGR", S57ObjectClassLookup.acronym(400))
        assertEquals(129, S57ObjectClassLookup.code("soundg"))
    }

    @Test
    fun keepsUnknownObjectClassesStable() {
        assertEquals("OBJL_99999", S57ObjectClassLookup.acronym(99999))
        assertNull(S57ObjectClassLookup.code("does_not_exist"))
    }

    @Test
    fun coversCorrectBaseAttributeCodes() {
        assertTrue(S57AttributeLookup.knownCount() >= 190)
        assertEquals("CATACH", S57AttributeLookup.acronym(8))
        assertEquals("CATOBS", S57AttributeLookup.acronym(42))
        assertEquals("DRVAL1", S57AttributeLookup.acronym(87))
        assertEquals("QUASOU", S57AttributeLookup.acronym(125))
        assertEquals("SCAMIN", S57AttributeLookup.acronym(133))
        assertEquals("TECSOU", S57AttributeLookup.acronym(156))
        assertEquals("VALSOU", S57AttributeLookup.acronym(179))
        assertEquals(187, S57AttributeLookup.code("watlev"))
    }

    @Test
    fun keepsUnknownAttributesStable() {
        assertEquals("ATTL_99999", S57AttributeLookup.acronym(99999))
        assertNull(S57AttributeLookup.code("does_not_exist"))
    }
}
