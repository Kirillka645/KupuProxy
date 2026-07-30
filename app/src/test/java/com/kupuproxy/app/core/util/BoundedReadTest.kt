package com.kupuproxy.app.core.util

import java.io.IOException
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Регрессионные тесты на баг, из-за которого сломалось автообновление в 1.3.3.3+:
 * okio `readByteArray(n)` — точное чтение и бросает EOFException, если байт меньше n.
 */
class BoundedReadTest {

    @Test
    fun smallPayloadUnderLargeLimitIsReadFully() {
        val json = """{"tag_name":"v1.3.3.8"}"""
        val source = Buffer().writeUtf8(json)
        // Раньше здесь падал EOFException — именно это ломало проверку обновлений.
        assertEquals(json, source.readUtf8Bounded(512L * 1024))
    }

    @Test
    fun payloadExactlyAtLimitIsAccepted() {
        val payload = "a".repeat(100)
        assertEquals(payload, Buffer().writeUtf8(payload).readUtf8Bounded(100))
    }

    @Test(expected = IOException::class)
    fun payloadOverLimitIsRejected() {
        Buffer().writeUtf8("b".repeat(2_000)).readUtf8Bounded(100)
    }

    @Test
    fun emptyPayloadDoesNotThrow() {
        assertEquals("", Buffer().readUtf8Bounded(1_024))
    }

    @Test
    fun realisticChecksumFileIsParsed() {
        val line = "75c7e237d71a3847ec8b059d3436ec01b5b7704d1212baa770378640c1a8e569  KupuProxy-v1.3.3.8.apk\n"
        assertEquals(line, Buffer().writeUtf8(line).readUtf8Bounded(1_024))
    }
}
