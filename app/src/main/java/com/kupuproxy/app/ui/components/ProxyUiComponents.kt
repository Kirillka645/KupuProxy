package com.kupuproxy.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kupuproxy.app.ProxyWithPing
import com.kupuproxy.app.R
import com.kupuproxy.app.ui.theme.LocalStatusColors
import com.kupuproxy.shared.design.KupuControl
import com.kupuproxy.shared.design.KupuIconSize
import com.kupuproxy.shared.design.KupuMotion
import com.kupuproxy.shared.design.KupuRadius
import com.kupuproxy.shared.domain.check.LinkQuality
import com.kupuproxy.shared.domain.model.ProxyProtocol

/** Цвет индикатора задержки. */
@Composable
fun qualityColor(quality: LinkQuality): Color {
    val status = LocalStatusColors.current
    return when (quality) {
        LinkQuality.EXCELLENT -> status.excellent
        LinkQuality.GOOD -> status.good
        LinkQuality.FAIR -> status.fair
        LinkQuality.POOR -> status.poor
        LinkQuality.UNKNOWN -> status.offline
    }
}

/** Подпись протокола. MTProto — по умолчанию, поэтому его не подписываем. */
fun protocolLabel(protocol: ProxyProtocol): String =
    when (protocol) {
        ProxyProtocol.MTPROTO -> "MTProto"
        ProxyProtocol.SOCKS5 -> "SOCKS5"
        ProxyProtocol.HTTP -> "HTTP"
        ProxyProtocol.WEB -> "WEB"
    }

/**
 * Индикатор состояния: точка с плавной сменой цвета и масштаба.
 * Раньше статус передавался только порядком в списке и зелёной «галочкой».
 */
@Composable
fun StatusDot(quality: LinkQuality, modifier: Modifier = Modifier, size: Int = 10) {
    val target = qualityColor(quality)
    val color by
        animateColorAsState(
            targetValue = target,
            animationSpec = tween(KupuMotion.stateFastMs),
            label = "statusDotColor",
        )
    val scale by
        animateFloatAsState(
            targetValue = if (quality == LinkQuality.UNKNOWN) 0.7f else 1f,
            animationSpec =
                spring(
                    dampingRatio = KupuMotion.springDampingRatio,
                    stiffness = KupuMotion.springStiffness,
                ),
            label = "statusDotScale",
        )
    Box(
        modifier
            .size(size.dp)
            .scale(scale)
            .background(color, CircleShape),
    )
}

/** Компактная плашка протокола — рядом с задержкой видно, чем именно является прокси. */
@Composable
fun ProtocolBadge(protocol: ProxyProtocol, modifier: Modifier = Modifier) {
    if (protocol == ProxyProtocol.MTPROTO) return
    val scheme = MaterialTheme.colorScheme
    Text(
        text = protocolLabel(protocol),
        style = MaterialTheme.typography.labelSmall,
        color = scheme.onSecondaryContainer,
        modifier =
            modifier
                .background(scheme.secondaryContainer, MaterialTheme.shapes.extraSmall)
                .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = KupuControl.touchTarget / 8, vertical = 2.dp),
    )
}

@Composable
fun ProxyResultCard(
    proxy: ProxyWithPing,
    favorite: Boolean,
    onConnect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Значения читаются здесь, чтобы remember не кэшировал устаревшие после смены прокси.
    val quality = remember(proxy.url, proxy.pingMs) { LinkQuality.of(proxy.pingMs) }
    val pingColor = qualityColor(quality)

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onConnect,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(quality, size = 12)

            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    proxyEndpoint(proxy.url, stringResource(R.string.proxy_fallback_name)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        if (proxy.pingMs > 0) {
                            append(stringResource(R.string.ping_format, proxy.pingMs))
                            if (proxy.jitterMs > 0) {
                                append(stringResource(R.string.jitter_format, proxy.jitterMs))
                            }
                        } else {
                            append(stringResource(R.string.proxy_saved))
                        }
                        if (proxy.profileLabel.isNotBlank()) append(" · ${proxy.profileLabel}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (proxy.pingMs > 0) pingColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ProtocolBadge(proxy.protocol)

            if (onCopy != null) {
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                    )
                }
            }

            // Звезда переключается пружиной — смена избранного заметна без задержки.
            val starScale by
                animateFloatAsState(
                    targetValue = if (favorite) 1.15f else 1f,
                    animationSpec =
                        spring(
                            dampingRatio = KupuMotion.springDampingRatio,
                            stiffness = KupuMotion.springStiffness,
                        ),
                    label = "favoriteStar",
                )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (favorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                    contentDescription = stringResource(R.string.favorite),
                    tint =
                        if (favorite) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.scale(starScale),
                )
            }

            FilledTonalIconButton(onClick = onConnect) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.connect),
                    modifier = Modifier.size(KupuIconSize.large),
                )
            }
        }
    }
}

private fun proxyEndpoint(url: String, fallback: String): String {
    // SOCKS5/HTTP/WEB: адрес лежит в authority, а не в query-параметрах.
    val authority = url.substringAfter("://", "")
    if (authority.isNotEmpty() && !url.contains("://")) return fallback
    if (url.contains("://")) {
        val hostPort = authority.substringBefore('/').substringBefore('?')
        val afterAt = hostPort.substringAfterLast('@')
        return if (afterAt.contains(':')) afterAt else fallback
    }

    val query = url.substringAfter('?', "")
    var host = fallback
    var port = ""
    query.split('&').forEach { part ->
        val key = part.substringBefore('=')
        val value = part.substringAfter('=', "")
        when (key) {
            "server" -> host = value
            "port" -> port = value
        }
    }
    return if (port.isBlank()) host else "$host:$port"
}