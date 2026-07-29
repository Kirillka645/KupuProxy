package com.kupuproxy.app.domain.aggregator

import com.kupuproxy.app.domain.model.RawProxyEntry
import com.kupuproxy.app.domain.model.SecretType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyAggregatorMetadataTest {

    @Test
    fun mergesMetadataWithoutCountingDisplayNameAsSource() {
        val secret = "0123456789abcdef0123456789abcdef"
        val entries = listOf(
            RawProxyEntry(
                url = "tg://proxy?server=8.8.8.8&port=443&secret=$secret",
                host = "8.8.8.8",
                port = 443,
                secret = secret,
                secretType = SecretType.PLAIN,
                sourceId = "kort_verified",
                sourceName = "Kort Verified",
                region = "us",
                upstreamPingMs = 40,
                probeResistant = true
            ),
            RawProxyEntry(
                url = "tg://proxy?server=8.8.8.8&port=443&secret=$secret",
                host = "8.8.8.8",
                port = 443,
                secret = secret,
                secretType = SecretType.PLAIN,
                sourceId = "other",
                sourceName = "Other",
                upstreamPingMs = 20
            )
        )

        val endpoint = ProxyAggregator(OkHttpClient()).dedupe(entries).single()

        assertEquals(setOf("kort_verified", "other"), endpoint.sourceIds)
        assertEquals(2, endpoint.reliabilityScore)
        assertEquals("us", endpoint.region)
        assertEquals(20, endpoint.upstreamPingMs)
        assertTrue(endpoint.probeResistant == true)
    }
}
