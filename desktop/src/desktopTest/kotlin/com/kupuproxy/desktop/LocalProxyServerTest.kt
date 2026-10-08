package com.kupuproxy.desktop

import com.kupuproxy.shared.domain.model.ProxyProtocol
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Сквозные проверки локального прокси на loopback-серверах: сеть не нужна. */
class LocalProxyServerTest {

    private val closeables = mutableListOf<AutoCloseable>()
    private val local = LocalProxyServer()

    @AfterTest
    fun tearDown() {
        local.stop()
        closeables.forEach { runCatching { it.close() } }
    }

    /** Эхо-сервер: возвращает всё, что получил. */
    private fun echoServer(): Int {
        val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress()).also(closeables::add)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    client.use { runCatching { it.getInputStream().copyTo(it.getOutputStream().autoFlush()) } }
                }
            }
        }
        return server.localPort
    }

    /** Настоящий SOCKS5-ретранслятор (без авторизации, ATYP=domain/IPv4). */
    private fun socksRelay(): Int {
        val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress()).also(closeables::add)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    client.use { c ->
                        val inp = c.getInputStream()
                        val out = c.getOutputStream()
                        val greet = inp.readNBytes(2)
                        inp.readNBytes(greet[1].toInt())
                        out.write(byteArrayOf(5, 0)); out.flush()
                        val req = inp.readNBytes(4)
                        val host = when (req[3].toInt()) {
                            3 -> String(inp.readNBytes(inp.read()))
                            else -> inp.readNBytes(4).joinToString(".") { (it.toInt() and 0xFF).toString() }
                        }
                        val portBytes = inp.readNBytes(2)
                        val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
                        val upstream = Socket(host, port)
                        // BND.ADDR = 10.0.0.1 — первый байт адреса не ноль: старый код принимал его за REP.
                        out.write(byteArrayOf(5, 0, 0, 1, 10, 0, 0, 1, 0x04, 0x38)); out.flush()
                        relay(c, upstream)
                    }
                }
            }
        }
        return server.localPort
    }

    /** HTTP CONNECT-прокси. */
    private fun httpConnectProxy(): Int {
        val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress()).also(closeables::add)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    client.use { c ->
                        val head = readHead(c.getInputStream())
                        val authority = head.lineSequence().first().split(' ')[1]
                        val upstream = Socket(authority.substringBeforeLast(':'), authority.substringAfterLast(':').toInt())
                        c.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nX-Test: 1\r\n\r\n".toByteArray()); flush() }
                        relay(c, upstream)
                    }
                }
            }
        }
        return server.localPort
    }

    private fun relay(a: Socket, b: Socket) {
        val t = thread(isDaemon = true) { runCatching { a.getInputStream().copyTo(b.getOutputStream().autoFlush()) }; runCatching { b.shutdownOutput() } }
        runCatching { b.getInputStream().copyTo(a.getOutputStream().autoFlush()) }
        runCatching { a.shutdownOutput() }
        t.join(2_000)
        b.close()
    }

    private fun OutputStream.autoFlush(): OutputStream = object : OutputStream() {
        override fun write(b: Int) { this@autoFlush.write(b); this@autoFlush.flush() }
        override fun write(b: ByteArray, off: Int, len: Int) { this@autoFlush.write(b, off, len); this@autoFlush.flush() }
    }

    private fun readHead(input: InputStream): String {
        val sb = StringBuilder()
        while (!sb.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0) break
            sb.append(b.toChar())
        }
        return sb.toString()
    }

    /** Открывает CONNECT через локальный прокси и проверяет эхо. */
    private fun roundTrip(message: String, echoPort: Int): String {
        Socket().use { s ->
            s.soTimeout = 5_000
            s.connect(InetSocketAddress("127.0.0.1", local.localPort), 3_000)
            val out = s.getOutputStream()
            out.write("CONNECT 127.0.0.1:$echoPort HTTP/1.1\r\nHost: 127.0.0.1:$echoPort\r\n\r\n".toByteArray())
            out.flush()
            val head = readHead(s.getInputStream())
            assertTrue(head.startsWith("HTTP/1.1 200"), head)
            out.write(message.toByteArray()); out.flush()
            return String(s.getInputStream().readNBytes(message.length))
        }
    }

    @Test
    fun tunnelsThroughSocks5() {
        val echo = echoServer()
        local.start(LocalProxyServer.Target("127.0.0.1", socksRelay(), ProxyProtocol.SOCKS5, null, null))
        assertEquals("hello-socks", roundTrip("hello-socks", echo))
        // Счётчик обновляется сразу после записи клиенту — даём ему мгновение.
        val deadline = System.currentTimeMillis() + 2_000
        fun counted() = local.snapshot().let { it.upBytes >= "hello-socks".length && it.downBytes >= "hello-socks".length }
        while (!counted() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(local.snapshot().upBytes >= "hello-socks".length)
        assertTrue(local.snapshot().downBytes >= "hello-socks".length)
    }

    @Test
    fun tunnelsThroughHttpConnect() {
        val echo = echoServer()
        local.start(LocalProxyServer.Target("127.0.0.1", httpConnectProxy(), ProxyProtocol.HTTP, "u", "p"))
        assertEquals("hello-http", roundTrip("hello-http", echo))
    }

    @Test
    fun servesConnectionsConcurrently() {
        val echo = echoServer()
        local.start(LocalProxyServer.Target("127.0.0.1", socksRelay(), ProxyProtocol.SOCKS5, null, null))
        // Первое соединение держим открытым: раньше оно блокировало все последующие.
        Socket().use { first ->
            first.connect(InetSocketAddress("127.0.0.1", local.localPort), 3_000)
            first.getOutputStream().apply { write("CONNECT 127.0.0.1:$echo HTTP/1.1\r\n\r\n".toByteArray()); flush() }
            first.soTimeout = 5_000
            assertTrue(readHead(first.getInputStream()).startsWith("HTTP/1.1 200"))
            assertEquals("second", roundTrip("second", echo))
        }
    }

    @Test
    fun answersBadGatewayWhenUpstreamIsDead() {
        val dead = ServerSocket(0).use { it.localPort }
        local.start(LocalProxyServer.Target("127.0.0.1", dead, ProxyProtocol.SOCKS5, null, null))
        Socket("127.0.0.1", local.localPort).use { s ->
            s.soTimeout = 8_000
            s.getOutputStream().apply { write("CONNECT example.com:443 HTTP/1.1\r\n\r\n".toByteArray()); flush() }
            assertTrue(readHead(s.getInputStream()).startsWith("HTTP/1.1 502"))
        }
    }

    @Test
    fun refusesMtprotoTarget() {
        assertTrue(runCatching { local.start(LocalProxyServer.Target("h", 1, ProxyProtocol.MTPROTO, null, null)) }.isFailure)
        assertFalse(local.isRunning)
    }

    @Test
    fun parsesConnectAndAbsoluteFormRequests() {
        val connect = assertNotNull(local.parseRequest("CONNECT [2001:db8::1]:8443 HTTP/1.1\r\n\r\n"))
        assertEquals("2001:db8::1", connect.host)
        assertEquals(8443, connect.port)
        assertTrue(connect.isConnect)

        val get = assertNotNull(
            local.parseRequest("GET http://example.com/a?b=1 HTTP/1.1\r\nHost: example.com\r\nProxy-Connection: keep-alive\r\n\r\n"),
        )
        assertEquals("example.com", get.host)
        assertEquals(80, get.port)
        assertFalse(get.isConnect)
        assertTrue(get.originFormHead.startsWith("GET /a?b=1 HTTP/1.1\r\n"))
        assertFalse(get.originFormHead.contains("Proxy-Connection", ignoreCase = true))

        assertNull(local.parseRequest("GET /relative HTTP/1.1\r\n\r\n"))
        assertNull(local.parseRequest("CONNECT host:99999 HTTP/1.1\r\n\r\n"))
    }
}
