package com.kupuproxy.desktop

import com.kupuproxy.shared.domain.model.ProxyProtocol
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Локальный HTTP-прокси на 127.0.0.1, который перенаправляет трафик через выбранный прокси.
 *
 * Поддерживается `CONNECT` (HTTPS и любые TCP-туннели) и обычные HTTP-запросы в
 * absolute-form (`GET http://host/path`) — их переписываем в origin-form и отправляем
 * через тот же туннель.
 *
 * MTProto не является универсальным туннелем, поэтому через него маршрутизация невозможна —
 * для него доступен только режим «открыть в Telegram». SOCKS5 и HTTP/WEB поддерживаются полностью.
 */
class LocalProxyServer {

    data class Target(val host: String, val port: Int, val protocol: ProxyProtocol, val username: String?, val password: String?) {
        val supportsTunnelling: Boolean get() = protocol != ProxyProtocol.MTPROTO
    }

    private val upBytes = AtomicLong(0)
    private val downBytes = AtomicLong(0)
    private val connections = AtomicInteger(0)
    private val activeConnections = AtomicInteger(0)

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var pool: ExecutorService? = null
    @Volatile private var target: Target? = null

    val isRunning: Boolean get() = serverSocket?.isClosed == false
    val localPort: Int get() = serverSocket?.localPort ?: 0
    val currentTarget: Target? get() = target
    val active: Int get() = activeConnections.get()

    fun snapshot(): TrafficStats = TrafficStats(
        upBytes = upBytes.get(),
        downBytes = downBytes.get(),
        connections = connections.get(),
    )

    fun resetStats() {
        upBytes.set(0)
        downBytes.set(0)
        connections.set(0)
    }

    /** Запускает слушатель на случайном свободном порту 127.0.0.1. */
    @Throws(IOException::class)
    fun start(newTarget: Target) {
        require(newTarget.supportsTunnelling) { "MTProto не поддерживает туннелирование" }
        stop()
        val socket = ServerSocket(0, 64, java.net.InetAddress.getLoopbackAddress())
        // Каждое соединение обслуживается в своём потоке: раньше accept-цикл обрабатывал
        // клиентов по очереди, и браузер «висел», пока не закроется первое соединение.
        val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "kupu-proxy-conn").apply { isDaemon = true }
        }
        target = newTarget
        pool = executor
        serverSocket = socket
        Thread({ acceptLoop(socket, executor, newTarget) }, "kupu-proxy-server").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        runCatching { pool?.shutdownNow() }
        serverSocket = null
        pool = null
        target = null
    }

    private fun acceptLoop(socket: ServerSocket, executor: ExecutorService, activeTarget: Target) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: Exception) {
                break
            }
            try {
                executor.execute { client.use { handle(it, activeTarget) } }
            } catch (_: Exception) {
                runCatching { client.close() }
            }
        }
    }

    private fun handle(client: Socket, activeTarget: Target) {
        activeConnections.incrementAndGet()
        var upstream: Socket? = null
        try {
            client.tcpNoDelay = true
            client.soTimeout = 15_000
            val input = client.getInputStream()
            val output = client.getOutputStream()
            val head = readRequestHead(input) ?: return
            val request = parseRequest(head) ?: run {
                writeSimpleError(output, 400, "Bad Request")
                return
            }

            upstream = try {
                openUpstream(activeTarget, request.host, request.port)
            } catch (_: Exception) {
                null
            } ?: run {
                writeSimpleError(output, 502, "Bad Gateway")
                return
            }

            if (request.isConnect) {
                output.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                output.flush()
            } else {
                // Обычный HTTP: пересылаем заголовок в origin-form, тело дочитает pump.
                val rewritten = request.originFormHead.toByteArray(Charsets.ISO_8859_1)
                upstream.getOutputStream().apply { write(rewritten); flush() }
                upBytes.addAndGet(rewritten.size.toLong())
            }
            connections.incrementAndGet()
            client.soTimeout = 0
            pump(client, upstream)
        } catch (_: Exception) {
            // Ошибка отдельного соединения не должна ронять слушатель.
        } finally {
            runCatching { upstream?.close() }
            activeConnections.decrementAndGet()
        }
    }

    /** Открывает туннель до целевого хоста через выбранный прокси. */
    internal fun openUpstream(proxy: Target, host: String, port: Int): Socket {
        val raw = Socket()
        try {
            raw.tcpNoDelay = true
            raw.connect(InetSocketAddress(proxy.host, proxy.port), 6_000)
            raw.soTimeout = 15_000

            val socket: Socket = when (proxy.protocol) {
                ProxyProtocol.SOCKS5 -> {
                    socks5Connect(raw, proxy, host, port)
                    raw
                }

                ProxyProtocol.HTTP, ProxyProtocol.WEB -> {
                    // WEB — тот же CONNECT, но поверх TLS к самому прокси. TLS поднимаем
                    // поверх уже открытого сокета, а не открываем второе соединение.
                    val stream: Socket = if (proxy.protocol == ProxyProtocol.WEB) {
                        val factory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
                        (factory.createSocket(raw, proxy.host, proxy.port, true) as javax.net.ssl.SSLSocket).apply {
                            useClientMode = true
                            soTimeout = 15_000
                            startHandshake()
                        }
                    } else {
                        raw
                    }
                    httpConnect(stream, proxy, host, port)
                    stream
                }

                ProxyProtocol.MTPROTO -> error("MTProto не поддерживает туннелирование")
            }
            socket.soTimeout = 0
            return socket
        } catch (e: Exception) {
            runCatching { raw.close() }
            throw e
        }
    }

    private fun socks5Connect(socket: Socket, proxy: Target, host: String, port: Int) {
        val out = socket.getOutputStream()
        val inp = socket.getInputStream()
        if (proxy.username.isNullOrEmpty()) {
            out.write(byteArrayOf(0x05, 0x01, 0x00)); out.flush()
            val reply = readExactly(inp, 2)
            require(reply[0].toInt() == 0x05) { "bad socks version" }
            require(reply[1].toInt() == 0x00) { "socks: auth required" }
        } else {
            out.write(byteArrayOf(0x05, 0x02, 0x00, 0x02)); out.flush()
            val reply = readExactly(inp, 2)
            require(reply[0].toInt() == 0x05) { "bad socks version" }
            when (reply[1].toInt() and 0xFF) {
                0x00 -> Unit
                0x02 -> {
                    val user = proxy.username.toByteArray(Charsets.UTF_8)
                    val pass = proxy.password.orEmpty().toByteArray(Charsets.UTF_8)
                    require(user.size in 1..255 && pass.size <= 255) { "socks: слишком длинный логин/пароль" }
                    out.write(byteArrayOf(0x01, user.size.toByte())); out.write(user)
                    out.write(pass.size); out.write(pass); out.flush()
                    val auth = readExactly(inp, 2)
                    require(auth[1].toInt() == 0x00) { "socks: auth rejected" }
                }
                else -> error("socks: auth method not accepted")
            }
        }

        val hostBytes = host.toByteArray(Charsets.US_ASCII)
        require(hostBytes.size in 1..255) { "socks: bad host" }
        out.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()))
        out.write(hostBytes)
        out.write((port shr 8) and 0xFF)
        out.write(port and 0xFF)
        out.flush()

        // Ответ: VER REP RSV ATYP BND.ADDR BND.PORT. Раньше после 4 байт заголовка читался
        // ещё один «REP», то есть первый байт адреса, — и туннель почти всегда отклонялся.
        val header = readExactly(inp, 4)
        require(header[0].toInt() == 0x05) { "bad socks version" }
        require(header[1].toInt() == 0x00) { "socks: connect refused (${header[1].toInt() and 0xFF})" }
        when (header[3].toInt() and 0xFF) {
            0x01 -> readExactly(inp, 4 + 2)
            0x04 -> readExactly(inp, 16 + 2)
            0x03 -> readExactly(inp, 1).also { readExactly(inp, (it[0].toInt() and 0xFF) + 2) }
            else -> error("socks: bad atyp")
        }
    }

    private fun httpConnect(socket: Socket, proxy: Target, host: String, port: Int) {
        val out = socket.getOutputStream()
        val inp = socket.getInputStream()
        val authority = authority(host, port)
        val sb = StringBuilder()
        sb.append("CONNECT ").append(authority).append(" HTTP/1.1\r\n")
        sb.append("Host: ").append(authority).append("\r\n")
        if (!proxy.username.isNullOrEmpty()) {
            val token = java.util.Base64.getEncoder()
                .encodeToString("${proxy.username}:${proxy.password.orEmpty()}".toByteArray(Charsets.UTF_8))
            sb.append("Proxy-Authorization: Basic ").append(token).append("\r\n")
        }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        out.flush()

        val statusLine = readLine(inp)
        val status = statusLine?.split(' ')?.getOrNull(1)?.toIntOrNull()
        require(status != null && status in 200..299) { "http: status ${status ?: "?"}" }
        var guard = 0
        while (readLine(inp)?.isNotEmpty() == true) {
            if (++guard > 100) error("http: too many headers")
        }
    }

    /**
     * Двунаправленная перекачка с учётом трафика. Оба направления работают одновременно:
     * раньше поток «клиент → прокси» запускался только после того, как закончится ответ,
     * поэтому запрос не доходил до сервера и туннель висел до таймаута.
     */
    private fun pump(client: Socket, upstream: Socket) {
        val up = Thread({
            runCatching {
                copy(client.getInputStream(), upstream.getOutputStream()) { n -> upBytes.addAndGet(n.toLong()) }
            }
            runCatching { if (upstream !is javax.net.ssl.SSLSocket) upstream.shutdownOutput() }
        }, "kupu-pump-up")
        up.isDaemon = true
        up.start()

        runCatching {
            copy(upstream.getInputStream(), client.getOutputStream()) { n -> downBytes.addAndGet(n.toLong()) }
        }
        runCatching { client.shutdownOutput() }
        // Сервер закрыл соединение — дальше клиенту слать некуда.
        runCatching { upstream.close() }
        up.join(5_000)
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

    internal data class ParsedRequest(
        val method: String,
        val host: String,
        val port: Int,
        val isConnect: Boolean,
        val originFormHead: String = "",
    )

    private fun readRequestHead(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 16 * 1024) {
            val line = readLine(input) ?: return null
            sb.append(line).append("\r\n")
            if (line.isEmpty()) return sb.toString()
        }
        return null
    }

    internal fun parseRequest(head: String): ParsedRequest? {
        val lines = head.split("\r\n")
        val first = lines.firstOrNull()?.trim().orEmpty()
        val parts = first.split(' ').filter(String::isNotEmpty)
        if (parts.size < 3) return null
        val method = parts[0].uppercase()
        val target = parts[1]
        val version = parts[2]

        if (method == "CONNECT") {
            val (host, port) = splitAuthority(target, 443) ?: return null
            return ParsedRequest(method, host, port, isConnect = true)
        }

        // absolute-form: http://host[:port]/path
        if (!target.startsWith("http://", ignoreCase = true)) return null
        val rest = target.substring("http://".length)
        val slash = rest.indexOf('/').let { if (it < 0) rest.length else it }
        val (host, port) = splitAuthority(rest.substring(0, slash), 80) ?: return null
        val path = rest.substring(slash).ifEmpty { "/" }

        val headers = lines.drop(1)
            .takeWhile { it.isNotEmpty() }
            .filterNot {
                val name = it.substringBefore(':').trim().lowercase()
                name == "proxy-connection" || name == "proxy-authorization"
            }
        val origin = buildString {
            append(method).append(' ').append(path).append(' ').append(version).append("\r\n")
            if (headers.none { it.substringBefore(':').trim().equals("host", true) }) {
                append("Host: ").append(authority(host, port)).append("\r\n")
            }
            headers.forEach { append(it).append("\r\n") }
            append("\r\n")
        }
        return ParsedRequest(method, host, port, isConnect = false, originFormHead = origin)
    }

    /** `host:port`, `[v6]:port` или `host` с портом по умолчанию. */
    private fun splitAuthority(value: String, defaultPort: Int): Pair<String, Int>? {
        if (value.isEmpty()) return null
        if (value.startsWith('[')) {
            val end = value.indexOf(']')
            if (end <= 1) return null
            val host = value.substring(1, end)
            val tail = value.substring(end + 1)
            val port = if (tail.startsWith(':')) tail.substring(1).toIntOrNull() else if (tail.isEmpty()) defaultPort else null
            return port?.takeIf { it in 1..65535 }?.let { host to it }
        }
        val colon = value.lastIndexOf(':')
        if (colon < 0) return value to defaultPort
        if (colon == 0) return null
        val port = value.substring(colon + 1).toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return value.substring(0, colon) to port
    }

    private fun authority(host: String, port: Int): String =
        if (host.contains(':')) "[$host]:$port" else "$host:$port"

    private fun writeSimpleError(out: OutputStream, code: Int, reason: String) {
        runCatching {
            out.write("HTTP/1.1 $code $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            out.flush()
        }
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream(64)
        var previous = -1
        while (buffer.size() < 8 * 1024) {
            val b = input.read()
            if (b < 0) return if (buffer.size() == 0) null else buffer.toString(Charsets.ISO_8859_1)
            if (previous == '\r'.code && b == '\n'.code) {
                val bytes = buffer.toByteArray()
                return String(bytes, 0, bytes.size - 1, Charsets.ISO_8859_1)
            }
            buffer.write(b)
            previous = b
        }
        return buffer.toString(Charsets.ISO_8859_1)
    }

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
