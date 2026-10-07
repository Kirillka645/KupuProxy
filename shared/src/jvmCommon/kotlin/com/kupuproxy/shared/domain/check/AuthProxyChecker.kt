package com.kupuproxy.shared.domain.check

import com.kupuproxy.shared.domain.model.ProxyProtocol
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Проверка прокси с авторизацией: SOCKS5, HTTP CONNECT и WEB (CONNECT поверх TLS).
 *
 * Логика общая для всех трёх: подключаемся к прокси, выполняем штатное рукопожатие протокола
 * и добираемся до публичной точки Telegram. Ответ прокси без корректного кода — прокси нерабочий.
 */
object AuthProxyChecker {

    /** Публичная точка для CONNECT-запроса: можно достучаться из любой страны без учётной записи. */
    private const val PROBE_HOST = "www.google.com"
    private const val PROBE_PORT = 443

    private const val SOCKS_VERSION = 0x05
    private const val SOCKS_AUTH_NONE = 0x00
    private const val SOCKS_AUTH_USERPASS = 0x02
    private const val SOCKS_AUTH_UNACCEPTABLE = 0xFF
    private const val SOCKS_CMD_CONNECT = 0x01
    private const val SOCKS_ADDR_DOMAIN = 0x03
    private const val SOCKS_REPLY_SUCCEEDED = 0x00

    private const val HTTP_MAX_STATUS_LINE = 8 * 1024

    fun check(
        protocol: ProxyProtocol,
        host: String,
        port: Int,
        username: String?,
        password: String?,
        connectTimeoutMs: Int,
        responseTimeoutMs: Int,
    ): ProbeResult {
        val started = System.currentTimeMillis()
        var socket: Socket? = null
        return try {
            socket = openSocket(host, port, protocol, connectTimeoutMs)
            socket.soTimeout = responseTimeoutMs.coerceIn(800, 3_500)

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            when (protocol) {
                ProxyProtocol.SOCKS5 -> {
                    socksHandshake(input, output, username, password)
                    socksConnect(input, output, PROBE_HOST, PROBE_PORT)
                }
                ProxyProtocol.HTTP, ProxyProtocol.WEB -> {
                    httpConnect(input, output, PROBE_HOST, PROBE_PORT, username, password)
                }
                ProxyProtocol.MTPROTO -> error("MTProto проверяется отдельно")
            }

            val elapsed = (System.currentTimeMillis() - started).toInt().coerceAtLeast(1)
            ProbeResult(
                ok = true,
                protocol = protocol,
                latencyMs = elapsed,
                samples = listOf(elapsed),
                tcpReachable = true,
            )
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - started).toInt().coerceAtLeast(0)
            ProbeResult(
                ok = false,
                protocol = protocol,
                latencyMs = elapsed,
                tcpReachable = socket?.isConnected == true,
                error = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "unavailable",
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    private fun openSocket(
        host: String,
        port: Int,
        protocol: ProxyProtocol,
        connectTimeoutMs: Int,
    ): Socket {
        // WEB-прокси принимает соединение по TLS: без рукопожатия CONNECT-строка не прочитается.
        if (protocol == ProxyProtocol.WEB) {
            val ssl = SSLSocketFactory.getDefault().createSocket() as SSLSocket
            ssl.tcpNoDelay = true
            ssl.connect(InetSocketAddress(host, port), connectTimeoutMs.coerceIn(600, 2_500))
            ssl.useClientMode = true
            ssl.startHandshake()
            return ssl
        }
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs.coerceIn(600, 2_500))
        return socket
    }

    // region SOCKS5 (RFC 1928 / RFC 1929)

    private fun socksHandshake(input: InputStream, output: OutputStream, username: String?, password: String?) {
        val hasCredentials = !username.isNullOrEmpty()
        val methods = if (hasCredentials) {
            byteArrayOf(SOCKS_VERSION.toByte(), 0x02, SOCKS_AUTH_USERPASS.toByte(), SOCKS_AUTH_NONE.toByte())
        } else {
            byteArrayOf(SOCKS_VERSION.toByte(), 0x01, SOCKS_AUTH_NONE.toByte())
        }
        output.write(methods)
        output.flush()

        val greeting = MtprotoChecker.readExactStream(input, 2)
        require(greeting[0].toInt() and 0xFF == SOCKS_VERSION) { "socks: bad version" }
        val chosen = greeting[1].toInt() and 0xFF
        require(chosen != SOCKS_AUTH_UNACCEPTABLE) { "socks: no acceptable auth" }

        if (chosen == SOCKS_AUTH_USERPASS) {
            require(hasCredentials) { "socks: auth required" }
            val user = username!!.toByteArray(StandardCharsets.UTF_8)
            val pass = password.orEmpty().toByteArray(StandardCharsets.UTF_8)
            require(user.size <= 255 && pass.size <= 255) { "socks: credential too long" }
            output.write(byteArrayOf(0x01, user.size.toByte()))
            output.write(user)
            output.write(pass.size and 0xFF)
            output.write(pass)
            output.flush()

            val authReply = MtprotoChecker.readExactStream(input, 2)
            require(authReply[1].toInt() and 0xFF == 0x00) { "socks: auth rejected" }
        } else {
            require(!hasCredentials) { "socks: credentials ignored" }
        }
    }

    private fun socksConnect(input: InputStream, output: OutputStream, host: String, port: Int) {
        val hostBytes = host.toByteArray(StandardCharsets.US_ASCII)
        require(hostBytes.size <= 255) { "socks: host too long" }

        val request = ByteArrayOutputStream(262).apply {
            write(SOCKS_VERSION)
            write(SOCKS_CMD_CONNECT)
            write(0x00) // RSV
            write(SOCKS_ADDR_DOMAIN)
            write(hostBytes.size)
            write(hostBytes)
            write((port shr 8) and 0xFF)
            write(port and 0xFF)
        }
        output.write(request.toByteArray())
        output.flush()

        val header = MtprotoChecker.readExactStream(input, 4)
        require(header[0].toInt() and 0xFF == SOCKS_VERSION) { "socks: bad reply version" }
        val reply = header[1].toInt() and 0xFF
        require(reply == SOCKS_REPLY_SUCCEEDED) { "socks: reply 0x${reply.toString(16)}" }

        // Читаем остаток адресного блока до конца REP.
        when (header[3].toInt() and 0xFF) {
            0x01 -> MtprotoChecker.readExactStream(input, 4 + 2)
            0x04 -> MtprotoChecker.readExactStream(input, 16 + 2)
            0x03 -> {
                val len = MtprotoChecker.readExactStream(input, 1)[0].toInt() and 0xFF
                MtprotoChecker.readExactStream(input, len + 2)
            }
            else -> error("socks: unknown address type")
        }
    }

    // endregion

    // region HTTP / WEB CONNECT

    private fun httpConnect(
        input: InputStream,
        output: OutputStream,
        host: String,
        port: Int,
        username: String?,
        password: String?,
    ) {
        val sb = StringBuilder()
        sb.append("CONNECT ").append(host).append(':').append(port).append(" HTTP/1.1\r\n")
        sb.append("Host: ").append(host).append(':').append(port).append("\r\n")
        if (!username.isNullOrEmpty()) {
            val token = base64("$username:${password.orEmpty()}")
            sb.append("Proxy-Authorization: Basic ").append(token).append("\r\n")
        }
        sb.append("Proxy-Connection: keep-alive\r\n")
        sb.append("\r\n")

        output.write(sb.toString().toByteArray(StandardCharsets.ISO_8859_1))
        output.flush()

        val statusLine = MtprotoChecker.readLine(input, HTTP_MAX_STATUS_LINE)
        require(statusLine.isNotBlank()) { "http: empty response" }
        val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
        require(status != null && status in 200..299) { "http: status ${status ?: "?"}" }

        // Съедаем остальные заголовки до пустой строки.
        while (true) {
            val line = MtprotoChecker.readLine(input, HTTP_MAX_STATUS_LINE)
            if (line.isEmpty()) break
        }
    }

    private fun base64(value: String): String =
        java.util.Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    // endregion
}