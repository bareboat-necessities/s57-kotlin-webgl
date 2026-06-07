package io.github.s57.adapter

import io.github.s57.core.S57Feature
import kotlin.test.Test
import kotlin.test.assertEquals

class S57ToS52AdapterTest {
    @Test
    fun preservesObjectClassAcronym() {
        val adapted = S57ToS52Adapter().adapt(S57Feature(id = 7, objectClass = "DEPARE"))
        assertEquals("DEPARE", adapted.objectClassAcronym)
    }
}
