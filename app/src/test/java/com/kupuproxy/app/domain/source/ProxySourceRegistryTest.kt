package com.kupuproxy.app.domain.source

import com.kupuproxy.app.data.source.KortCollectorSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySourceRegistryTest {

    @Test
    fun backgroundSourcesAreExplicitAndContainCollector() {
        val sources = ProxySourceRegistry.backgroundRefreshSources()

        assertTrue(sources.any { it.id == KortCollectorSource.ID })
        assertEquals(sources.map { it.id }.distinct(), sources.map { it.id })
        assertFalse(sources.any { it.id == "kort_ru" || it.id == "kort_eu" })
    }

    @Test
    fun legacyKortAllResolvesToVerifiedSource() {
        assertEquals(KortCollectorSource.ID, ProxySourceRegistry.byId("kort_all")?.id)
    }
}
