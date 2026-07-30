package com.kupuproxy.app.core.util

import java.io.IOException
import okio.BufferedSource

/**
 * Читает поток целиком, отвергая ответы больше [maxBytes].
 *
 * ВАЖНО: okio-функция `readByteArray(byteCount)` — это ТОЧНОЕ чтение: она бросает
 * `EOFException`, если в потоке меньше, чем `byteCount` байт. Использование её как
 * «лимита» ломает любой нормальный (маленький) ответ: например JSON релиза в 3 КБ
 * при лимите 512 КБ падал с EOFException. Именно из-за этого в 1.3.3.3+ перестала
 * работать проверка обновлений.
 *
 * Здесь лимит проверяется через [BufferedSource.request], который возвращает
 * `false`, если байт меньше запрошенного, и только потом читается весь ответ.
 */
@Throws(IOException::class)
fun BufferedSource.readAllBounded(maxBytes: Long): ByteArray {
    require(maxBytes > 0) { "maxBytes must be positive" }
    // request() вернёт true только если доступно СТРОГО больше лимита — значит ответ слишком большой.
    if (request(maxBytes + 1)) throw IOException("Ответ слишком большой (> $maxBytes байт)")
    return readByteArray()
}

/** То же, но результат сразу как UTF-8 строка. */
@Throws(IOException::class)
fun BufferedSource.readUtf8Bounded(maxBytes: Long): String =
    readAllBounded(maxBytes).toString(Charsets.UTF_8)
