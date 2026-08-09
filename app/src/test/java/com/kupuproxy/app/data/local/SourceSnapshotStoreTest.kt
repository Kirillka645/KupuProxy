package com.kupuproxy.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SourceSnapshotStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun cacheKeyIsStableAndDoesNotExposeSourceId() {
        val first = SourceSnapshotStore.keyFor("https://example.org/private/source")
        val second = SourceSnapshotStore.keyFor("https://example.org/private/source")
        assertEquals(first, second)
        assertEquals(24, first.length)
        assertNotEquals("https://example.org/private/source", first)
    }

    @Test
    fun differentSourcesUseDifferentFiles() {
        assertNotEquals(SourceSnapshotStore.keyFor("source-a"), SourceSnapshotStore.keyFor("source-b"))
    }

    @Test
    fun cleanupDeletesExpiredAndCorruptSnapshotsButKeepsFreshOnes() {
        val now = 1_000_000_000L
        val fresh = temporaryFolder.newFile("fresh.json")
        val expired = temporaryFolder.newFile("expired.json")
        val corrupt = temporaryFolder.newFile("corrupt.json")
        fresh.writeText("{\"savedAt\":$now,\"urls\":[\"tg://proxy\"]}")
        expired.writeText("{\"savedAt\":${now - SourceSnapshotStore.MAX_AGE_MS - 1},\"urls\":[]}")
        corrupt.writeText("not-json")

        SourceSnapshotStore.cleanupDirectory(temporaryFolder.root, now)

        assertTrue(fresh.exists())
        assertFalse(expired.exists())
        assertFalse(corrupt.exists())
    }

    @Test
    fun cleanupRecoversLegacyBackupAndDeletesUncommittedNewFile() {
        val now = 2_000_000_000L
        val base = temporaryFolder.newFile("source.json")
        val backup = temporaryFolder.newFile("source.json.bak")
        val uncommitted = temporaryFolder.newFile("other.json.new")
        base.writeText("corrupt-new-base")
        backup.writeText("{\"savedAt\":$now,\"urls\":[\"tg://proxy\"]}")
        uncommitted.writeText("unfinished")

        SourceSnapshotStore.cleanupDirectory(temporaryFolder.root, now)

        assertTrue(base.exists())
        assertTrue(base.readText().contains("tg://proxy"))
        assertFalse(backup.exists())
        assertFalse(uncommitted.exists())
    }
}
