package com.kupuproxy.app

import org.junit.Assert.assertEquals
import org.junit.Test

class InsightsModelTest {
    @Test fun reliabilityUsesAllObservations() {
        val insight = ProxyInsight("tg://proxy", successes = 3, failures = 1, 80, 2, 1)
        assertEquals(75, insight.reliability)
    }

    @Test fun emptyHistoryHasZeroReliability() {
        val insight = ProxyInsight("tg://proxy", 0, 0, -1, 0, 0)
        assertEquals(0, insight.reliability)
    }
}
