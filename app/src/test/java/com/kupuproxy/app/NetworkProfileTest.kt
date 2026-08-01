package com.kupuproxy.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkProfileTest {
    @Test
    fun wifiScansTheFullCollectorCapacityWithoutEarlyStop() {
        val settings = ProfileSettings.forMode(NetworkProfileMode.WIFI)
        assertEquals(MAX_SCAN_PROXIES, settings.maxToCheck)
        assertEquals(0, settings.stopWhenFound)
    }

    @Test
    fun mobileScansTheFullCollectorCapacityWithoutEarlyStop() {
        val settings = ProfileSettings.forMode(NetworkProfileMode.MOBILE)
        assertEquals(MAX_SCAN_PROXIES, settings.maxToCheck)
        assertEquals(0, settings.stopWhenFound)
    }

    @Test
    fun preparationNoLongerTruncatesListsAtFourHundred() {
        val proxies =
            (1..1_000).map { index ->
                "tg://proxy?server=proxy$index.example.com&port=443&secret=abcdef$index"
            }
        val prepared =
            ProxyManager.prepareForProfile(
                proxies,
                ProfileSettings.forMode(NetworkProfileMode.WIFI),
            )
        assertEquals(1_000, prepared.size)
    }
}
