package com.kupuproxy.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProxyCacheAtomicWriteTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun atomicWriteReplacesPreviousContentAndCleansTemporaryFiles() {
        val target = temporaryFolder.newFile("state.json")
        target.writeText("old")

        ProxyCache.atomicWrite(target, "new-state")

        assertEquals("new-state", target.readText())
        assertFalse(java.io.File(target.parentFile, "state.json.tmp").exists())
        assertFalse(java.io.File(target.parentFile, "state.json.bak").exists())
    }

    @Test
    fun readerRecoversBackupLeftByInterruptedRename() {
        val target = java.io.File(temporaryFolder.root, "favorites.json")
        val backup = java.io.File(temporaryFolder.root, "favorites.json.bak")
        val temporary = java.io.File(temporaryFolder.root, "favorites.json.tmp")
        backup.writeText("old-safe-state")
        temporary.writeText("unfinished-new-state")

        val recovered = ProxyCache.recoverAtomicFile(target)

        assertEquals(target, recovered)
        assertEquals("old-safe-state", target.readText())
        assertFalse(backup.exists())
        assertFalse(temporary.exists())
    }

    @Test
    fun concurrentWritesNeverLeavePartialOrRecoveryFiles() {
        val target = java.io.File(temporaryFolder.root, "state.json")
        val values = (1..24).map { index -> "value-$index-" + "x".repeat(4_096) }
        val threads = values.map { value -> Thread { ProxyCache.atomicWrite(target, value) } }

        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertTrue(target.readText() in values)
        assertFalse(java.io.File(target.parentFile, "state.json.tmp").exists())
        assertFalse(java.io.File(target.parentFile, "state.json.bak").exists())
    }

    @Test
    fun concurrentReadersOnlyObserveCompleteGenerations() {
        val target = java.io.File(temporaryFolder.root, "state.json")
        val first = "a".repeat(32_768)
        val second = "b".repeat(32_768)
        ProxyCache.atomicWrite(target, first)
        val observed = java.util.Collections.synchronizedList(mutableListOf<String?>())
        val writer = Thread { repeat(40) { ProxyCache.atomicWrite(target, if (it % 2 == 0) second else first) } }
        val reader = Thread { repeat(200) { observed += ProxyCache.atomicReadText(target) } }

        writer.start()
        reader.start()
        writer.join()
        reader.join()

        assertTrue(observed.isNotEmpty())
        assertTrue(observed.all { it == first || it == second })
    }
}
