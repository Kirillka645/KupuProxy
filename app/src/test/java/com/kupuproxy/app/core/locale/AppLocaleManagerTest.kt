package com.kupuproxy.app.core.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLocaleManagerTest {
    @Test
    fun normalizeTagMatchesSupportedLocales() {
        assertEquals("pt-BR", AppLocaleManager.normalizeTag("pt-br"))
        assertEquals("zh-CN", AppLocaleManager.normalizeTag("zh-cn"))
    }

    @Test
    fun normalizeTagRejectsUnsupportedLocales() {
        assertNull(AppLocaleManager.normalizeTag("xx"))
    }
}
