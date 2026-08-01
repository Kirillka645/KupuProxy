package com.kupuproxy.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppearancePreferencesTest {
    @Test
    fun normalizesValidHexColors() {
        assertEquals("#006C61", normalizeHexColor("006c61"))
        assertEquals("#AABBCC", normalizeHexColor("  #aabbcc "))
    }

    @Test
    fun rejectsInvalidHexColors() {
        assertNull(normalizeHexColor(null))
        assertNull(normalizeHexColor("#123"))
        assertNull(normalizeHexColor("#GG0000"))
    }
}
