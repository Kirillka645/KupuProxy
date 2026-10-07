package com.kupuproxy.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kupuproxy.desktop.AppState
import com.kupuproxy.desktop.ThemeMode
import com.kupuproxy.desktop.AutoStartBridge
import com.kupuproxy.desktop.DesktopTab
import com.kupuproxy.desktop.ProxyRow
import com.kupuproxy.desktop.ScanState
import com.kupuproxy.desktop.TrafficStats
import com.kupuproxy.desktop.pickProxyFile
import com.kupuproxy.shared.design.KupuMotion
import com.kupuproxy.shared.design.KupuControl
import com.kupuproxy.shared.design.KupuSpacing
import com.kupuproxy.shared.domain.check.LinkQuality
import com.kupuproxy.shared.domain.model.ProxyProtocol

/** Корень окна: навигация слева, контент справа — сетка для широких экранов. */
@Composable
fun DesktopRoot(state: AppState, onOpenTelegram: (String) -> Unit) {
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight().width(96.dp),
        ) {
            VSpace(KupuSpacing.lg)
            Text(
                "KupuProxy",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = KupuSpacing.sm, vertical = KupuSpacing.sm),
            )
            VSpace(KupuSpacing.md)
            DesktopTab.entries.forEach { entry ->
                NavigationRailItem(
                    selected = state.tab == entry,
                    onClick = { state.tab = entry },
                    icon = {
                        Icon(
                            imageVector = when (entry) {
                                DesktopTab.DASHBOARD -> Icons.Filled.Public
                                DesktopTab.PROXIES -> Icons.Filled.PlayArrow
                                DesktopTab.SOURCES -> Icons.Filled.FolderOpen
                                DesktopTab.SETTINGS -> Icons.Filled.Settings
                            },
                            contentDescription = entry.title,
                        )
                    },
                    label = { Text(entry.title, style = MaterialTheme.typography.labelSmall) },
                )
                VSpace(KupuSpacing.xs)
            }
        }

        HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))

        Box(Modifier.fillMaxSize()) {
            when (state.tab) {
                DesktopTab.DASHBOARD -> DashboardScreen(state)
                DesktopTab.PROXIES -> ProxiesScreen(state, onOpenTelegram)
                DesktopTab.SOURCES -> SourcesScreen(state)
                DesktopTab.SETTINGS -> SettingsScreen(state)
            }
        }
    }
}

// region Обзор

@Composable
private fun DashboardScreen(state: AppState) {
    val rows = state.rows
    val latencies = rows.map { it.latencyMs }
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(KupuSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(KupuSpacing.lg),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("KupuProxy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    state.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            ScanButton(state)
        }

        ScanProgress(state.scan)

        // Метрики в три колонки — раскладка рассчитана на широкий экран.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KupuSpacing.md)) {
            MetricTile(
                label = "Рабочих прокси",
                value = rows.size.toString(),
                accent = scheme.primary,
                caption = "из ${state.scan.candidates} кандидатов",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Медианная задержка",
                value = if (latencies.isEmpty()) "—" else "${median(latencies)} ms",
                accent = qualityColor(LinkQuality.of(median(latencies))),
                caption = "минимум ${latencies.minOrNull() ?: 0} ms",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Трафик",
                value = state.traffic.formatBytes(state.traffic.totalBytes),
                accent = scheme.tertiary,
                caption = "${state.traffic.connections} соединений",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Протоколы",
                value = rows.groupingBy { it.protocol }.eachCount().size.toString(),
                accent = scheme.secondary,
                caption = state.settings.protocolFilter.joinToString(" ") { protocolLabel(it) }.ifBlank { "—" },
                modifier = Modifier.weight(1f),
            )
        }

        // Графики в две колонки.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KupuSpacing.md)) {
            Card(
                Modifier.weight(1.4f),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
            ) {
                Column(Modifier.padding(KupuSpacing.cardPadding)) {
                    Text("Трафик за сессию", style = MaterialTheme.typography.titleMedium)
                    VSpace(KupuSpacing.sm)
                    Text(
                        "${state.traffic.formatBytes(state.traffic.upBytes)} вверх · " +
                            "${state.traffic.formatBytes(state.traffic.downBytes)} вниз",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    VSpace(KupuSpacing.lg)
                    TrafficSparkline(
                        history = state.traffic.history,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                }
            }

            Card(
                Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
            ) {
                Column(Modifier.padding(KupuSpacing.cardPadding)) {
                    Text("Распределение задержек", style = MaterialTheme.typography.titleMedium)
                    VSpace(KupuSpacing.md)
                    LatencyBars(values = latencies)
                    VSpace(KupuSpacing.md)
                    KeyValueRow("< 150 ms", latencies.count { it < 150 }.toString())
                    KeyValueRow("150–400 ms", latencies.count { it in 150..399 }.toString())
                    KeyValueRow("400–900 ms", latencies.count { it in 400..899 }.toString())
                    KeyValueRow("> 900 ms", latencies.count { it >= 900 }.toString())
                }
            }
        }

        Text("Топ-5 по задержке", style = MaterialTheme.typography.titleMedium)
        if (rows.isEmpty()) {
            EmptyState(
                title = "Прокси ещё не проверены",
                subtitle = "Откройте «Источники», выберите файл или URL со списком и запустите скан.",
            )
        } else {
            rows.take(5).forEach { row -> TopRow(row) }
        }

        VSpace(KupuSpacing.xxl)
    }
}

@Composable
private fun TopRow(row: ProxyRow) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(KupuSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(row.quality)
            HSpace(KupuSpacing.md)
            Column(Modifier.weight(1f)) {
                Text(row.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    protocolLabel(row.protocol) +
                        if (row.samples > 1) " · ${row.samples} замеров · jitter ${row.jitterMs} ms" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                "${row.latencyMs} ms",
                style = MaterialTheme.typography.titleMedium,
                color = qualityColor(row.quality),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ScanButton(state: AppState) {
    if (state.scan.running) {
        OutlinedButton(onClick = { state.cancelScan() }) {
            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
            HSpace(KupuSpacing.sm)
            Text("Стоп")
        }
    } else {
        Button(
            onClick = { state.tab = DesktopTab.SOURCES },
            modifier = Modifier.height(KupuControl.buttonHeight).width(180.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            HSpace(KupuSpacing.sm)
            Text("Запустить скан", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ScanProgress(scan: ScanState) {
    AnimatedVisibility(
        visible = scan.running || scan.total > 0,
        enter = fadeIn(tween(KupuMotion.standardMs)),
        exit = fadeOut(tween(KupuMotion.standardMs)),
    ) {
        if (scan.total > 0) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(KupuSpacing.cardPadding)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            if (scan.running) scan.phase else "Скан завершён",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${scan.processed} / ${scan.total}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    VSpace(KupuSpacing.sm)
                    LinearProgressIndicator(
                        progress = { if (scan.total > 0) scan.processed.toFloat() / scan.total else 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                }
            }
        }
    }
}

private fun median(values: List<Int>): Int {
    if (values.isEmpty()) return 0
    val sorted = values.sorted()
    return sorted[sorted.size / 2]
}

// endregion

// region Прокси

@Composable
private fun ProxiesScreen(state: AppState, onOpenTelegram: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val rows = state.filteredRows
    val selected = state.selectedRow

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1.6f).fillMaxHeight()) {
            // Поиск и фильтр по задержке.
            Column(Modifier.padding(KupuSpacing.lg)) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { state.searchQuery = it },
                    label = { Text("Поиск по хосту или ссылке") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                VSpace(KupuSpacing.md)
                Text(
                    "Фильтр по задержке: до ${state.settings.maxLatencyFilterMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Slider(
                    value = state.settings.maxLatencyFilterMs.toFloat(),
                    onValueChange = { value ->
                        state.updateSettings { it.copy(maxLatencyFilterMs = value.toInt()) }
                    },
                    valueRange = 100f..8_000f,
                    modifier = Modifier.fillMaxWidth(),
                )
                VSpace(KupuSpacing.xs)
                Text(
                    "${rows.size} из ${state.rows.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                VSpace(KupuSpacing.md)
                ScanProgress(state.scan)
            }

            HorizontalDivider()

            if (rows.isEmpty()) {
                EmptyState(
                    title = "Ничего не найдено",
                    subtitle = "Измените фильтры или запустите новый скан.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = KupuSpacing.lg,
                        vertical = KupuSpacing.sm,
                    ),
                ) {
                    items(rows, key = { it.url }) { row ->
                        ProxyTableRow(
                            row = row,
                            selected = row.url == selected?.url,
                            favorite = row.url in state.favorites,
                            onClick = { state.select(row.url) },
                            onFavorite = { state.toggleFavorite(row.url) },
                        )
                    }
                }
            }
        }

        // Боковая панель с действиями.
        Column(
            Modifier
                .width(320.dp)
                .fillMaxHeight()
                .background(scheme.surface)
                .padding(KupuSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KupuSpacing.md),
        ) {
            Text("Действия", style = MaterialTheme.typography.titleMedium)
            if (selected == null) {
                Text(
                    "Выберите прокси в списке.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                KeyValueRow("Адрес", selected.label)
                KeyValueRow("Протокол", protocolLabel(selected.protocol))
                KeyValueRow("Задержка", "${selected.latencyMs} ms")
                if (selected.samples > 1) {
                    KeyValueRow("Jitter", "${selected.jitterMs} ms")
                    KeyValueRow("Замеров", selected.samples.toString())
                }
                KeyValueRow("Качество", selected.quality.name.lowercase())

                VSpace(KupuSpacing.xs)
                Button(
                    onClick = { onOpenTelegram(selected.url) },
                    modifier = Modifier.fillMaxWidth().height(KupuControl.buttonHeight),
                ) {
                    Text("Открыть в Telegram", fontWeight = FontWeight.SemiBold)
                }

                val tunnelling = selected.protocol != ProxyProtocol.MTPROTO
                Button(
                    onClick = { state.toggleLocalProxy() },
                    enabled = tunnelling,
                    modifier = Modifier.fillMaxWidth().height(KupuControl.buttonHeight),
                ) {
                    Text(
                        if (state.localProxy.isRunning) "Остановить прокси" else "Включить локальный прокси",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (!tunnelling) {
                    Text(
                        "MTProto не поддерживает туннелирование — используйте режим «Открыть в Telegram».",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            VSpace(KupuSpacing.md)
            HorizontalDivider()
            VSpace(KupuSpacing.sm)

            if (state.localProxy.isRunning) {
                Text("Локальный прокси", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                KeyValueRow("Адрес", "127.0.0.1:${state.localProxy.localPort}")
                KeyValueRow("Отправлено", state.traffic.formatBytes(state.traffic.upBytes))
                KeyValueRow("Получено", state.traffic.formatBytes(state.traffic.downBytes))
                KeyValueRow("Соединений", state.traffic.connections.toString())
            }
        }
    }
}

@Composable
private fun ProxyTableRow(
    row: ProxyRow,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(
                if (selected) scheme.primaryContainer.copy(alpha = 0.45f) else scheme.surface,
                MaterialTheme.shapes.small,
            )
            .border(
                1.dp,
                if (selected) scheme.primary else scheme.outlineVariant,
                MaterialTheme.shapes.small,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = KupuSpacing.md, vertical = KupuSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(row.quality, size = 12.dp)
        HSpace(KupuSpacing.md)
        Column(Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                protocolLabel(row.protocol) +
                    if (row.samples > 1) " · jitter ${row.jitterMs} ms" else "",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        ProtocolBadge(row.protocol)
        HSpace(KupuSpacing.md)
        Text(
            "${row.latencyMs} ms",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = qualityColor(row.quality),
        )
        IconButton(onClick = onFavorite, modifier = Modifier.size(KupuSpacing.xxl)) {
            Icon(
                imageVector = if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "Избранное",
                tint = if (favorite) scheme.tertiary else scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// endregion

// region Источники

@Composable
private fun SourcesScreen(state: AppState) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(KupuSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(KupuSpacing.md),
    ) {
        Text("Источники прокси", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Поддерживаются MTProto, SOCKS5, HTTP и WEB. Форматы: ссылки, host:port:secret, " +
                "JSON, YAML, markdown-таблицы, HTML и base64-блоки.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        VSpace(KupuSpacing.sm)
        Text("Локальный файл", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { state.scanFile(pickProxyFile()) }, modifier = Modifier.height(KupuControl.buttonHeight)) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                HSpace(KupuSpacing.sm)
                Text("Выбрать файл…", fontWeight = FontWeight.SemiBold)
            }
        }

        VSpace(KupuSpacing.sm)
        Text("Источник по URL", style = MaterialTheme.typography.titleMedium)
        UrlScanBlock(state)

        VSpace(KupuSpacing.md)
        ScanProgress(state.scan)

        if (state.errorMessage != null) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(KupuSpacing.md).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        state.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    IconButton(onClick = { state.errorMessage = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (state.rows.isNotEmpty()) {
            Text("Найдено: ${state.rows.size}", style = MaterialTheme.typography.titleMedium)
            state.rows.take(3).forEach { TopRow(it) }
            if (state.rows.size > 3) {
                TextButton(onClick = { state.tab = DesktopTab.PROXIES }) {
                    Text("Открыть все ${state.rows.size} в списке")
                }
            }
        }
        VSpace(KupuSpacing.xxl)
    }
}

@Composable
private fun UrlScanBlock(state: AppState) {
    var url by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KupuSpacing.md)) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("https://…/proxies.txt") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { if (url.isNotBlank()) state.scanUrl(url.trim()) },
            enabled = url.isNotBlank(),
            modifier = Modifier.height(KupuControl.buttonHeight),
        ) {
            Text("Загрузить", fontWeight = FontWeight.SemiBold)
        }
    }
}

// endregion

// region Настройки

@Composable
private fun SettingsScreen(state: AppState) {
    val settings = state.settings
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(KupuSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(KupuSpacing.md),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SettingSwitch("Автозапуск при входе в систему", settings.autostart, enabled = AutoStartFlag()) { checked ->
            val applied = if (checked) AutoStartBridge.set(true) else AutoStartBridge.set(false)
            if (checked && !applied) {
                state.errorMessage = "Не удалось включить автозапуск для текущей системы"
            }
            state.updateSettings { it.copy(autostart = checked && applied) }
        }

        SettingSwitch("Сворачивать в трей вместо закрытия", settings.minimizeToTray) { checked ->
            state.updateSettings { it.copy(minimizeToTray = checked) }
        }

        SettingSwitch("Запускаться свёрнутым в трей", settings.startMinimized) { checked ->
            state.updateSettings { it.copy(startMinimized = checked) }
        }

        HorizontalDivider()

        Text("Тема", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(KupuSpacing.sm)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { state.updateSettings { it.copy(themeMode = mode) } },
                    label = {
                        Text(
                            when (mode) {
                                com.kupuproxy.desktop.ThemeMode.SYSTEM -> "Системная"
                                com.kupuproxy.desktop.ThemeMode.LIGHT -> "Светлая"
                                com.kupuproxy.desktop.ThemeMode.DARK -> "Тёмная"
                            },
                        )
                    },
                )
            }
        }

        HorizontalDivider()

        Text("Протоколы для проверки", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(KupuSpacing.sm)) {
            ProxyProtocol.entries.forEach { protocol ->
                FilterChip(
                    selected = protocol in settings.protocolFilter,
                    onClick = { state.toggleProtocol(protocol) },
                    label = { Text(protocolLabel(protocol)) },
                )
            }
        }

        HorizontalDivider()

        Text("Проверка", style = MaterialTheme.typography.titleMedium)
        Text(
            "Замеров на прокси: ${settings.jitterSamples}",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Slider(
            value = settings.jitterSamples.toFloat(),
            onValueChange = { value -> state.updateSettings { it.copy(jitterSamples = value.toInt()) } },
            valueRange = 1f..5f,
            steps = 3,
        )
        Text(
            "Максимум прокси за скан: ${settings.maxToCheck}",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Slider(
            value = settings.maxToCheck.toFloat(),
            onValueChange = { value -> state.updateSettings { it.copy(maxToCheck = value.toInt()) } },
            valueRange = 100f..10_000f,
        )

        HorizontalDivider()

        Text("Данные", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { state.resetTraffic() }) { Text("Сбросить статистику трафика") }
        TextButton(onClick = { state.favorites.forEach { state.toggleFavorite(it) } }) {
            Text("Очистить избранное (${settings.favorites.size})")
        }

        VSpace(KupuSpacing.xxl)
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = KupuSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun AutoStartFlag(): Boolean = com.kupuproxy.desktop.AutoStart.isSupported()

// endregion

@Composable
fun LoadingIndicator() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}