package com.kupuproxy.shared.domain.check

import com.kupuproxy.shared.domain.model.ProxyProtocol
import com.kupuproxy.shared.domain.parser.ProxyParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Проверки SOCKS5/HTTP выполняются против локального сервера-имитации: сеть не нужна,
 * результат детерминированный.
 */
class AuthProxyCheckerTest {

    private val servers = mutableListOf<ServerSocket>()
    private val threads = mutableListOf<Thread>()

    @AfterTest
    fun tearDown() {
        servers.forEach { runCatching { it.close() } }
        threads.forEach { runCatching { it.interrupt() } }
        servers.clear()
        threads.clear()
    }

    /** Поднимает сервер, который отвечает по заданному сценарию, и возвращает его порт. */
    private fun startServer(handler: (Socket) -> Unit): Int {
        val server = ServerSocket(0)
        servers += server
        val ready = CountDownLatch(1)
        val thread = Thread {
            ready.countDown()
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                try {
                    handler(client)
                } catch (_: Exception) {
                    // серверный сбой не важен для теста
                } finally {
                    runCatching { client.close() }
                }
            }
        }
        thread.isDaemon = true
        threads += thread
        thread.start()
        ready.await(2, TimeUnit.SECONDS)
        return server.localPort
    }

    private fun Socket.readLine(): String =
        BufferedReader(InputStreamReader(getInputStream(), Charsets.ISO_8859_1)).readLine().orEmpty()

    // region SOCKS5

    @Test
    fun socks5ReportsSuccessOnValidHandshake() {
        val port = startServer { socket ->
            val input = socket.getInputStream()
            val out: OutputStream = socket.getOutputStream()

            // Приветствие клиента: VER NMETHODS METHODS...
            val greeting = input.readNBytes(2)
            require(greeting.size == 2 && greeting[0] == 0x05.toByte())
            val methodCount = greeting[1].toInt() and 0xFF
            input.readNBytes(methodCount)
            // Выбираем «без аутентификации».
            out.write(byteArrayOf(0x05, 0x00))
            out.flush()

            // CONNECT: VER CMD RSV ATYP LEN host port
            val request = input.readNBytes(4)
            require(request.size == 4)
            require(request[0] == 0x05.toByte())
            require(request[1] == 0x01.toByte())
            val atyp = request[3].toInt() and 0xFF
            require(atyp == 0x03) { "expected domain atyp, got $atyp" }
            val hostLen = input.readNBytes(1)[0].toInt() and 0xFF
            input.readNBytes(hostLen)
            input.readNBytes(2)

            // Успешный REP с IPv4-адресом привязки.
            out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            out.flush()
        }

        val entry = assertNotNull(ProxyParser.fromUrl("socks5://127.0.0.1:$port"))
        val result = AuthProxyChecker.check(
            ProxyProtocol.SOCKS5, entry.host, entry.port, entry.username, entry.password, 1_000, 1_000,
        )
        assertTrue(result.ok, "expected success, got ${result.error}")
        assertEquals(ProxyProtocol.SOCKS5, result.protocol)
    }

    @Test
    fun socks5ReportsFailureOnNonZeroReply() {
        val port = startServer { socket ->
            val input = socket.getInputStream()
            val out: OutputStream = socket.getOutputStream()
            val greeting = input.readNBytes(2)
            input.readNBytes(greeting[1].toInt() and 0xFF)
            out.write(byteArrayOf(0x05, 0x00))
            out.flush()

            val request = input.readNBytes(4)
            val atyp = request[3].toInt() and 0xFF
            val hostLen = if (atyp == 0x03) input.readNBytes(1)[0].toInt() and 0xFF else 4
            input.readNBytes(hostLen + 2)

            // REP = 0x05 — connection refused по RFC 1928.
            out.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            out.flush()
        }

        val result = AuthProxyChecker.check(ProxyProtocol.SOCKS5, "127.0.0.1", port, null, null, 1_000, 1_000)
        assertFalse(result.ok)
        assertTrue(result.error?.contains("reply") == true, "unexpected error ${result.error}")
    }

    @Test
    fun socks5PerformsUserPassAuth() {
        var sawUser = false
        var sawPass = false
        val port = startServer { socket ->
            val input = socket.getInputStream()
            val out: OutputStream = socket.getOutputStream()

            val greeting = input.readNBytes(2)
            input.readNBytes(greeting[1].toInt() and 0xFF)
            out.write(byteArrayOf(0x05, 0x02)) // выбираем user/pass
            out.flush()

            val verLen = input.readNBytes(2)
            val userLen = verLen[1].toInt() and 0xFF
            sawUser = String(input.readNBytes(userLen)) == "alice"
            val passLen = input.readNBytes(1)[0].toInt() and 0xFF
            sawPass = String(input.readNBytes(passLen)) == "s3cret"
            out.write(byteArrayOf(0x01, 0x00)) // auth ok
            out.flush()

            val request = input.readNBytes(4)
            val hostLen = input.readNBytes(1)[0].toInt() and 0xFF
            input.readNBytes(hostLen + 2)
            out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            out.flush()
        }

        val result = AuthProxyChecker.check(ProxyProtocol.SOCKS5, "127.0.0.1", port, "alice", "s3cret", 1_000, 1_000)
        assertTrue(result.ok, "expected success, got ${result.error}")
        assertTrue(sawUser, "username not transmitted")
        assertTrue(sawPass, "password not transmitted")
    }

    @Test
    fun socks5ReportsFailureOnUnacceptableAuth() {
        val port = startServer { socket ->
            val out: OutputStream = socket.getOutputStream()
            out.write(byteArrayOf(0x05, 0xFF.toByte())) // no acceptable methods
            out.flush()
        }

        val result = AuthProxyChecker.check(ProxyProtocol.SOCKS5, "127.0.0.1", port, null, null, 1_000, 1_000)
        assertFalse(result.ok)
        assertTrue(result.error?.contains("acceptable") == true, "unexpected error ${result.error}")
    }

    // endregion

    // region HTTP

    @Test
    fun httpReportsSuccessOnConnect200() {
        val port = startServer { socket ->
            val input = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
            val request = StringBuilder()
            while (true) {
                val line = input.readLine() ?: break
                request.append(line).append('\n')
                if (line.isEmpty()) break
            }
            val out: OutputStream = socket.getOutputStream()
            out.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
            out.flush()
            request.toString()
        }

        val result = AuthProxyChecker.check(ProxyProtocol.HTTP, "127.0.0.1", port, null, null, 1_000, 1_000)
        assertTrue(result.ok, "expected success, got ${result.error}")
    }

    @Test
    fun httpReportsFailureOn403() {
        val port = startServer { socket ->
            val input = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
            }
            socket.getOutputStream().write("HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray())
            socket.getOutputStream().flush()
        }

        val result = AuthProxyChecker.check(ProxyProtocol.HTTP, "127.0.0.1", port, null, null, 1_000, 1_000)
        assertFalse(result.ok)
        assertTrue(result.error?.contains("403") == true, "unexpected error ${result.error}")
    }

    @Test
    fun httpSendsBasicAuthHeaderWhenCredentialsGiven() {
        var seen: String? = null
        val port = startServer { socket ->
            val input = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
            while (true) {
                val line = input.readLine() ?: break
                if (line.startsWith("Proxy-Authorization", ignoreCase = true)) seen = line
                if (line.isEmpty()) break
            }
            socket.getOutputStream().write("HTTP/1.1 200 OK\r\n\r\n".toByteArray())
            socket.getOutputStream().flush()
        }

        val result = AuthProxyChecker.check(ProxyProtocol.HTTP, "127.0.0.1", port, "u", "p", 1_000, 1_000)
        assertTrue(result.ok)
        val expected = "Proxy-Authorization: Basic " +
            java.util.Base64.getEncoder().encodeToString("u:p".toByteArray())
        assertEquals(expected, seen)
    }

    // endregion

    // region Недоступные endpoint'ы

    @Test
    fun reportsFailureForClosedPort() {
        // Заведомо свободный порт.
        val probe = ServerSocket(0).use { it.localPort }
        val result = AuthProxyChecker.check(ProxyProtocol.HTTP, "127.0.0.1", probe, null, null, 500, 500)
        assertFalse(result.ok)
    }

    @Test
    fun tcpPreflightRejectsClosedPort() {
        val probe = ServerSocket(0).use { it.localPort }
        assertFalse(ProxyProbe.isTcpReachable("127.0.0.1", probe, 500))
    }

    @Test
    fun tcpPreflightAcceptsListeningPort() {
        val port = startServer { socket ->
            val out: OutputStream = socket.getOutputStream()
            out.write(byteArrayOf(0x05, 0xFF.toByte()))
            out.flush()
        }
        assertTrue(ProxyProbe.isTcpReachable("127.0.0.1", port, 1_000))
    }

    @Test
    fun probeUrlRejectsUnsupportedUrl() {
        val result = ProxyProbe.probeUrl("not-a-proxy", 500, 500)
        assertFalse(result.ok)
        assertEquals("bad_url", result.error)
    }

    @Test
    fun probeUrlReachesSocksEndpointThroughPreflight() {
        val port = startServer { socket ->
            val input = socket.getInputStream()
            val out: OutputStream = socket.getOutputStream()
            val greeting = input.readNBytes(2)
            input.readNBytes(greeting[1].toInt() and 0xFF)
            out.write(byteArrayOf(0x05, 0x00))
            out.flush()

            val request = input.readNBytes(4)
            val hostLen = input.readNBytes(1)[0].toInt() and 0xFF
            input.readNBytes(hostLen + 2)
            out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            out.flush()
        }

        val result = ProxyProbe.probeUrl("socks5://127.0.0.1:$port", 1_000, 1_000)
        assertTrue(result.ok, "expected ok, got ${result.error}")
        assertTrue(result.tcpReachable)
    }

    // endregion
}