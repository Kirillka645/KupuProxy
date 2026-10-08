package com.kupuproxy.desktop

import com.kupuproxy.shared.domain.model.ProxyProtocol
import java.io.BufferedReader
import java.io.OutputStream
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Сквозной тест движка десктоп-клиента на локальных серверах-имитациях: сеть не нужна,
 * результат детерминированный.
 */
class ProxyEngineTest {

    private val servers = mutableListOf<ServerSocket>()
    private val threads = mutableListOf<Thread>()

    @AfterTest
    fun tearDown() {
        servers.forEach { runCatching { it.close() } }
        threads.forEach { runCatching { it.interrupt() } }
        servers.clear()
        threads.clear()
    }

    /** SOCKS5-сервер, который проходит рукопожатие и подтверждает CONNECT. */
    private fun startSocks5Server(acceptConnect: Boolean = true): Int {
        val server = ServerSocket(0)
        servers += server
        val ready = CountDownLatch(1)
        val t = Thread {
            ready.countDown()
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                try {
                    val input = client.getInputStream()
                    val out: OutputStream = client.getOutputStream()
                    val greeting = input.readNBytes(2)
                    if (greeting.size < 2) continue
                    input.readNBytes(greeting[1].toInt() and 0xFF)
                    out.write(byteArrayOf(0x05, 0x00))
                    out.flush()
                    val request = input.readNBytes(4)
                    if (request.size < 4) continue
                    val atyp = request[3].toInt() and 0xFF
                    input.readNBytes(if (atyp == 0x03) 1 else 4).also { n ->
                        if (atyp == 0x03) input.readNBytes(n[0].toInt() and 0xFF)
                    }
                    input.readNBytes(2)
                    out.write(
                        if (acceptConnect) {
                            byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0)
                        } else {
                            byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0)
                        },
                    )
                    out.flush()
                } catch (_: Exception) {
                    // разрывы не важны
                } finally {
                    runCatching { client.close() }
                }
            }
        }
        t.isDaemon = true
        threads += t
        t.start()
        ready.await(2, TimeUnit.SECONDS)
        return server.localPort
    }

    private fun startDeadPort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun findsWorkingSocksProxy() = runTest {
        val port = startSocks5Server()
        val engine = ProxyEngine()

        val rows = engine.scan(listOf("socks5://127.0.0.1:$port"), ScanConfig(jitterSamples = 1, maxToCheck = 50))

        assertEquals(1, rows.size, "expected one working proxy")
        assertEquals(ProxyProtocol.SOCKS5, rows[0].protocol)
        assertEquals("127.0.0.1", rows[0].host)
        assertEquals(port, rows[0].port)
        assertTrue(rows[0].latencyMs > 0)
    }

    @Test
    fun measuresJitterAcrossSamples() = runTest {
        val port = startSocks5Server()
        val engine = ProxyEngine()

        val rows = engine.scan(listOf("socks5://127.0.0.1:$port"), ScanConfig(jitterSamples = 3, maxToCheck = 50))
        assertEquals(1, rows.size)
        assertEquals(3, rows[0].samples, "expected three samples")
        assertTrue(rows[0].jitterMs >= 0)
    }

    @Test
    fun reportsProgressWhileScanning() = runTest {
        val port = startSocks5Server()
        val engine = ProxyEngine()
        val states = mutableListOf<ScanState>()

        engine.scan(
            urls = listOf("socks5://127.0.0.1:$port"),
            config = ScanConfig(jitterSamples = 1, maxToCheck = 50),
            onProgress = { states.add(it) },
        )

        assertTrue(states.isNotEmpty(), "progress must be reported")
        assertTrue(states.any { it.running }, "there must be a running state")
        assertEquals(false, states.last().running, "last state must be finished")
    }

    @Test
    fun scanDocumentDropsLoopbackAndPrivateHosts() = runTest {
        // Локальные адреса из внешнего источника принимать нельзя: так фильтрует парсер.
        val engine = ProxyEngine()
        val rows = engine.scanDocument(
            "socks5://127.0.0.1:1080\nsocks5://192.168.1.1:1080\nsocks5://10.0.0.1:1080",
            ScanConfig(),
        )
        assertTrue(rows.isEmpty(), "private and loopback proxies must be filtered out")
    }

    @Test
    fun scanDocumentParsesPublicProxy() = runTest {
        val engine = ProxyEngine()
        val states = mutableListOf<ScanState>()
        val rows = engine.scanDocument(
            "socks5://user:pass@proxy.example.org:1080",
            ScanConfig(jitterSamples = 1),
            onProgress = { states.add(it) },
        )
        // Публичный адрес недоступен из теста — важен сам факт разбора кандидата.
        assertEquals(0, rows.size)
        assertTrue(states.any { it.candidates == 1 }, "document must yield one candidate")
    }

    @Test
    fun skipsDeadEndpoints() = runTest {
        val dead = startDeadPort()
        val engine = ProxyEngine()

        val rows = engine.scan(listOf("socks5://127.0.0.1:$dead"), ScanConfig(jitterSamples = 1, maxToCheck = 50))
        assertTrue(rows.isEmpty(), "dead endpoint must not be reported")
    }

    @Test
    fun respectsProtocolFilter() = runTest {
        val port = startSocks5Server()
        val engine = ProxyEngine()

        val rows = engine.scan(
            urls = listOf("socks5://127.0.0.1:$port"),
            config = ScanConfig(protocolFilter = setOf(ProxyProtocol.MTPROTO), maxToCheck = 50),
        )
        assertTrue(rows.isEmpty(), "SOCKS must be skipped when MTProto-only")
    }

    @Test
    fun measuresJitterAcrossSamplesAlreadyCovered() = Unit

    @Test
    fun handlesEmptyDocument() = runTest {
        val engine = ProxyEngine()
        val rows = engine.scanDocument("", ScanConfig())
        assertTrue(rows.isEmpty())
    }

    @Test
    fun filtersBySearchAndLatency() {
        val rows = listOf(
            ProxyRow("tg://proxy?server=a.example&port=443&secret=s", "a.example", 443, ProxyProtocol.MTPROTO, 100, 5, 3),
            ProxyRow("socks5://b.example:1080", "b.example", 1080, ProxyProtocol.SOCKS5, 300, 10, 3),
            ProxyRow("http://c.example:8080", "c.example", 8080, ProxyProtocol.HTTP, 800, 20, 3),
        )

        assertEquals(3, ProxyFilter.apply(rows, ScanConfig()).size)
        assertEquals(1, ProxyFilter.apply(rows, ScanConfig(searchQuery = "b.example")).size)
        assertEquals(2, ProxyFilter.apply(rows, ScanConfig(maxLatencyFilterMs = 400)).size)
        assertEquals(
            1,
            ProxyFilter.apply(rows, ScanConfig(protocolFilter = setOf(ProxyProtocol.SOCKS5))).size,
        )
        assertEquals(1, ProxyFilter.apply(rows, ScanConfig(searchQuery = "HTTP")).size)
    }

    @Test
    fun sortsByLatencyThenJitter() {
        val rows = listOf(
            ProxyRow("u1", "h1", 1, ProxyProtocol.MTPROTO, 300, 10, 1),
            ProxyRow("u2", "h2", 2, ProxyProtocol.MTPROTO, 100, 5, 1),
            ProxyRow("u3", "h3", 3, ProxyProtocol.MTPROTO, 100, 1, 1),
        )
        val sorted = rows.sortedWith(compareBy({ it.latencyMs }, { it.jitterMs }))
        assertEquals(listOf("u3", "u2", "u1"), sorted.map { it.url })
    }

    @Test
    fun trafficStatsFormatsBytes() {
        val stats = TrafficStats(upBytes = 2048, downBytes = 5 * 1024 * 1024, connections = 3)
        assertEquals("2.0 КБ", stats.formatBytes(2048))
        assertTrue(stats.formatBytes(5 * 1024 * 1024).endsWith("МБ"))
        assertEquals("0 Б", stats.formatBytes(0))
        assertEquals(5 * 1024 * 1024 + 2048, stats.totalBytes)
    }

    @Test
    fun localProxyRefusesMtprotoTarget() {
        val target = LocalProxyServer.Target("h", 1, ProxyProtocol.MTPROTO, null, null)
        assertFalse(target.supportsTunnelling)
    }

    @Test
    fun progressNeverExceedsTotal() = runTest {
        val port = startSocks5Server()
        val dead = startDeadPort()
        val states = java.util.Collections.synchronizedList(mutableListOf<ScanState>())
        val engine = ProxyEngine()
        engine.scan(
            urls = listOf("socks5://127.0.0.1:$port", "socks5://127.0.0.1:$dead", "socks5://u:p@127.0.0.1:$port"),
            config = ScanConfig(jitterSamples = 1, maxToCheck = 50),
            onProgress = { states += it },
        )
        assertTrue(states.isNotEmpty())
        assertTrue(states.all { it.processed <= it.total }, states.toString())
    }
}
