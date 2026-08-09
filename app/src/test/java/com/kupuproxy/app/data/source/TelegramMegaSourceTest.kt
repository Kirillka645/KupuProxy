package com.kupuproxy.app.data.source

import com.kupuproxy.app.domain.model.RawProxyEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramMegaSourceTest {

    @Test
    fun collectPhaseResults_runsConcurrentlyAndPreservesSuccessfulSiblings() = runTest {
        val results =
            TelegramMegaSource.collectPhaseResults(
                phaseTimeoutMs = 500L,
                phases =
                    listOf(
                        {
                            delay(100L)
                            listOf(proxy("1.1.1.1"))
                        },
                        {
                            delay(200L)
                            error("broken phase")
                        },
                        {
                            delay(300L)
                            listOf(proxy("2.2.2.2"))
                        },
                        {
                            delay(5_000L)
                            listOf(proxy("3.3.3.3"))
                        },
                    ),
            )

        assertEquals(listOf(1, 0, 1, 0), results.map { it.size })
        assertEquals(listOf("1.1.1.1", "2.2.2.2"), results.flatten().map { it.host })
        assertEquals(500L, testScheduler.currentTime)
    }

    @Test
    fun mergeEntries_deduplicatesCaseInsensitivelyAndSetsMegaSource() {
        val first = proxy(host = "Proxy.Example", secret = "EEAABBCC")
        val duplicate = proxy(host = "proxy.example", secret = "eeaabbcc")
        val distinct = proxy(host = "proxy.example", secret = "eeaabbdd")

        val merged =
            TelegramMegaSource.mergeEntries(
                sourceId = "tg_mega",
                sourceName = "Telegram mega",
                phaseResults = listOf(listOf(first), listOf(duplicate, distinct)),
            )

        assertEquals(2, merged.size)
        assertTrue(merged.all { it.sourceId == "tg_mega" })
        assertTrue(merged.all { it.sourceName == "Telegram mega" })
        assertEquals(first.url, merged.first().url)
    }

    @Test
    fun collectPhaseResults_propagatesParentCancellation() = runTest {
        try {
            withTimeout(100L) {
                TelegramMegaSource.collectPhaseResults(
                    phaseTimeoutMs = 1_000L,
                    phases =
                        listOf(suspend {
                            delay(5_000L)
                            listOf(proxy("1.1.1.1"))
                        }),
                )
            }
            fail("Parent cancellation must not be converted into an empty phase")
        } catch (_: TimeoutCancellationException) {
            // Expected: only a phase's own deadline is converted to an empty result.
        }
    }

    @Test
    fun collectTaskResults_preservesWorkCompletedBeforeDeadline() = runTest {
        val results =
            TelegramMegaSource.collectTaskResults(
                timeoutMs = 300L,
                tasks =
                    listOf(
                        suspend {
                            delay(100L)
                            listOf(proxy("1.1.1.1"))
                        },
                        suspend {
                            delay(5_000L)
                            listOf(proxy("2.2.2.2"))
                        },
                        suspend {
                            delay(200L)
                            listOf(proxy("3.3.3.3"))
                        },
                    ),
            )

        assertEquals(listOf("1.1.1.1", "3.3.3.3"), results.map { it.host })
        assertEquals(300L, testScheduler.currentTime)
    }

    @Test
    fun normalizeBody_decodesEscapedTelegramQuery() {
        val normalized =
            TelegramMegaSource.normalizeBody(
                "tg%3A%2F%2Fproxy%3Fserver%3Dexample.org%26port%3D443&amp;secret=abc",
            )

        assertEquals(
            "tg://proxy?server=example.org&port=443&secret=abc",
            normalized,
        )
    }

    private fun proxy(
        host: String,
        secret: String = "ee0123456789abcdef0123456789abcdef",
    ): RawProxyEntry =
        RawProxyEntry(
            url = "tg://proxy?server=$host&port=443&secret=$secret",
            host = host,
            port = 443,
            secret = secret,
            sourceId = "phase",
            sourceName = "Phase",
        )
}
