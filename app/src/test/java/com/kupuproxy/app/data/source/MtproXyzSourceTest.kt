package com.kupuproxy.app.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MtproXyzSourceTest {

    @Test
    fun parseHostPortSecretJson_fromDecodedPayload() {
        val json =
            """[{"host":"1.2.3.4","port":443,"secret":"ee0123456789abcdef0123456789abcdef","country":"DE"},{"host":"9.9.9.9","port":8443,"secret":"dd0123456789abcdef0123456789abcdef"}]"""
        val decoded = """(function(){ if (window.location.hostname === "mtpro.xyz") { return $json; } })"""
        val r = MtproXyzSource.parseHostPortSecretJson(decoded)
        assertEquals(2, r.size)
        assertEquals("1.2.3.4", r[0].host)
        assertEquals(8443, r[1].port)
        assertTrue(r[0].url.startsWith("tg://proxy?"))
    }

    @Test
    fun parseHostPortSecretJson_emptyWhenNoArray() {
        val r = MtproXyzSource.parseHostPortSecretJson("no data here")
        assertTrue(r.isEmpty())
    }
}
