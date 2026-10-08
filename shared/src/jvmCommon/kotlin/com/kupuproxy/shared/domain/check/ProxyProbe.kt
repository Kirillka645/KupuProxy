package com.kupuproxy.shared.domain.check

import com.kupuproxy.shared.domain.model.ProxyProtocol
import com.kupuproxy.shared.domain.model.RawProxyEntry
import com.kupuproxy.shared.domain.parser.ProxyParser

/**
 * Единая точка проверки доступности для всех транспортов.
 *
 * Раньше проверка умела только MTProto, а SOCKS-ссылки молча отбрасывались без проверки и без
 * записи в историю. Теперь протокол определяется автоматически, доступность проверяется по
 * правилам самого протокола, а по нескольким замерам считается jitter.
 */
object ProxyProbe {

    /** Число замеров для расчёта jitter. 1 — когда нужно только «работает / не работает». */
    const val DEFAULT_SAMPLES = 3

    /** Быстрый TCP-префлайт: отсекает мёртвые host до дорогого рукопожатия. */
    fun isTcpReachable(host: String, port: Int, timeoutMs: Int): Boolean =
        MtprotoChecker.isTcpReachable(host, port, timeoutMs)

    /** Разбирает любую поддерживаемую ссылку; `null` — формат не распознан. */
    fun parse(url: String): RawProxyEntry? = ProxyParser.fromUrl(url)

    /**
     * Проверяет одну запись. [samples] замеров подряд; jitter считается по успешным.
     * При `samples <= 1` дополнительных замеров не делается — это режим быстрого скана.
     */
    fun probe(
        entry: RawProxyEntry,
        connectTimeoutMs: Int,
        responseTimeoutMs: Int,
        samples: Int = 1,
    ): ProbeResult {
        val rounds = samples.coerceIn(1, 5)

        // Префлайт: один TCP-запрос на endpoint, а не на каждый секрет.
        if (!MtprotoChecker.isTcpReachable(entry.host, entry.port, (connectTimeoutMs / 2).coerceIn(500, 900))) {
            return ProbeResult(
                ok = false,
                protocol = entry.protocol,
                latencyMs = -1,
                tcpReachable = false,
                error = "tcp_unreachable",
            )
        }

        val timings = ArrayList<Int>(rounds)
        var lastError: String? = null

        for (round in 0 until rounds) {
            val result = probeOnce(entry, connectTimeoutMs, responseTimeoutMs)
            if (!result.ok) {
                lastError = result.error
                // Провал посреди серии замеров: дальше мерять нет смысла.
                if (timings.isNotEmpty()) lastError = "flaky"
                break
            }
            timings.add(result.latencyMs)
            // Ранние выходы здесь недопустимы: если запрошено несколько замеров, jitter
            // обязан считаться по всем из них, а не по одному «быстрому» ответу.
        }

        if (timings.isEmpty()) {
            return ProbeResult(
                ok = false,
                protocol = entry.protocol,
                latencyMs = -1,
                tcpReachable = true,
                error = lastError ?: "unavailable",
            )
        }

        val stats = LatencyStats.from(timings)
        return ProbeResult(
            ok = true,
            protocol = entry.protocol,
            latencyMs = stats.avgLatencyMs,
            jitterMs = stats.jitterMs,
            samples = timings,
            tcpReachable = true,
        )
    }

    /** Проверяет готовую ссылку. */
    fun probeUrl(
        url: String,
        connectTimeoutMs: Int,
        responseTimeoutMs: Int,
        samples: Int = 1,
    ): ProbeResult {
        val entry = parse(url)
            ?: return ProbeResult(
                ok = false,
                protocol = ProxyProtocol.MTPROTO,
                latencyMs = -1,
                error = "bad_url",
            )
        return probe(entry, connectTimeoutMs, responseTimeoutMs, samples)
    }

    private fun probeOnce(
        entry: RawProxyEntry,
        connectTimeoutMs: Int,
        responseTimeoutMs: Int,
    ): ProbeResult = when (entry.protocol) {
        ProxyProtocol.MTPROTO -> {
            val result = try {
                MtprotoChecker.checkUrl(entry.url, connectTimeoutMs, responseTimeoutMs)
            } catch (e: Exception) {
                MtprotoChecker.CheckResult(false, -1, e.message ?: "error")
            }
            ProbeResult(
                ok = result.ok,
                protocol = ProxyProtocol.MTPROTO,
                latencyMs = if (result.ok) result.rttMs.coerceAtLeast(1) else -1,
                tcpReachable = true,
                error = result.error,
            )
        }

        ProxyProtocol.SOCKS5, ProxyProtocol.HTTP, ProxyProtocol.WEB ->
            AuthProxyChecker.check(
                protocol = entry.protocol,
                host = entry.host,
                port = entry.port,
                username = entry.username,
                password = entry.password,
                connectTimeoutMs = connectTimeoutMs,
                responseTimeoutMs = responseTimeoutMs,
            )
    }
}