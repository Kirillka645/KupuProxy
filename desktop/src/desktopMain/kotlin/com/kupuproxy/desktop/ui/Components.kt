package com.kupuproxy.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.kupuproxy.desktop.Fluent
import com.kupuproxy.desktop.LocalStatusColors
import com.kupuproxy.shared.domain.check.LinkQuality
import com.kupuproxy.shared.domain.model.ProxyProtocol

/** Цвет для индикатора задержки. */
@Composable
fun qualityColor(quality: LinkQuality): Color {
    val status = LocalStatusColors.current
    return when (quality) {
        LinkQuality.EXCELLENT -> status.excellent
        LinkQuality.GOOD -> status.good
        LinkQuality.FAIR -> status.slow
        LinkQuality.POOR -> Fluent.colors.critical
        LinkQuality.UNKNOWN -> status.offline
    }
}

fun protocolLabel(protocol: ProxyProtocol): String = when (protocol) {
    ProxyProtocol.MTPROTO -> "MTProto"
    ProxyProtocol.SOCKS5 -> "SOCKS5"
    ProxyProtocol.HTTP -> "HTTP"
    ProxyProtocol.WEB -> "HTTPS"
}

fun qualityLabel(quality: LinkQuality): String = when (quality) {
    LinkQuality.EXCELLENT -> "Отличное"
    LinkQuality.GOOD -> "Хорошее"
    LinkQuality.FAIR -> "Среднее"
    LinkQuality.POOR -> "Слабое"
    LinkQuality.UNKNOWN -> "Неизвестно"
}

@Composable
fun protocolColor(protocol: ProxyProtocol): Color {
    val c = Fluent.colors
    return when (protocol) {
        ProxyProtocol.MTPROTO -> c.accentText
        ProxyProtocol.SOCKS5 -> if (c.isDark) Color(0xFFB4A0FF) else Color(0xFF5B4AC4)
        ProxyProtocol.HTTP -> if (c.isDark) Color(0xFFFFB86B) else Color(0xFFB4570B)
        ProxyProtocol.WEB -> if (c.isDark) Color(0xFF8AD0FF) else Color(0xFF0063B1)
    }
}

/** Индикатор статуса: точка с плавной сменой цвета. */
@Composable
fun StatusDot(quality: LinkQuality, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val color by animateColorAsState(qualityColor(quality), tween(150), label = "statusDot")
    Box(modifier.size(size).background(color, CircleShape))
}

/** Карточка метрики: иконка в плашке, подпись, крупное значение. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    caption: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = Fluent.colors
    FluentCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconPlate(icon, accent)
            Spacer(Modifier.width(12.dp))
            FText(label, Fluent.type.body, c.textSecondary, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        FText(value, Fluent.type.title, c.textPrimary, maxLines = 1)
        if (caption != null) FText(caption, Fluent.type.caption, c.textTertiary, maxLines = 1)
    }
}

/** График трафика (байт/с) с заливкой под линией. */
@Composable
fun TrafficSparkline(history: List<Long>, modifier: Modifier = Modifier, color: Color = Fluent.colors.accent) {
    val c = Fluent.colors
    Canvas(modifier) {
        // Сетка — три горизонтальные линии.
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(c.divider, Offset(0f, y), Offset(size.width, y), 1f)
        }
        if (history.size < 2) {
            drawLine(c.controlStrong.copy(alpha = 0.4f), Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
            return@Canvas
        }
        val max = history.max().coerceAtLeast(1L).toFloat()
        val stepX = size.width / (history.size - 1).toFloat()
        val line = Path()
        history.forEachIndexed { index, value ->
            val x = stepX * index
            val y = size.height - (value / max) * (size.height - 4f)
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f))))
        drawPath(line, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Гистограмма задержек по корзинам. */
@Composable
fun LatencyHistogram(values: List<Int>, modifier: Modifier = Modifier) {
    val c = Fluent.colors
    val status = LocalStatusColors.current
    val labels = listOf("< 150", "150–400", "400–900", "> 900")
    val colors = listOf(status.excellent, status.good, status.slow, c.critical)
    val buckets = IntArray(4)
    values.forEach { v ->
        buckets[
            when {
                v < 150 -> 0
                v < 400 -> 1
                v < 900 -> 2
                else -> 3
            },
        ]++
    }
    val max = buckets.max().coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        buckets.forEachIndexed { i, count ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FText("${labels[i]} ms", Fluent.type.caption, c.textSecondary, Modifier.width(72.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.subtleHover)) {
                    Box(
                        Modifier
                            .fillMaxWidth(count.toFloat() / max)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors[i].copy(alpha = if (count == 0) 0f else 0.9f)),
                    )
                }
                FText(count.toString(), Fluent.type.caption, c.textPrimary, Modifier.width(44.dp).padding(start = 8.dp))
            }
        }
    }
}

/** Метка протокола. */
@Composable
fun ProtocolBadge(protocol: ProxyProtocol, modifier: Modifier = Modifier) {
    Tag(protocolLabel(protocol), protocolColor(protocol), modifier)
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = Fluent.colors
    Column(modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Icon(icon, null, tint = c.textTertiary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
        }
        FText(title, Fluent.type.subtitle, c.textPrimary)
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Text(
            subtitle,
            style = Fluent.type.body,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(420.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    val c = Fluent.colors
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        FText(label, Fluent.type.body, c.textSecondary)
        Spacer(Modifier.width(12.dp))
        FText(value, Fluent.type.bodyStrong, valueColor ?: c.textPrimary, maxLines = 1)
    }
}

/**
 * QR-код ссылки на прокси: телефон считывает его камерой, и прокси открывается в Telegram.
 * Рисуется на Canvas по матрице ZXing (та же библиотека, что и в Android-клиенте).
 */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
        }.getOrNull()
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (matrix != null) {
            Canvas(Modifier.fillMaxWidth().height(size - 20.dp)) {
                val n = matrix.width
                val cell = minOf(this.size.width, this.size.height) / n
                val offset = Offset((this.size.width - cell * n) / 2, (this.size.height - cell * n) / 2)
                for (y in 0 until n) {
                    for (x in 0 until n) {
                        if (matrix[x, y]) {
                            drawRect(
                                Color(0xFF111111),
                                Offset(offset.x + x * cell, offset.y + y * cell),
                                Size(cell + 0.5f, cell + 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VSpace(size: Dp) = Spacer(Modifier.height(size))

@Composable
fun HSpace(size: Dp) = Spacer(Modifier.width(size))
