package com.kupuproxy.shared.domain.check

import com.kupuproxy.shared.domain.model.ProxyProtocol
import kotlin.math.sqrt

/** Итог одной попытки проверки прокси. */
data class ProbeResult(
    val ok: Boolean,
    val protocol: ProxyProtocol,
    /** Round-trip по успешной пробе, мс. При неудаче — затраченное время (>= 0). */
    val latencyMs: Int,
    /** Разброс замеров (max - min), мс. 0 при одиночном замере. */
    val jitterMs: Int = 0,
    /** Все успешные замеры, мс. Пусто при неудаче. */
    val samples: List<Int> = emptyList(),
    val tcpReachable: Boolean = false,
    val error: String? = null,
) {
    val displayLatency: Int get() = if (ok) latencyMs.coerceAtLeast(0) else -1
}

/**
 * Усреднённая метрика endpoint'а, накопленная по нескольким проверкам.
 * Используется для сортировки и индикаторов в списках.
 */
data class LatencyStats(
    val lastLatencyMs: Int,
    val avgLatencyMs: Int,
    val jitterMs: Int,
    val samples: Int,
) {
    companion object {
        val Empty = LatencyStats(-1, -1, 0, 0)

        /** Считает статистику по успешным замерам; [samples] может содержать и провалы (<=0). */
        fun from(samples: List<Int>): LatencyStats {
            val ok = samples.filter { it > 0 }.sorted()
            if (ok.isEmpty()) return Empty
            val avg = Math.round(ok.average()).toInt()
            val spread = if (ok.size < 2) 0 else ok.last() - ok.first()
            return LatencyStats(lastLatencyMs = ok.last(), avgLatencyMs = avg, jitterMs = spread, samples = ok.size)
        }
    }
}

/** Стандартное отклонение — для отображения «стабильности» соединения. */
fun List<Int>.stdDev(): Double {
    if (size < 2) return 0.0
    val mean = average()
    return sqrt(sumOf { (it - mean) * (it - mean) } / size)
}

/** Качество соединения по задержке — та же шкала, что в UI (общая с мобильной версией). */
enum class LinkQuality { EXCELLENT, GOOD, FAIR, POOR, UNKNOWN;

    companion object {
        fun of(latencyMs: Int): LinkQuality = when {
            latencyMs <= 0 -> UNKNOWN
            latencyMs < 150 -> EXCELLENT
            latencyMs < 400 -> GOOD
            latencyMs < 900 -> FAIR
            else -> POOR
        }
    }
}