package com.kupuproxy.app.ui

import com.kupuproxy.app.R
import com.kupuproxy.app.domain.source.BuiltInSourceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceDisplayNamesTest {

    @Test
    fun exactTelegramDisplayNameUsesLocalizedResource() {
        assertEquals(
            R.string.source_telegram_mega_title,
            sourceNameResource("Telegram · mega"),
        )
    }

    @Test
    fun customRemoteSourceKeepsItsProvidedName() {
        assertNull(sourceNameResource("My private source"))
        assertEquals(
            "My private source",
            BuiltInSourceIdentity.insightKey("user_123", "My private source"),
        )
    }

    @Test
    fun builtInInsightUsesStableIdInsteadOfLocalizedDisplayName() {
        assertEquals(
            "tg_mega",
            BuiltInSourceIdentity.insightKey("tg_mega", "Telegram · мега-источники"),
        )
        assertEquals(
            "tg_mega",
            BuiltInSourceIdentity.insightKey("Telegram · mega", "Telegram · mega"),
        )
    }
}
