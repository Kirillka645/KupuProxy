package com.kupuproxy.desktop

import com.kupuproxy.shared.domain.model.ProxyProtocol
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Локальный HTTP CONNECT-перехватчик, который перенаправляет трафик через выбранный прокси.
 *
 * MTProto не является универсальным туннелем, поэтому через него маршрутизация невозможна —
 * для него доступен только режим «открыть в Telegram». SOCKS5 и HTTP/WEB-поддерживаются полностью.
 */
class LocalProxyServer {

    data class Target(val host: String, val port: Int, val protocol: ProxyProtocol, val username: String?, val password: String?) {
        val supportsTunnelling: Boolean get() = protocol != ProxyProtocol.MTPROTO
    }

    private val upBytes = AtomicLong(0)
    private val downBytes = AtomicLong(0)
    private val connections = AtomicInteger(0)
    private val history = ArrayList<Long>()

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var thread: Thread? = null
    @Volatile private var target: Target? = null

    val isRunning: Boolean get() = serverSocket?.isClosed == false
    val localPort: Int get() = serverSocket?.localPort ?: 0

    fun snapshot(): TrafficStats = TrafficStats(
        upBytes = upBytes.get(),
        downBytes = downBytes.get(),
        connections = connections.get(),
        history = synchronized(history) { history.toList() },
    )

    fun resetStats() {
        upBytes.set(0)
        downBytes.set(0)
        connections.set(0)
        synchronized(history) { history.clear() }
    }

    /** Запускает слушатель на случайном свободном порту. */
    fun start(newTarget: Target) {
        stop()
        target = newTarget
        val socket = ServerSocket(0, 64, java.net.InetAddress.getLoopbackAddress())
        serverSocket = socket
        val worker = Thread({ acceptLoop(socket, newTarget) }, "kupu-proxy-server")
        worker.isDaemon = true
        worker.start()
        thread = worker
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        thread = null
        target = null
    }

    private fun acceptLoop(socket: ServerSocket, activeTarget: Target) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: Exception) {
                break
            }
            client.use { handle(it, activeTarget) }
        }
    }

    private fun handle(client: Socket, activeTarget: Target) {
        try {
            client.tcpNoDelay = true
            client.soTimeout = 15_000
            val input = client.getInputStream()
            val request = readRequestHead(input) ?: return
            val (method, host, port) = parseConnectRequest(request) ?: run {
                writeSimpleError(client.getOutputStream(), 400, "Bad Request")
                return
            }

            val upstream = openUpstream(activeTarget, host, port) ?: run {
                writeSimpleError(client.getOutputStream(), 502, "Bad Gateway")
                return
            }

            client.getOutputStream().write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
            client.getOutputStream().flush()
            connections.incrementAndGet()

            pump(client, upstream)
        } catch (_: Exception) {
            // Ошибка отдельного соединения не должна ронять слушатель.
        }
    }

    /** Открывает туннель до целевого хоста через выбранный прокси. */
    private fun openUpstream(proxy: Target, host: String, port: Int): Socket? {
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(proxy.host, proxy.port), 6_000)
        socket.soTimeout = 15_000

        when (proxy.protocol) {
            ProxyProtocol.SOCKS5 -> {
                val out = socket.getOutputStream()
                val inp = socket.getInputStream()
                if (proxy.username.isNullOrEmpty()) {
                    out.write(byteArrayOf(0x05, 0x01, 0x00)); out.flush()
                    require(readByte(inp) == 0x05) { "bad socks version" }
                    require(readByte(inp) == 0x00) { "socks: auth required" }
                } else {
                    out.write(byteArrayOf(0x05, 0x02, 0x02, 0x00)); out.flush()
                    require(readByte(inp) == 0x05) { "bad socks version" }
                    require(readByte(inp) == 0x02) { "socks: auth not selected" }
                    val user = proxy.username.toByteArray()
                    val pass = proxy.password.orEmpty().toByteArray()
                    out.write(byteArrayOf(0x01, user.size.toByte())); out.write(user)
                    out.write(pass.size); out.write(pass); out.flush()
                    require(readByte(inp) == 0x00) { "socks: auth rejected" }
                }

                val hostBytes = host.toByteArray(Charsets.US_ASCII)
                out.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()))
                out.write(hostBytes)
                out.write((port shr 8) and 0xFF)
                out.write(port and 0xFF)
                out.flush()

                readExactly(inp, 4)
                require(readByte(inp) == 0x00) { "socks: connect refused" }
                when (readByte(inp).toInt() and 0xFF) {
                    0x01 -> readExactly(inp, 4 + 2)
                    0x04 -> readExactly(inp, 16 + 2)
                    0x03 -> readExactly(inp, 1).also { readExactly(inp, (it[0].toInt() and 0xFF) + 2) }
                    else -> error("socks: bad atyp")
                }
            }

            ProxyProtocol.HTTP, ProxyProtocol.WEB -> {
                var socketRef: Socket = socket
                if (proxy.protocol == ProxyProtocol.WEB) {
                    val ssl = javax.net.ssl.SSLSocketFactory.getDefault()
                        .createSocket() as javax.net.ssl.SSLSocket
                    ssl.tcpNoDelay = true
                    ssl.connect(InetSocketAddress(proxy.host, proxy.port), 6_000)
                    ssl.useClientMode = true
                    ssl.startHandshake()
                    ssl.soTimeout = 15_000
                    socketRef = ssl
                }
                val out = socketRef.getOutputStream()
                val inp = socketRef.getInputStream()

                val sb = StringBuilder()
                sb.append("CONNECT ").append(host).append(':').append(port).append(" HTTP/1.1\r\n")
                sb.append("Host: ").append(host).append(':').append(port).append("\r\n")
                if (!proxy.username.isNullOrEmpty()) {
                    val token = java.util.Base64.getEncoder()
                        .encodeToString("${proxy.username}:${proxy.password.orEmpty()}".toByteArray())
                    sb.append("Proxy-Authorization: Basic ").append(token).append("\r\n")
                }
                sb.append("\r\n")
                out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
                out.flush()

                val statusLine = readLine(inp)
                val status = statusLine?.split(' ')?.getOrNull(1)?.toIntOrNull()
                require(status != null && status in 200..299) { "http: status ${status ?: "?"}" }
                while (readLine(inp)?.isNotEmpty() == true) { /* съедаем заголовки */ }
            }

            ProxyProtocol.MTPROTO -> error("MTProto не поддерживает туннелирование")
        }
        socket.soTimeout = 0
        return socket
    }

    /** Двунаправленная перекачка с учётом трафика. */
    private fun pump(client: Socket, upstream: Socket) {
        val up = Thread({
            runCatching {
                copy(client.getInputStream(), upstream.getOutputStream()) { n -> upBytes.addAndGet(n.toLong()) }
            }
            runCatching { upstream.shutdownOutput() }
        }, "kupu-pump-up")
        up.isDaemon = true

        runCatching {
            copy(upstream.getInputStream(), client.getOutputStream()) { n ->
                downBytes.addAndGet(n.toLong())
                synchronized(history) {
                    history.add(downBytes.get())
                    if (history.size > 120) history.removeAt(0)
                }
            }
        }
        runCatching { client.shutdownOutput() }
        up.start()
        up.join(30_000)
        runCatching { upstream.close() }
    }

    private inline fun copy(from: InputStream, to: OutputStream, onBytes: (Int) -> Unit) {
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val read = from.read(buffer)
            if (read < 0) break
            to.write(buffer, 0, read)
            to.flush()
            onBytes(read)
        }
    }

    // region HTTP helpers

    private fun readRequestHead(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 16 * 1024) {
            val line = readLine(input) ?: return null
            sb.append(line).append('\n')
            if (line.isEmpty()) break
        }
        return sb.toString()
    }

    private fun parseConnectRequest(head: String): Triple<String, String, Int>? {
        val first = head.lineSequence().firstOrNull()?.trim() ?: return null
        val parts = first.split(' ')
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val authority = parts[1]
        val colon = authority.lastIndexOf(':')
        if (colon <= 0) return null
        val host = authority.substring(0, colon)
        val port = authority.substring(colon + 1).toIntOrNull() ?: return null
        return Triple(method, host, port)
    }

    private fun writeSimpleError(out: OutputStream, code: Int, reason: String) {
        runCatching {
            out.write("HTTP/1.1 $code $reason\r\nContent-Length: 0\r\n\r\n".toByteArray())
            out.flush()
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder(64)
        var previous = -1
        while (sb.length < 8 * 1024) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            sb.append(b.toChar())
            if (previous == '\r'.code && b == '\n'.code) return sb.toString().trimEnd('\r', '\n')
            previous = b
        }
        return sb.toString()
    }

    private fun readByte(input: InputStream): Int = input.read()

    private fun readExactly(input: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = input.read(out, offset, n - offset)
            if (read < 0) throw IOException("closed")
            offset += read
        }
        return out
    }

    // endregion
}