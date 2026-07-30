package com.kupuproxy.app.data.export

import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyExporterTest {
    @Test
    fun exportFilenameSanitizerProducesSafeName() {
        val sanitized = ProxyExporter::class.java.getDeclaredMethod("sanitizeFileName", String::class.java).apply {
            isAccessible = true
        }.invoke(ProxyExporter, "../../bad name.txt") as String

        assertTrue(sanitized.contains("bad_name.txt"))
    }
}
