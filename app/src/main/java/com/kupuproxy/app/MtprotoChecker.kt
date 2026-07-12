package com.kupuproxy.app

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/**
 * Проверка MTProxy «как Telegram»:
 * TCP → (FakeTLS если ee) → obfuscated2 handshake → req_pq_multi → resPQ.
 *
 * Логика по мотивам open-source MTProxy checkers (MIT).
 */
object MtprotoChecker {

    private val random = SecureRandom()

    private val PROTO_ABRIDGED = byteArrayOf(0xEF.toByte(), 0xEF.toByte(), 0xEF.toByte(), 0xEF.toByte())
    private val PROTO_INTERMEDIATE = byteArrayOf(0xEE.toByte(), 0xEE.toByte(), 0xEE.toByte(), 0xEE.toByte())
    private val PROTO_SECURE = byteArrayOf(0xDD.toByte(), 0xDD.toByte(), 0xDD.toByte(), 0xDD.toByte())

    private const val REQ_PQ_MULTI = 0xBE7E8EF1.toInt()
    private const val RES_PQ = 0x05162463
    private const val MAX_FRAME = 2 * 1024 * 1024
    private const val FAKETLS_HELLO_LEN = 517
    private val FAKETLS_CCS = byteArrayOf(0x14, 0x03, 0x03, 0x00, 0x01, 0x01)
    private val FAKETLS_APP_PREFIX = byteArrayOf(0x17, 0x03, 0x03)
    private const val FAKETLS_MAX_APP = 1425
    private const val DEFAULT_DOMAIN = "www.google.com"

    enum class Mode { SECURE, INTERMEDIATE, ABRIDGED, FAKETLS }

    data class SecretInfo(
        val raw: ByteArray,
        val isFakeTls: Boolean,
        val domain: String = DEFAULT_DOMAIN
    )

    data class CheckResult(
        val ok: Boolean,
        val rttMs: Int,
        val error: String? = null
    )

    fun checkUrl(url: String, connectTimeoutMs: Int, responseTimeoutMs: Int): CheckResult {
        val parsed = parseProxy(url) ?: return CheckResult(false, -1, "bad_url")
        return check(parsed.host, parsed.port, parsed.secret, connectTimeoutMs, responseTimeoutMs)
    }

    fun check(
        host: String,
        port: Int,
        secret: SecretInfo,
        connectTimeoutMs: Int,
        responseTimeoutMs: Int
    ): CheckResult {
        val modes: List<Mode> = if (secret.isFakeTls) {
            listOf(Mode.FAKETLS)
        } else {
            listOf(Mode.SECURE, Mode.ABRIDGED, Mode.INTERMEDIATE)
        }
        val dcs = intArrayOf(2, 1, 3)

        var lastError = "unavailable"
        val started = System.currentTimeMillis()

        for (dc in dcs) {
            for (mode in modes) {
                try {
                    val rtt = checkOnce(
                        host, port, secret, mode, dc,
                        connectTimeoutMs, responseTimeoutMs
                    )
                    return CheckResult(true, rtt)
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                }
            }
        }

        val elapsed = (System.currentTimeMillis() - started).toInt()
        return CheckResult(false, elapsed.coerceAtLeast(-1), lastError)
    }

    private fun checkOnce(
        host: String,
        port: Int,
        secret: SecretInfo,
        mode: Mode,
        dcId: Int,
        connectTimeoutMs: Int,
        responseTimeoutMs: Int
    ): Int {
        val start = System.currentTimeMillis()
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = responseTimeoutMs

            val transport: Transport = if (mode == Mode.FAKETLS) {
                FakeTlsTransport(socket, secret.raw, secret.domain).also { it.handshake() }
            } else {
                PlainTransport(socket)
            }

            val inner = if (mode == Mode.FAKETLS) Mode.SECURE else mode
            val (init, enc, dec) = makeObfuscated2Handshake(secret.raw, inner, dcId)
            transport.write(init)

            val (nonce, req) = makeUnencryptedReqPqMulti()
            val framed = frameMessage(req, inner)
            transport.write(enc.update(framed))

            val frame = readFrame(transport, dec, inner)
            parseResPq(frame, nonce)

            return (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    // region Secret / URL parsing

    data class ParsedProxy(val host: String, val port: Int, val secret: SecretInfo)

    fun parseProxy(url: String): ParsedProxy? {
        return try {
            val q = when {
                url.startsWith("tg://proxy?") -> url.removePrefix("tg://proxy?")
                url.startsWith("https://t.me/proxy?") -> url.substringAfter("?")
                url.startsWith("http://t.me/proxy?") -> url.substringAfter("?")
                else -> return null
            }
            var host = ""
            var port = 0
            var secretRaw = ""
            q.split("&").forEach { p ->
                val parts = p.split("=", limit = 2)
                if (parts.size == 2) {
                    when (parts[0]) {
                        "server" -> host = parts[1]
                        "port" -> port = parts[1].toIntOrNull() ?: 0
                        "secret" -> secretRaw = parts[1]
                    }
                }
            }
            if (host.isBlank() || port !in 1..65535 || secretRaw.isBlank()) return null
            val secret = decodeSecret(secretRaw) ?: return null
            ParsedProxy(host, port, secret)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeSecret(secret: String): SecretInfo? {
        val s = secret.trim()
        if (s.isEmpty()) return null
        val lower = s.lowercase()

        // hex path
        try {
            if (lower.all { it in "0123456789abcdef" }) {
                if (lower.startsWith("ee")) {
                    if (lower.length < 34) return null
                    val raw = lower.substring(2, 34).hexToBytes()
                    val domain = if (lower.length > 34) {
                        try {
                            lower.substring(34).hexToBytes()
                                .toString(Charsets.US_ASCII)
                                .trim { it <= ' ' || it == '\u0000' }
                                .ifBlank { DEFAULT_DOMAIN }
                        } catch (_: Exception) {
                            DEFAULT_DOMAIN
                        }
                    } else DEFAULT_DOMAIN
                    return SecretInfo(raw, true, extractDomain(domain))
                }
                if (lower.startsWith("dd")) {
                    if (lower.length < 34) return null
                    return SecretInfo(lower.substring(2, 34).hexToBytes(), false)
                }
                val bytes = lower.hexToBytes()
                if (bytes.size >= 16) return SecretInfo(bytes.copyOf(16), false)
            }
        } catch (_: Exception) {
        }

        // base64 / base64url
        return try {
            val b64 = s.replace('-', '+').replace('_', '/')
            val pad = "=".repeat((4 - b64.length % 4) % 4)
            val raw = android.util.Base64.decode(b64 + pad, android.util.Base64.DEFAULT)
            when {
                raw.size == 16 -> SecretInfo(raw, false)
                raw.size >= 17 && raw[0] == 0xDD.toByte() ->
                    SecretInfo(raw.copyOfRange(1, 17), false)
                raw.size >= 17 && raw[0] == 0xEE.toByte() -> {
                    val domainBytes = if (raw.size > 17) raw.copyOfRange(17, raw.size) else ByteArray(0)
                    val domain = extractDomain(
                        domainBytes.toString(Charsets.US_ASCII)
                            .filter { it.isLetterOrDigit() || it == '.' || it == '-' }
                    )
                    SecretInfo(raw.copyOfRange(1, 17), true, domain)
                }
                raw.size > 16 -> SecretInfo(raw.copyOf(16), false)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractDomain(text: String): String {
        val cleaned = text.trim().trim('\u0000')
        if (cleaned.isBlank()) return DEFAULT_DOMAIN
        val tlds = listOf(".com", ".net", ".org", ".ru", ".io", ".co", ".dev", ".app", ".cloud", ".me", ".info", ".homes", ".shop")
        var best: String? = null
        for (tld in tlds) {
            val pos = cleaned.lowercase().indexOf(tld)
            if (pos > 0) {
                val end = pos + tld.length
                val candidate = cleaned.substring(0, end).trimStart { !it.isLetterOrDigit() }
                if (best == null || candidate.length < best.length) best = candidate
            }
        }
        return best?.ifBlank { DEFAULT_DOMAIN } ?: cleaned.take(64).ifBlank { DEFAULT_DOMAIN }
    }

    // endregion

    // region Obfuscated2 + MTProto

    private class AesCtr(key: ByteArray, iv: ByteArray) {
        private val cipher: Cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        }

        fun update(data: ByteArray): ByteArray {
            return cipher.update(data) ?: ByteArray(0)
        }
    }

    private fun makeObfuscated2Handshake(
        secret: ByteArray,
        mode: Mode,
        dcId: Int
    ): Triple<ByteArray, AesCtr, AesCtr> {
        val protoTag = when (mode) {
            Mode.SECURE, Mode.FAKETLS -> PROTO_SECURE
            Mode.INTERMEDIATE -> PROTO_INTERMEDIATE
            Mode.ABRIDGED -> PROTO_ABRIDGED
        }

        val forbidden = setOf(
            "GET ", "POST", "HEAD", "OPTI",
            "\u0000\u0000\u0000\u0000",
            String(PROTO_ABRIDGED, Charsets.ISO_8859_1),
            String(PROTO_INTERMEDIATE, Charsets.ISO_8859_1),
            String(PROTO_SECURE, Charsets.ISO_8859_1)
        )

        val init = ByteArray(64)
        while (true) {
            random.nextBytes(init)
            if (init[0] == 0xEF.toByte()) continue
            val first4 = String(init, 0, 4, Charsets.ISO_8859_1)
            if (first4 in forbidden) continue
            if (init[4] == 0.toByte() && init[5] == 0.toByte() &&
                init[6] == 0.toByte() && init[7] == 0.toByte()
            ) continue
            break
        }

        System.arraycopy(protoTag, 0, init, 56, 4)
        init[60] = (dcId and 0xFF).toByte()
        init[61] = 0
        init[62] = 0
        init[63] = 0

        val encryptKeyMaterial = init.copyOfRange(8, 40)
        val encryptIv = init.copyOfRange(40, 56)
        val decryptKeyMaterial = ByteArray(32)
        for (i in 0 until 32) decryptKeyMaterial[i] = init[55 - i]
        val decryptIv = ByteArray(16)
        for (i in 0 until 16) decryptIv[i] = init[23 - i]

        val encKey = sha256(encryptKeyMaterial + secret)
        val decKey = sha256(decryptKeyMaterial + secret)

        val enc = AesCtr(encKey, encryptIv)
        val dec = AesCtr(decKey, decryptIv)

        val encryptedInit = enc.update(init)
        // replace last 8 bytes with encrypted version (protocol quirk)
        System.arraycopy(encryptedInit, 56, init, 56, 8)

        return Triple(init, enc, dec)
    }

    private fun makeUnencryptedReqPqMulti(): Pair<ByteArray, ByteArray> {
        val nonce = ByteArray(16).also { random.nextBytes(it) }
        val payload = ByteBuffer.allocate(4 + 16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(REQ_PQ_MULTI)
            .put(nonce)
            .array()

        // msg_id: time-based, aligned to 4 (clear lowest 2 bits)
        val msgId = (System.currentTimeMillis() * 4294967296L / 1000) and -4L
        val message = ByteBuffer.allocate(8 + 8 + 4 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(ByteArray(8))
            .putLong(msgId)
            .putInt(payload.size)
            .put(payload)
            .array()

        return nonce to message
    }

    private fun frameMessage(data: ByteArray, mode: Mode): ByteArray {
        return when (mode) {
            Mode.SECURE, Mode.FAKETLS -> {
                val padLen = random.nextInt(4)
                val padding = ByteArray(padLen).also { random.nextBytes(it) }
                ByteBuffer.allocate(4 + data.size + padLen).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(data.size + padLen)
                    .put(data)
                    .put(padding)
                    .array()
            }
            Mode.INTERMEDIATE -> {
                ByteBuffer.allocate(4 + data.size).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(data.size)
                    .put(data)
                    .array()
            }
            Mode.ABRIDGED -> {
                require(data.size % 4 == 0) { "abridged len" }
                val words = data.size / 4
                val header = if (words < 127) {
                    byteArrayOf(words.toByte())
                } else {
                    byteArrayOf(
                        0x7F,
                        (words and 0xFF).toByte(),
                        ((words shr 8) and 0xFF).toByte(),
                        ((words shr 16) and 0xFF).toByte()
                    )
                }
                header + data
            }
        }
    }

    private fun readFrame(transport: Transport, dec: AesCtr, mode: Mode): ByteArray {
        if (mode == Mode.SECURE || mode == Mode.INTERMEDIATE || mode == Mode.FAKETLS) {
            val encLen = transport.readExact(4)
            val lenBytes = dec.update(encLen)
            val rawLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).int
            var frameLenLong = rawLen.toLong() and 0xFFFFFFFFL
            if (frameLenLong > 0x80000000L) {
                frameLenLong -= 0x80000000L
            }
            val frameLen = frameLenLong.toInt()
            require(frameLen in 1..MAX_FRAME) { "bad frame len $frameLen" }
            val encPayload = transport.readExact(frameLen)
            return dec.update(encPayload)
        }

        // abridged
        val firstEnc = transport.readExact(1)
        val first = dec.update(firstEnc)[0].toInt() and 0xFF
        val frameLen = if (first < 127) {
            first * 4
        } else {
            val restEnc = transport.readExact(3)
            val rest = dec.update(restEnc)
            val words = (rest[0].toInt() and 0xFF) or
                ((rest[1].toInt() and 0xFF) shl 8) or
                ((rest[2].toInt() and 0xFF) shl 16)
            words * 4
        }
        require(frameLen in 1..MAX_FRAME) { "bad abridged len $frameLen" }
        return dec.update(transport.readExact(frameLen))
    }

    private fun parseResPq(frame: ByteArray, expectedNonce: ByteArray): String {
        require(frame.size >= 40) { "short response ${frame.size}" }
        require(frame.copyOfRange(0, 8).all { it == 0.toByte() }) { "not unencrypted" }

        val msgLen = ByteBuffer.wrap(frame, 16, 4).order(ByteOrder.LITTLE_ENDIAN).int
        require(msgLen in 1..(frame.size - 20)) { "bad msg len $msgLen" }

        val body = frame.copyOfRange(20, 20 + msgLen)
        require(body.size >= 36) { "short body" }

        val constructor = ByteBuffer.wrap(body, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        require(constructor == RES_PQ) { "bad constructor 0x${constructor.toString(16)}" }

        val responseNonce = body.copyOfRange(4, 20)
        require(responseNonce.contentEquals(expectedNonce)) { "nonce mismatch" }

        return body.copyOfRange(20, 36).toHex()
    }

    // endregion

    // region Transport

    private interface Transport {
        fun write(data: ByteArray)
        fun readExact(n: Int): ByteArray
    }

    private class PlainTransport(socket: Socket) : Transport {
        private val input: InputStream = socket.getInputStream()
        private val output: OutputStream = socket.getOutputStream()

        override fun write(data: ByteArray) {
            output.write(data)
            output.flush()
        }

        override fun readExact(n: Int): ByteArray = readExactStream(input, n)
    }

    private class FakeTlsTransport(
        private val socket: Socket,
        private val secret: ByteArray,
        private val domain: String
    ) : Transport {
        private val input = socket.getInputStream()
        private val output = socket.getOutputStream()
        private val readBuffer = ArrayList<Byte>()
        private var didFirstWrite = false
        private lateinit var clientRandom: ByteArray

        fun handshake() {
            val (hello, cr) = makeFakeTlsClientHello(secret, domain)
            clientRandom = cr
            output.write(hello)
            output.flush()

            val firstHeader = readExactStream(input, 5)
            val (recordType, payloadLen) = parseTlsHeader(firstHeader)
            require(recordType == 0x16) { "expected ServerHello, got 0x${recordType.toString(16)}" }

            val firstPayload = readExactStream(input, payloadLen)
            val ccs = readExactStream(input, FAKETLS_CCS.size)
            require(ccs.contentEquals(FAKETLS_CCS)) { "bad CCS" }

            val appHeader = readExactStream(input, 5)
            val (appType, appLen) = parseTlsHeader(appHeader)
            require(appType == 0x17) { "expected app data, got 0x${appType.toString(16)}" }
            val appPayload = readExactStream(input, appLen)

            val response = firstHeader + firstPayload + ccs + appHeader + appPayload
            validateFakeTlsServer(secret, clientRandom, response)
        }

        override fun write(data: ByteArray) {
            val out = ArrayList<Byte>()
            if (!didFirstWrite) {
                FAKETLS_CCS.forEach { out.add(it) }
                didFirstWrite = true
            }
            var offset = 0
            while (offset < data.size) {
                val end = min(offset + FAKETLS_MAX_APP, data.size)
                val chunk = data.copyOfRange(offset, end)
                FAKETLS_APP_PREFIX.forEach { out.add(it) }
                out.add(((chunk.size shr 8) and 0xFF).toByte())
                out.add((chunk.size and 0xFF).toByte())
                chunk.forEach { out.add(it) }
                offset = end
            }
            output.write(out.toByteArray())
            output.flush()
        }

        override fun readExact(n: Int): ByteArray {
            while (readBuffer.size < n) {
                val header = readExactStream(input, 5)
                val (type, len) = parseTlsHeader(header)
                val payload = readExactStream(input, len)
                if (type == 0x14 && payload.contentEquals(byteArrayOf(0x01))) continue
                require(type == 0x17) { "expected app data 0x${type.toString(16)}" }
                payload.forEach { readBuffer.add(it) }
            }
            val result = ByteArray(n)
            for (i in 0 until n) {
                result[i] = readBuffer.removeAt(0)
            }
            return result
        }
    }

    private fun makeFakeTlsClientHello(secret: ByteArray, domain: String): Pair<ByteArray, ByteArray> {
        require(secret.size == 16)
        val domainBytes = domain.encodeToByteArray()
        val greases = makeGrease()
        val out = ArrayList<Byte>()

        // TLS record + handshake header skeleton
        fun add(vararg b: Int) = b.forEach { out.add(it.toByte()) }
        fun addBytes(b: ByteArray) = b.forEach { out.add(it) }
        fun addU16(v: Int) {
            out.add(((v shr 8) and 0xFF).toByte())
            out.add((v and 0xFF).toByte())
        }
        fun addGrease(idx: Int) {
            val g = greases[idx]
            out.add(g.toByte())
            out.add(g.toByte())
        }

        add(0x16, 0x03, 0x01, 0x02, 0x00, 0x01, 0x00, 0x01, 0xFC, 0x03, 0x03)
        val randomOffset = out.size
        repeat(32) { out.add(0) }
        out.add(0x20)
        val sessionId = ByteArray(32).also { random.nextBytes(it) }
        addBytes(sessionId)
        add(0x00, 0x22)
        addGrease(0)
        addBytes(
            byteArrayOf(
                0x13, 0x01, 0x13, 0x02, 0x13, 0x03, 0xC0.toByte(), 0x2B, 0xC0.toByte(), 0x2F,
                0xC0.toByte(), 0x2C, 0xC0.toByte(), 0x30, 0xCC.toByte(), 0xA9.toByte(),
                0xCC.toByte(), 0xA8.toByte(), 0xC0.toByte(), 0x13, 0xC0.toByte(), 0x14,
                0x00, 0x9C.toByte(), 0x00, 0x9D.toByte(), 0x00, 0x2F, 0x00, 0x35, 0x00, 0x0A,
                0x01, 0x00, 0x01, 0x91.toByte()
            )
        )
        addGrease(2)
        add(0x00, 0x00, 0x00, 0x00)
        addU16(domainBytes.size + 5)
        addU16(domainBytes.size + 3)
        out.add(0x00)
        addU16(domainBytes.size)
        addBytes(domainBytes)
        addBytes(
            byteArrayOf(
                0x00, 0x17, 0x00, 0x00, 0xFF.toByte(), 0x01, 0x00, 0x01, 0x00, 0x00, 0x0A,
                0x00, 0x0A, 0x00, 0x08
            )
        )
        addGrease(4)
        addBytes(
            byteArrayOf(
                0x00, 0x1D, 0x00, 0x17, 0x00, 0x18, 0x00, 0x0B, 0x00, 0x02, 0x01, 0x00,
                0x00, 0x23, 0x00, 0x00, 0x00, 0x10, 0x00, 0x0E, 0x00, 0x0C, 0x02, 0x68,
                0x32, 0x08, 0x68, 0x74, 0x74, 0x70, 0x2F, 0x31, 0x2E, 0x31, 0x00, 0x05,
                0x00, 0x05, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0D, 0x00, 0x14, 0x00,
                0x12, 0x04, 0x03, 0x08, 0x04, 0x04, 0x01, 0x05, 0x03, 0x08, 0x05, 0x05,
                0x01, 0x08, 0x06, 0x06, 0x01, 0x02, 0x01, 0x00, 0x12, 0x00, 0x00, 0x00,
                0x33, 0x00, 0x2B, 0x00, 0x29
            )
        )
        addGrease(4)
        add(0x00, 0x01, 0x00, 0x00, 0x1D, 0x00, 0x20)
        val keyShare = ByteArray(32).also { random.nextBytes(it) }
        addBytes(keyShare)
        addBytes(byteArrayOf(0x00, 0x2D, 0x00, 0x02, 0x01, 0x01, 0x00, 0x2B, 0x00, 0x0B, 0x0A))
        addGrease(6)
        addBytes(byteArrayOf(0x03, 0x04, 0x03, 0x03, 0x03, 0x02, 0x03, 0x01, 0x00, 0x1B, 0x00, 0x03, 0x02, 0x00, 0x02))
        addGrease(3)
        add(0x00, 0x01, 0x00, 0x00, 0x15)

        val paddingLength = FAKETLS_HELLO_LEN - 2 - out.size
        require(paddingLength >= 0) { "ClientHello too long" }
        addU16(paddingLength)
        repeat(paddingLength) { out.add(0) }

        val hello = out.toByteArray()
        require(hello.size == FAKETLS_HELLO_LEN) { "bad hello len ${hello.size}" }

        val digest = hmacSha256(secret, hello)
        val timestamp = (System.currentTimeMillis() / 1000).toInt()
        val digestTail = ByteBuffer.wrap(digest, 28, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val clientRandom = ByteArray(32)
        System.arraycopy(digest, 0, clientRandom, 0, 28)
        ByteBuffer.wrap(clientRandom, 28, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(digestTail xor timestamp)
        System.arraycopy(clientRandom, 0, hello, randomOffset, 32)

        return hello to clientRandom
    }

    private fun validateFakeTlsServer(secret: ByteArray, clientRandom: ByteArray, response: ByteArray) {
        require(response.size >= 43) { "short faketls response" }
        val serverRandom = response.copyOfRange(11, 43)
        val zeroed = response.copyOf()
        for (i in 11 until 43) zeroed[i] = 0
        val expected = hmacSha256(secret, clientRandom + zeroed)
        require(serverRandom.contentEquals(expected)) { "faketls hmac mismatch" }
    }

    private fun makeGrease(): IntArray {
        val greases = IntArray(7) { (random.nextInt(256) and 0xF0) + 0x0A }
        for (i in 1 until greases.size step 2) {
            if (greases[i] == greases[i - 1]) greases[i] = 0x10 xor greases[i]
        }
        return greases
    }

    private fun parseTlsHeader(header: ByteArray): Pair<Int, Int> {
        require(header.size == 5) { "short tls header" }
        val type = header[0].toInt() and 0xFF
        val ver = header.copyOfRange(1, 3)
        require(
            ver.contentEquals(byteArrayOf(0x03, 0x01)) ||
                ver.contentEquals(byteArrayOf(0x03, 0x03))
        ) { "bad tls ver" }
        val len = ((header[3].toInt() and 0xFF) shl 8) or (header[4].toInt() and 0xFF)
        return type to len
    }

    private fun readExactStream(input: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(out, off, n - off)
            if (r < 0) throw java.io.EOFException("closed reading $n got $off")
            off += r
        }
        return out
    }

    // endregion

    // region utils

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun String.hexToBytes(): ByteArray {
        val clean = if (length % 2 == 0) this else "0$this"
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray =
        this.copyOf(size + other.size).also {
            System.arraycopy(other, 0, it, size, other.size)
        }

    // endregion
}
