package com.kupuproxy.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanPreferencesTest {
    private val base = ProfileSettings.forMode(NetworkProfileMode.WIFI)

    @Test fun quickModeIsBoundedAndStopsEarly() {
        val value = ScanPreferences.apply(base, ScanConfiguration(mode = ScanMode.QUICK))
        assertEquals(500, value.maxToCheck)
        assertEquals(25, value.stopWhenFound)
    }

    @Test fun fullModeUsesCollectorCapacity() {
        val value = ScanPreferences.apply(base, ScanConfiguration(mode = ScanMode.FULL))
        assertEquals(MAX_SCAN_PROXIES, value.maxToCheck)
        assertEquals(0, value.stopWhenFound)
    }

    @Test fun customModeClampsUnsafeValues() {
        val value =
            ScanPreferences.apply(
                base,
                ScanConfiguration(mode = ScanMode.CUSTOM, customLimit = Int.MAX_VALUE, customWorkers = 1),
            )
        assertEquals(MAX_SCAN_PROXIES, value.maxToCheck)
        assertEquals(16, value.batchSize)
    }
}
