package com.kupuproxy.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kupuproxy.desktop.LocalStatusColors
import com.kupuproxy.shared.design.KupuMotion
import com.kupuproxy.shared.design.KupuSpacing
import com.kupuproxy.shared.domain.check.LinkQuality
import com.kupuproxy.shared.domain.model.ProxyProtocol

/** Цвет для индикатора задержки. */
@Composable
fun qualityColor(quality: LinkQuality): Color {
    val status = LocalStatusColors.current
    val scheme = MaterialTheme.colorScheme
    return when (quality) {
        LinkQuality.EXCELLENT -> status.excellent
        LinkQuality.GOOD -> status.good
        LinkQuality.FAIR -> status.slow
        LinkQuality.POOR -> scheme.error
        LinkQuality.UNKNOWN -> status.offline
    }
}

fun protocolLabel(protocol: ProxyProtocol): String = when (protocol) {
    ProxyProtocol.MTPROTO -> "MTProto"
    ProxyProtocol.SOCKS5 -> "SOCKS5"
    ProxyProtocol.HTTP -> "HTTP"
    ProxyProtocol.WEB -> "WEB"
}

/**
 * Индикатор статуса: точка с плавной анимацией цвета и масштаба.
 * Раньше статус показывался только порядком в списке — цветовых индикаторов не было вовсе.
 */
@Composable
fun StatusDot(
    quality: LinkQuality,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 10.dp,
) {
    val target = qualityColor(quality)
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(KupuMotion.stateFastMs),
        label = "statusDotColor",
    )
    val scale by animateFloatAsState(
        targetValue = if (quality == LinkQuality.UNKNOWN) 0.7f else 1f,
        animationSpec = spring(
            dampingRatio = KupuMotion.springDampingRatio,
            stiffness = KupuMotion.springStiffness,
        ),
        label = "statusDotScale",
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .background(color, CircleShape),
    )
}

/** Метрика в виде плитки. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    caption: String? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(KupuSpacing.cardPadding)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(KupuSpacing.xs))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            if (caption != null) {
                Spacer(Modifier.height(KupuSpacing.xxs))
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Спарклайн по истории трафика. Рисуется на Canvas — внешняя библиотека графиков не нужна.
 */
@Composable
fun TrafficSparkline(
    history: List<Long>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier) {
        if (history.size < 2) {
            // Пустое состояние: базовая линия, чтобы график не «мигал» пустотой.
            drawLine(
                color = scheme.outlineVariant,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 2f,
            )
            return@Canvas
        }

        val max = history.max().coerceAtLeast(1L).toFloat()
        val min = history.min().toFloat()
        val span = (max - min).coerceAtLeast(1f)
        val stepX = size.width / (history.size - 1).toFloat()

        val path = Path()
        history.forEachIndexed { index, value ->
            val x = stepX * index
            val normalized = (value - min) / span
            val y = size.height - normalized * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round),
        )

        val lastX = stepX * (history.size - 1)
        val lastY = size.height - ((history.last() - min) / span) * size.height
        drawCircle(color = color, radius = 4f, center = Offset(lastX, lastY))
    }
}

/** Горизонтальная полоса распределения задержек. */
@Composable
fun LatencyBars(
    values: List<Int>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    Row(
        modifier = modifier.fillMaxWidth().height(10.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val buckets = intArrayOf(0, 0, 0, 0)
        values.forEach { value ->
            val index = when {
                value < 150 -> 0
                value < 400 -> 1
                value < 900 -> 2
                else -> 3
            }
            buckets[index]++
        }
        val total = buckets.sum().coerceAtLeast(1)
        val colors = listOf(status.excellent, status.good, status.slow, scheme.error)
        buckets.forEachIndexed { index, count ->
            val weight = count.toFloat() / total
            Box(
                Modifier
                    .weight(weight.coerceAtLeast(0.01f))
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(colors[index].copy(alpha = if (count == 0) 0.15f else 0.85f)),
            )
        }
    }
}

/** Метка протокола компактной плашкой. */
@Composable
fun ProtocolBadge(protocol: ProxyProtocol, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = protocolLabel(protocol),
        style = MaterialTheme.typography.labelSmall,
        color = scheme.onSecondaryContainer,
        modifier = modifier
            .background(scheme.secondaryContainer, MaterialTheme.shapes.extraSmall)
            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = KupuSpacing.sm, vertical = 2.dp),
    )
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(KupuSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(KupuSpacing.sm))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = KupuSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun VSpace(size: androidx.compose.ui.unit.Dp) = Spacer(Modifier.height(size))

@Composable
fun HSpace(size: androidx.compose.ui.unit.Dp) = Spacer(Modifier.width(size))