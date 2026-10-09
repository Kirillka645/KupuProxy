@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kupuproxy.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kupuproxy.desktop.AccentMode
import com.kupuproxy.desktop.AppPaths
import com.kupuproxy.desktop.AppState
import com.kupuproxy.desktop.AutoStart
import com.kupuproxy.desktop.AutoStartBridge
import com.kupuproxy.desktop.CopyKind
import com.kupuproxy.desktop.DESKTOP_VERSION
import com.kupuproxy.desktop.DesktopTab
import com.kupuproxy.desktop.Fluent
import com.kupuproxy.desktop.Notice
import com.kupuproxy.desktop.ProxyLinks
import com.kupuproxy.desktop.ProxyRow
import com.kupuproxy.desktop.ScanState
import com.kupuproxy.desktop.Severity
import com.kupuproxy.desktop.SortMode
import com.kupuproxy.desktop.StockFeeds
import com.kupuproxy.desktop.SystemActions
import com.kupuproxy.desktop.TelegramLauncher
import com.kupuproxy.desktop.ThemeMode
import com.kupuproxy.desktop.UpdateChecker
import com.kupuproxy.desktop.pickExportFile
import com.kupuproxy.desktop.pickProxyFile
import com.kupuproxy.desktop.pickTelegramExecutable
import com.kupuproxy.shared.domain.check.LinkQuality
import com.kupuproxy.shared.domain.model.ProxyProtocol
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val PagePadding = PaddingValues(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 24.dp)

/**
 * Корень окна в стиле Windows 11: панель навигации (NavigationView) на слое Mica слева и
 * «слой контента» со скруглённым верхним левым углом справа.
 */
@Composable
fun DesktopRoot(state: AppState) {
    val c = Fluent.colors
    CompositionLocalProvider(
        LocalScrollbarStyle provides ScrollbarStyle(
            minimalHeight = 24.dp,
            thickness = 6.dp,
            shape = RoundedCornerShape(3.dp),
            hoverDurationMillis = 200,
            unhoverColor = c.textTertiary.copy(alpha = 0.35f),
            hoverColor = c.textSecondary.copy(alpha = 0.6f),
        ),
    ) {
        Row(Modifier.fillMaxSize().background(c.mica)) {
            NavigationPane(state)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 8.dp))
                    .background(c.layer)
                    .border(1.dp, c.layerStroke, RoundedCornerShape(topStart = 8.dp)),
            ) {
                when (state.tab) {
                    DesktopTab.DASHBOARD -> HomePage(state)
                    DesktopTab.PROXIES -> ProxiesPage(state)
                    DesktopTab.SOURCES -> SourcesPage(state)
                    DesktopTab.SETTINGS -> SettingsPage(state)
                }
                NoticeHost(state, Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp))
            }
        }
    }
}

// region Навигация

@Composable
private fun NavigationPane(state: AppState) {
    val c = Fluent.colors
    val expanded = !state.settings.navCollapsed
    val width by animateDpAsState(if (expanded) 264.dp else 52.dp, tween(200), label = "paneWidth")
    Column(Modifier.width(width).fillMaxHeight().padding(vertical = 6.dp, horizontal = 4.dp)) {
        Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            FluentIconButton(
                Icons.Outlined.Menu,
                if (expanded) "Свернуть панель" else "Развернуть панель",
                onClick = { state.updateSettings { it.copy(navCollapsed = !it.navCollapsed) } },
                size = 40.dp,
            )
            if (expanded) {
                Spacer(Modifier.width(8.dp))
                Image(painterResource("icon.png"), null, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                FText("KupuProxy", Fluent.type.caption, c.textPrimary, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        NavItem(state, DesktopTab.DASHBOARD, Icons.Outlined.Home, expanded)
        NavItem(state, DesktopTab.PROXIES, Icons.Outlined.Dns, expanded, badge = state.rows.size.takeIf { it > 0 })
        NavItem(state, DesktopTab.SOURCES, Icons.Outlined.CloudDownload, expanded)
        Spacer(Modifier.weight(1f))
        if (expanded) PaneStatus(state)
        FluentDivider(Modifier.padding(vertical = 4.dp, horizontal = 4.dp))
        NavItem(state, DesktopTab.SETTINGS, Icons.Outlined.Settings, expanded)
    }
}

@Composable
private fun NavItem(state: AppState, tab: DesktopTab, icon: ImageVector, expanded: Boolean, badge: Int? = null) {
    val c = Fluent.colors
    val selected = state.tab == tab
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pill by animateDpAsState(if (selected) 16.dp else 0.dp, tween(167), label = "navPill")
    val item: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(vertical = 1.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected || hovered) c.subtleHover else Color.Transparent)
                .hoverable(interaction)
                .clickable(interaction, null) { state.tab = tab },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(pill).clip(CircleShape).background(c.accent))
            Spacer(Modifier.width(13.dp))
            Box {
                Icon(icon, tab.title, tint = if (selected) c.accentText else c.textPrimary, modifier = Modifier.size(18.dp))
                if (!expanded && badge != null) {
                    Box(Modifier.align(Alignment.TopEnd).size(6.dp).background(c.accent, CircleShape))
                }
            }
            if (expanded) {
                Spacer(Modifier.width(16.dp))
                FText(tab.title, Fluent.type.body, c.textPrimary, Modifier.weight(1f), maxLines = 1)
                if (badge != null) {
                    Box(
                        Modifier.padding(end = 10.dp).clip(CircleShape).background(c.accent).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FText(if (badge > 999) "999+" else badge.toString(), Fluent.type.caption, c.onAccent)
                    }
                }
            }
        }
    }
    if (expanded) item() else FluentTooltip(tab.title) { item() }
}

/** Статус внизу панели: ход скана и локальный прокси. */
@Composable
private fun PaneStatus(state: AppState) {
    val c = Fluent.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.busy) {
            FText(state.scan.phase.ifBlank { "Сканирование…" }, Fluent.type.caption, c.textSecondary, maxLines = 1)
            ProgressBar(state.scan.progressOrNull())
        }
        if (state.localProxyRunning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(c.success, CircleShape))
                Spacer(Modifier.width(8.dp))
                FText("Локальный прокси · 127.0.0.1:${state.localProxy.localPort}", Fluent.type.caption, c.textSecondary, maxLines = 1)
            }
        }
    }
}

// endregion

// region Уведомления

@Composable
private fun NoticeHost(state: AppState, modifier: Modifier) {
    var last by remember { mutableStateOf<Notice?>(null) }
    val notice = state.notice
    if (notice != null) last = notice
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            delay(if (notice.action != null) 9_000 else 4_500)
            state.dismissNotice(notice.id)
        }
    }
    AnimatedVisibility(
        visible = notice != null,
        modifier = modifier,
        enter = fadeIn(tween(167)) + slideInVertically(tween(250)) { it / 2 },
        exit = fadeOut(tween(167)) + slideOutVertically(tween(167)) { it / 3 },
    ) {
        val shown = last ?: return@AnimatedVisibility
        InfoBar(
            severity = shown.severity,
            title = shown.title,
            message = shown.message,
            actionLabel = shown.actionLabel,
            onAction = shown.action?.let { action -> { action(); state.dismissNotice(shown.id) } },
            onClose = { state.dismissNotice(shown.id) },
            elevated = true,
            modifier = Modifier.widthIn(min = 360.dp, max = 680.dp),
        )
    }
}

/** InfoBar'ы, которые показываются вверху страниц: ошибка, обновление, кэш. */
@Composable
private fun PageBanners(state: AppState, showCache: Boolean = false) {
    state.errorMessage?.let { error ->
        InfoBar(Severity.ERROR, "Ошибка", error, Modifier.fillMaxWidth(), onClose = { state.errorMessage = null })
        Spacer(Modifier.height(12.dp))
    }
    state.update?.let { update ->
        InfoBar(
            Severity.INFO,
            "Доступна версия ${update.version}",
            "Сейчас установлена $DESKTOP_VERSION.",
            Modifier.fillMaxWidth(),
            actionLabel = "Скачать",
            onAction = { SystemActions.browse(update.releaseUrl) },
        )
        Spacer(Modifier.height(12.dp))
    }
    if (showCache && state.restoredFromCache && !state.busy) {
        InfoBar(
            Severity.INFO,
            "Список из прошлого сеанса",
            "Проверено ${formatTime(state.lastScanAt)}. Прокси могли перестать работать — перепроверьте их.",
            Modifier.fillMaxWidth(),
            actionLabel = "Перепроверить",
            onAction = { state.recheckFound() },
        )
        Spacer(Modifier.height(12.dp))
    }
}

// endregion

// region Главная

@Composable
private fun HomePage(state: AppState) {
    val c = Fluent.colors
    val rows = state.rows
    val latencies = rows.map { it.latencyMs }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(PagePadding)) {
            PageHeader("Главная", state.statusMessage)
            Spacer(Modifier.height(20.dp))
            PageBanners(state, showCache = true)
            HeroCard(state)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    "Рабочих прокси",
                    rows.size.toString(),
                    Icons.Outlined.Dns,
                    c.accent,
                    if (state.scan.candidates > 0) "из ${state.scan.candidates} проверенных" else "запустите скан",
                    Modifier.weight(1f),
                )
                MetricTile(
                    "Лучшая задержка",
                    latencies.minOrNull()?.let { "$it ms" } ?: "—",
                    Icons.Outlined.Speed,
                    qualityColor(LinkQuality.of(latencies.minOrNull() ?: -1)),
                    if (latencies.isEmpty()) "нет данных" else "медиана ${median(latencies)} ms",
                    Modifier.weight(1f),
                )
                MetricTile(
                    "Протоколы",
                    rows.map { it.protocol }.distinct().size.toString(),
                    Icons.Outlined.Layers,
                    protocolColor(ProxyProtocol.SOCKS5),
                    rows.groupingBy { it.protocol }.eachCount().entries.joinToString(" · ") { "${protocolLabel(it.key)} ${it.value}" }.ifBlank { "—" },
                    Modifier.weight(1f),
                )
                MetricTile(
                    "Трафик",
                    state.traffic.formatBytes(state.traffic.totalBytes),
                    Icons.Outlined.SwapVert,
                    protocolColor(ProxyProtocol.HTTP),
                    if (state.localProxyRunning) "${state.traffic.connections} соединений" else "локальный прокси выключен",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BestProxiesCard(state, Modifier.weight(1.5f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FluentCard(Modifier.fillMaxWidth()) {
                        FText("Распределение задержек", Fluent.type.bodyStrong)
                        Spacer(Modifier.height(14.dp))
                        LatencyHistogram(latencies)
                    }
                    FluentCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FText("Трафик локального прокси", Fluent.type.bodyStrong, modifier = Modifier.weight(1f))
                            FText(
                                "↑ ${state.traffic.formatBytes(state.traffic.upBytes)}  ↓ ${state.traffic.formatBytes(state.traffic.downBytes)}",
                                Fluent.type.caption,
                                c.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        TrafficSparkline(state.traffic.history, Modifier.fillMaxWidth().height(96.dp))
                        Spacer(Modifier.height(4.dp))
                        FText("байт/с за последние 2 минуты", Fluent.type.caption, c.textTertiary)
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp))
    }
}

@Composable
private fun HeroCard(state: AppState) {
    val c = Fluent.colors
    val scan = state.scan
    FluentCard(Modifier.fillMaxWidth(), padding = PaddingValues(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconPlate(if (state.busy) Icons.Outlined.Bolt else Icons.Outlined.Public, c.accent, size = 48.dp)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                FText(
                    when {
                        state.busy -> scan.phase.ifBlank { "Подготовка…" }
                        state.rows.isEmpty() -> "Найдите рабочие прокси для Telegram"
                        else -> "Найдено ${state.rows.size} рабочих прокси"
                    },
                    Fluent.type.subtitle,
                )
                FText(
                    when {
                        state.busy && scan.total > 0 -> "${scan.processed} из ${scan.total} · найдено ${state.rows.size}"
                        state.busy -> "Загрузка списков…"
                        state.rows.isEmpty() -> "Проверим встроенные источники MTProto и SOCKS5 и покажем самые быстрые."
                        else -> "Последняя проверка: ${formatTime(state.lastScanAt)} · ${state.sourceLabel.takeIf { it != "—" } ?: "встроенные источники"}"
                    },
                    Fluent.type.body,
                    c.textSecondary,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(16.dp))
            if (state.busy) {
                FluentButton("Остановить", { state.cancelScan() }, icon = Icons.Outlined.Stop)
            } else {
                if (state.rows.isNotEmpty()) {
                    FluentButton("Перепроверить", { state.recheckFound() }, icon = Icons.Outlined.Refresh)
                    Spacer(Modifier.width(8.dp))
                }
                FluentTooltip("Ctrl+R") {
                    FluentButton("Найти прокси", { state.scanStock() }, style = ButtonStyle.ACCENT, icon = Icons.Outlined.PlayArrow)
                }
            }
        }
        if (state.busy) {
            Spacer(Modifier.height(18.dp))
            ProgressBar(scan.progressOrNull())
        }
    }
}

@Composable
private fun BestProxiesCard(state: AppState, modifier: Modifier) {
    val c = Fluent.colors
    FluentCard(modifier, padding = PaddingValues(vertical = 12.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            FText("Лучшие прокси", Fluent.type.bodyStrong, modifier = Modifier.weight(1f))
            FluentButton("Все прокси", { state.tab = DesktopTab.PROXIES }, style = ButtonStyle.SUBTLE)
        }
        Spacer(Modifier.height(4.dp))
        if (state.rows.isEmpty()) {
            EmptyState(
                "Пока пусто",
                if (state.busy) "Рабочие прокси появятся здесь сразу, как только будут найдены." else "Нажмите «Найти прокси» — проверка займёт меньше минуты.",
                icon = Icons.Outlined.Dns,
            )
        } else {
            state.rows.take(7).forEach { row ->
                CompactProxyRow(state, row)
            }
        }
    }
}

@Composable
private fun CompactProxyRow(state: AppState, row: ProxyRow) {
    val c = Fluent.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val links = remember(row.url) { ProxyLinks.of(row.url) }
    ProxyContextMenu(state, row) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (hovered) c.subtleHover else Color.Transparent)
                .hoverable(interaction)
                .clickable(interaction, null) {
                    state.select(row.url)
                    state.tab = DesktopTab.PROXIES
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(row.quality)
            Spacer(Modifier.width(12.dp))
            FText(row.label, Fluent.type.body, modifier = Modifier.weight(1f), maxLines = 1)
            ProtocolBadge(row.protocol)
            Spacer(Modifier.width(12.dp))
            FText("${row.latencyMs} ms", Fluent.type.bodyStrong, qualityColor(row.quality), Modifier.width(72.dp))
            if (links?.tg != null) {
                FluentIconButton(Icons.AutoMirrored.Outlined.Send, "Открыть в Telegram", { state.openInTelegram(row.url) }, tint = c.accentText)
            } else {
                Spacer(Modifier.width(Fluent.controlHeight))
            }
            FluentIconButton(Icons.Outlined.ContentCopy, "Скопировать прокси", { state.copyProxy(row.url) })
        }
    }
}

// endregion

// region Прокси

@Composable
private fun ProxiesPage(state: AppState) {
    val c = Fluent.colors
    val rows = state.filteredRows
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(state.searchFocusRequests) {
        if (state.searchFocusRequests > 0) runCatching { searchFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(PagePadding)) {
        PageHeader(
            "Прокси",
            if (state.rows.isEmpty()) "Список пуст — запустите скан" else "Показано ${rows.size} из ${state.rows.size}" +
                if (state.lastScanAt > 0) " · проверено ${formatTime(state.lastScanAt)}" else "",
        )
        Spacer(Modifier.height(16.dp))
        PageBanners(state)

        // CommandBar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.busy) {
                FluentButton("Остановить", { state.cancelScan() }, icon = Icons.Outlined.Stop)
            } else {
                FluentTooltip("Проверить встроенные источники (Ctrl+R)") {
                    FluentButton("Сканировать", { state.scanStock() }, style = ButtonStyle.ACCENT, icon = Icons.Outlined.PlayArrow)
                }
            }
            FluentButton("Перепроверить", { state.recheckFound() }, style = ButtonStyle.SUBTLE, icon = Icons.Outlined.Refresh, enabled = !state.busy && state.rows.isNotEmpty())
            FluentDivider(Modifier.height(20.dp).padding(horizontal = 4.dp), vertical = true)
            FluentButton("Копировать все", { state.copyAll() }, style = ButtonStyle.SUBTLE, icon = Icons.Outlined.ContentCopy, enabled = rows.isNotEmpty())
            FluentButton("Экспорт…", { pickExportFile()?.let(state::exportTo) }, style = ButtonStyle.SUBTLE, icon = Icons.Outlined.FileDownload, enabled = rows.isNotEmpty())
            Spacer(Modifier.weight(1f))
            ComboBox(SortMode.entries, state.settings.sortMode, { it.title }, { mode -> state.updateSettings { it.copy(sortMode = mode) } })
            Spacer(Modifier.width(4.dp))
            TextBox(
                state.searchQuery,
                { state.searchQuery = it },
                "Поиск (Ctrl+F)",
                Modifier.width(240.dp),
                leadingIcon = Icons.Outlined.Search,
                focusRequester = searchFocus,
            )
        }
        Spacer(Modifier.height(12.dp))

        // Фильтры
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProxyProtocol.entries.forEach { protocol ->
                val count = state.rows.count { it.protocol == protocol }
                ToggleChip(
                    protocolLabel(protocol) + if (count > 0) " · $count" else "",
                    protocol in state.settings.protocolFilter,
                    { state.toggleProtocol(protocol) },
                )
            }
            Spacer(Modifier.width(6.dp))
            ToggleChip(
                "Избранные · ${state.favorites.size}",
                state.onlyFavorites,
                { state.onlyFavorites = !state.onlyFavorites },
                icon = Icons.Filled.Star,
            )
            Spacer(Modifier.weight(1f))
            FText("Задержка до ${state.settings.maxLatencyFilterMs} ms", Fluent.type.caption, c.textSecondary)
            FluentSlider(
                state.settings.maxLatencyFilterMs.toFloat(),
                { v -> state.updateSettings { it.copy(maxLatencyFilterMs = (v / 50f).roundToInt() * 50) } },
                100f..8000f,
                Modifier.width(200.dp),
            )
        }
        if (state.busy || state.scan.running) {
            Spacer(Modifier.height(10.dp))
            ScanStrip(state.scan)
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().weight(1f)) {
            ProxyGrid(state, rows, Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.width(16.dp))
            DetailsPane(state, Modifier.width(344.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun ScanStrip(scan: ScanState) {
    val c = Fluent.colors
    Column(Modifier.fillMaxWidth()) {
        Row {
            FText(scan.phase.ifBlank { "Загрузка списков…" }, Fluent.type.caption, c.textSecondary, Modifier.weight(1f))
            if (scan.total > 0) FText("${scan.processed} / ${scan.total} · найдено ${scan.found}", Fluent.type.caption, c.textSecondary)
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(scan.progressOrNull())
    }
}

private object Cols {
    val star = 36.dp
    val protocol = 96.dp
    val ping = 80.dp
    val jitter = 72.dp
    val source = 150.dp
    val actions = 72.dp
}

@Composable
private fun ProxyGrid(state: AppState, rows: List<ProxyRow>, modifier: Modifier) {
    val c = Fluent.colors
    val listState = rememberLazyListState()
    // ↑/↓ меняют выбор — прокручиваем к выбранной строке, если она за краем.
    LaunchedEffect(state.selectedUrl, rows.size) {
        val index = rows.indexOfFirst { it.url == state.selectedUrl }
        if (index >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val first = visible.firstOrNull()?.index ?: 0
            val last = visible.lastOrNull()?.index ?: 0
            if (index < first || index >= last) listState.animateScrollToItem((index - 2).coerceAtLeast(0))
        }
    }
    FluentCard(modifier, padding = PaddingValues(0.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val showSource = maxWidth > 720.dp
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(Cols.star))
                    HeaderCell("Адрес", Modifier.weight(1f).padding(start = 18.dp))
                    HeaderCell("Протокол", Modifier.width(Cols.protocol), sort = SortMode.PROTOCOL, state = state)
                    HeaderCell("Задержка", Modifier.width(Cols.ping), sort = SortMode.PING, state = state)
                    HeaderCell("Jitter", Modifier.width(Cols.jitter), sort = SortMode.JITTER, state = state)
                    if (showSource) HeaderCell("Источник", Modifier.width(Cols.source))
                    Spacer(Modifier.width(Cols.actions))
                }
                FluentDivider()
                if (rows.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            if (state.rows.isEmpty()) "Прокси ещё не найдены" else "Ничего не подходит под фильтры",
                            if (state.rows.isEmpty()) {
                                if (state.busy) "Рабочие прокси появятся здесь по мере проверки." else "Запустите скан встроенных источников или добавьте свой список в «Источниках»."
                            } else {
                                "Измените поиск, протоколы или порог задержки."
                            },
                            icon = Icons.Outlined.Dns,
                            action = if (state.rows.isEmpty() && !state.busy) {
                                { FluentButton("Сканировать", { state.scanStock() }, style = ButtonStyle.ACCENT, icon = Icons.Outlined.PlayArrow) }
                            } else {
                                null
                            },
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(rows, key = { it.url }) { row ->
                                GridRow(state, row, showSource)
                            }
                        }
                        VerticalScrollbar(
                            rememberScrollbarAdapter(listState),
                            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, sort: SortMode? = null, state: AppState? = null) {
    val c = Fluent.colors
    val active = sort != null && state?.settings?.sortMode == sort
    Row(
        modifier.then(
            if (sort != null && state != null) Modifier.clickable { state.updateSettings { it.copy(sortMode = sort) } } else Modifier,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(text, Fluent.type.caption, if (active) c.textPrimary else c.textSecondary, maxLines = 1)
        if (active) FText(" ↑", Fluent.type.caption, c.accentText)
    }
}

@Composable
private fun GridRow(state: AppState, row: ProxyRow, showSource: Boolean) {
    val c = Fluent.colors
    val selected = row.url == state.selectedUrl
    val favorite = row.url in state.favorites
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val links = remember(row.url) { ProxyLinks.of(row.url) }
    var lastClick by remember { mutableStateOf(0L) }
    ProxyContextMenu(state, row) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 4.dp, vertical = 1.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    when {
                        selected -> c.accent.copy(alpha = if (c.isDark) 0.16f else 0.10f)
                        hovered -> c.subtleHover
                        else -> Color.Transparent
                    },
                )
                .hoverable(interaction)
                .clickable(interaction, null) {
                    // Двойной щелчок — открыть в Telegram. Свой детектор: combinedClickable
                    // задерживает одиночный клик, и выбор строки «тормозил» бы.
                    val now = System.currentTimeMillis()
                    if (selected && now - lastClick < 400) state.openInTelegram(row.url) else state.select(row.url)
                    lastClick = now
                }
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(if (selected) 16.dp else 0.dp).clip(CircleShape).background(c.accent))
            Box(Modifier.width(Cols.star), contentAlignment = Alignment.Center) {
                FluentIconButton(
                    if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    if (favorite) "Убрать из избранного" else "В избранное",
                    { state.toggleFavorite(row.url) },
                    tint = if (favorite) c.caution else c.textTertiary,
                    size = 28.dp,
                )
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(row.quality)
                Spacer(Modifier.width(10.dp))
                FText(row.label, Fluent.type.body, maxLines = 1)
            }
            Box(Modifier.width(Cols.protocol)) { ProtocolBadge(row.protocol) }
            FText("${row.latencyMs} ms", Fluent.type.bodyStrong, qualityColor(row.quality), Modifier.width(Cols.ping), maxLines = 1)
            FText(if (row.samples > 1) "±${row.jitterMs} ms" else "—", Fluent.type.body, c.textSecondary, Modifier.width(Cols.jitter), maxLines = 1)
            if (showSource) FText(row.source.ifBlank { "—" }, Fluent.type.caption, c.textTertiary, Modifier.width(Cols.source), maxLines = 1)
            Row(Modifier.width(Cols.actions), horizontalArrangement = Arrangement.End) {
                if (hovered || selected) {
                    if (links?.tg != null) {
                        FluentIconButton(Icons.AutoMirrored.Outlined.Send, "Открыть в Telegram (Enter)", { state.openInTelegram(row.url) }, tint = c.accentText, size = 30.dp)
                    }
                    FluentIconButton(Icons.Outlined.ContentCopy, "Скопировать (Ctrl+C)", { state.copyProxy(row.url) }, size = 30.dp)
                }
            }
        }
    }
}

/** Контекстное меню строки (правая кнопка мыши). */
@Composable
private fun ProxyContextMenu(state: AppState, row: ProxyRow, content: @Composable () -> Unit) {
    ContextMenuArea(
        items = {
            val links = ProxyLinks.of(row.url)
            buildList {
                if (links?.tg != null) add(ContextMenuItem("Открыть в Telegram") { state.openInTelegram(row.url) })
                add(ContextMenuItem("Скопировать прокси") { state.copyProxy(row.url) })
                if (links?.tg != null) add(ContextMenuItem("Скопировать tg://-ссылку") { state.copyProxy(row.url, CopyKind.TG) })
                add(ContextMenuItem("Скопировать адрес") { state.copyProxy(row.url, CopyKind.ADDRESS) })
                add(
                    ContextMenuItem(if (row.url in state.favorites) "Убрать из избранного" else "Добавить в избранное") {
                        state.toggleFavorite(row.url)
                    },
                )
            }
        },
        content = content,
    )
}

@Composable
private fun DetailsPane(state: AppState, modifier: Modifier) {
    val c = Fluent.colors
    val row = state.selectedRow
    val scroll = rememberScrollState()
    FluentCard(modifier, padding = PaddingValues(0.dp)) {
        if (row == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("Выберите прокси", "Подробности, QR-код и действия появятся здесь.", icon = Icons.Outlined.Info)
            }
            return@FluentCard
        }
        val links = remember(row.url) { ProxyLinks.of(row.url) }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(row.quality, size = 10.dp)
                    Spacer(Modifier.width(10.dp))
                    SelectionContainer(Modifier.weight(1f)) {
                        FText(row.label, Fluent.type.subtitle, maxLines = 2)
                    }
                    val favorite = row.url in state.favorites
                    FluentIconButton(
                        if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        if (favorite) "Убрать из избранного" else "В избранное",
                        { state.toggleFavorite(row.url) },
                        tint = if (favorite) c.caution else c.textSecondary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProtocolBadge(row.protocol)
                    Tag(qualityLabel(row.quality), qualityColor(row.quality))
                }
                Spacer(Modifier.height(18.dp))
                if (links?.tg != null) {
                    FluentButton(
                        "Открыть в Telegram",
                        { state.openInTelegram(row.url) },
                        Modifier.fillMaxWidth(),
                        style = ButtonStyle.ACCENT,
                        icon = Icons.AutoMirrored.Outlined.Send,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FluentButton("Скопировать прокси", { state.copyProxy(row.url) }, Modifier.weight(1f), icon = Icons.Outlined.ContentCopy)
                        FluentTooltip("Скопировать tg://-ссылку") {
                            FluentButton("tg://", { state.copyProxy(row.url, CopyKind.TG) })
                        }
                    }
                } else {
                    FluentButton(
                        "Скопировать адрес",
                        { state.copyProxy(row.url, CopyKind.ADDRESS) },
                        Modifier.fillMaxWidth(),
                        style = ButtonStyle.ACCENT,
                        icon = Icons.Outlined.ContentCopy,
                    )
                    Spacer(Modifier.height(6.dp))
                    FText(
                        "Telegram не принимает ${protocolLabel(row.protocol)}-прокси ссылкой: добавьте адрес вручную " +
                            "или включите локальный прокси ниже.",
                        Fluent.type.caption,
                        c.textSecondary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                FluentButton("Скопировать адрес host:port", { state.copyProxy(row.url, CopyKind.ADDRESS) }, Modifier.fillMaxWidth(), style = ButtonStyle.SUBTLE)

                Spacer(Modifier.height(12.dp))
                FluentDivider()
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Задержка", "${row.latencyMs} ms", valueColor = qualityColor(row.quality))
                KeyValueRow("Jitter", if (row.samples > 1) "±${row.jitterMs} ms" else "—")
                KeyValueRow("Замеров", row.samples.toString())
                KeyValueRow("Источник", row.source.ifBlank { "—" })
                KeyValueRow("Проверен", if (row.checkedAt > 0) formatTime(row.checkedAt) else "—")

                val qrText = links?.tme
                if (qrText != null) {
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        QrCode(qrText)
                        Spacer(Modifier.height(8.dp))
                        FText("Наведите камеру телефона — прокси откроется в Telegram", Fluent.type.caption, c.textSecondary)
                    }
                }

                if (row.protocol != ProxyProtocol.MTPROTO) {
                    Spacer(Modifier.height(16.dp))
                    FluentDivider()
                    Spacer(Modifier.height(12.dp))
                    LocalProxyBlock(state, row)
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp))
        }
    }
}

@Composable
private fun LocalProxyBlock(state: AppState, row: ProxyRow) {
    val c = Fluent.colors
    val target = state.localProxy.currentTarget
    val runningHere = state.localProxyRunning && target?.host == row.host && target.port == row.port
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lan, null, tint = c.textPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            FText("Локальный прокси", Fluent.type.bodyStrong)
            FText("HTTP-прокси на 127.0.0.1 для браузера и системы", Fluent.type.caption, c.textSecondary)
        }
        ToggleSwitch(
            checked = state.localProxyRunning,
            onCheckedChange = {
                if (state.localProxyRunning && !runningHere) {
                    state.toggleLocalProxy() // остановить старый
                    state.toggleLocalProxy() // запустить через выбранный
                } else {
                    state.toggleLocalProxy()
                }
            },
            showLabel = false,
        )
    }
    if (state.localProxyRunning) {
        Spacer(Modifier.height(10.dp))
        KeyValueRow("Адрес", "127.0.0.1:${state.localProxy.localPort}")
        target?.let { KeyValueRow("Через", "${it.host}:${it.port}") }
        KeyValueRow("Отправлено", state.traffic.formatBytes(state.traffic.upBytes))
        KeyValueRow("Получено", state.traffic.formatBytes(state.traffic.downBytes))
        if (!runningHere) {
            FText("Сейчас работает через другой прокси — переключатель запустит через выбранный.", Fluent.type.caption, c.caution)
        }
    }
}

// endregion

// region Источники

@Composable
private fun SourcesPage(state: AppState) {
    val c = Fluent.colors
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PageHeader(
                "Источники",
                "MTProto, SOCKS5, HTTP и HTTPS · ссылки, host:port:secret, JSON, YAML, CSV, HTML и base64",
            )
            Spacer(Modifier.height(12.dp))
            PageBanners(state)
            if (state.busy || state.scan.total > 0 && state.scan.running) {
                FluentCard(Modifier.fillMaxWidth()) { ScanStrip(state.scan) }
            }

            GroupHeader("Встроенные источники")
            FText(
                "Зеркала proxy-feeds из репозитория KupuProxy обновляются каждые 4 часа. Порядок загрузки: " +
                    "GitHub → jsDelivr → снимок внутри приложения" + (StockFeeds.bundledSnapshotDate()?.let { " (от $it)" } ?: "") + ".",
                Fluent.type.caption,
                c.textSecondary,
                Modifier.padding(bottom = 6.dp),
            )
            StockFeeds.all.forEach { feed ->
                val status = state.feedStatuses.firstOrNull { it.feedId == feed.id }
                SettingsCard(
                    title = feed.title,
                    description = feed.description + when {
                        status == null -> ""
                        status.count > 0 -> " · загружено ${status.count} (${status.origin?.label ?: "—"})"
                        else -> " · ошибка: ${status.error ?: "нет данных"}"
                    },
                    icon = Icons.Outlined.Public,
                ) {
                    ProtocolBadge(feed.protocol)
                    ToggleSwitch(feed.id in state.settings.stockFeeds, { state.toggleStockFeed(feed.id) }, enabled = !state.busy)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.busy) {
                    FluentButton("Остановить", { state.cancelScan() }, icon = Icons.Outlined.Stop)
                } else {
                    FluentButton(
                        "Сканировать встроенные",
                        { state.scanStock() },
                        style = ButtonStyle.ACCENT,
                        icon = Icons.Outlined.PlayArrow,
                        enabled = state.settings.stockFeeds.isNotEmpty(),
                    )
                    FluentButton(
                        "Только снимок, без сети",
                        { state.scanStock(offline = true) },
                        icon = Icons.Outlined.OfflineBolt,
                        enabled = state.settings.stockFeeds.isNotEmpty(),
                    )
                }
            }

            GroupHeader("Свои списки")
            SettingsCard(
                "Файл со списком",
                description = ".txt, .json, .yaml, .csv, .md, .html — любой формат, который понимает парсер",
                icon = Icons.Outlined.FolderOpen,
            ) {
                FluentButton("Выбрать файл…", { pickProxyFile()?.let(state::scanFile) }, enabled = !state.busy)
            }
            var url by remember { mutableStateOf("") }
            val trimmed = url.trim()
            val valid = trimmed.startsWith("https://", true) || trimmed.startsWith("http://", true)
            SettingsCard("Список по ссылке", description = "Прямая ссылка на текстовый список (raw GitHub, Gist, свой сервер)", icon = Icons.Outlined.Link) {
                TextBox(url, { url = it }, "https://…/proxies.txt", Modifier.width(320.dp), onSubmit = { if (valid && !state.busy) state.scanUrl(trimmed) })
                FluentButton("Загрузить", { state.scanUrl(trimmed) }, style = ButtonStyle.ACCENT, enabled = valid && !state.busy)
            }
            SettingsCard(
                "Из буфера обмена",
                description = "Скопируйте ссылки tg://, t.me, socks5:// или строки host:port — KupuProxy разберёт и проверит их",
                icon = Icons.Outlined.ContentPaste,
            ) {
                FluentButton("Вставить и проверить", { state.scanClipboard() }, enabled = !state.busy)
            }
            if (state.sourceLabel != "—") {
                FText("Последний источник: ${state.sourceLabel}", Fluent.type.caption, c.textTertiary, Modifier.padding(top = 8.dp))
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp))
    }
}

// endregion

// region Настройки

@Composable
private fun SettingsPage(state: AppState) {
    val c = Fluent.colors
    val settings = state.settings
    val scroll = rememberScrollState()
    var detectedTelegram by remember { mutableStateOf<String?>(null) }
    var detecting by remember { mutableStateOf(true) }
    LaunchedEffect(settings.telegramPath) {
        detecting = true
        detectedTelegram = withContext(Dispatchers.IO) { TelegramLauncher.detectExecutable()?.path }
        detecting = false
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PageHeader("Настройки")
            Spacer(Modifier.height(8.dp))
            PageBanners(state)

            GroupHeader("Оформление")
            SettingsCard("Тема", description = "Светлая, тёмная или как в системе", icon = Icons.Outlined.DarkMode) {
                ComboBox(
                    ThemeMode.entries,
                    settings.themeMode,
                    {
                        when (it) {
                            ThemeMode.SYSTEM -> "Как в системе"
                            ThemeMode.LIGHT -> "Светлая"
                            ThemeMode.DARK -> "Тёмная"
                        }
                    },
                    { mode -> state.updateSettings { it.copy(themeMode = mode) } },
                )
            }
            SettingsCard(
                "Акцентный цвет",
                description = "Системный берётся из «Параметры → Персонализация → Цвета» Windows",
                icon = Icons.Outlined.ColorLens,
            ) {
                ComboBox(
                    AccentMode.entries,
                    settings.accent,
                    {
                        when (it) {
                            AccentMode.SYSTEM -> "Системный"
                            AccentMode.TEAL -> "Бирюзовый KupuProxy"
                            AccentMode.BLUE -> "Синий Fluent"
                        }
                    },
                    { mode -> state.updateSettings { it.copy(accent = mode) } },
                )
            }

            GroupHeader("Telegram")
            val custom = settings.telegramPath.takeIf { it.isNotBlank() }
            SettingsCard(
                "Telegram Desktop",
                description = when {
                    custom != null -> "Указан вручную: $custom"
                    detecting -> "Поиск…"
                    detectedTelegram != null -> "Найден: $detectedTelegram"
                    else -> "Не найден автоматически. Для portable-версии укажите путь к Telegram.exe — иначе ссылка будет копироваться в буфер."
                },
                icon = Icons.AutoMirrored.Outlined.Send,
            ) {
                if (custom != null) {
                    FluentButton("Сбросить", { state.updateSettings { it.copy(telegramPath = "") } }, style = ButtonStyle.SUBTLE)
                }
                FluentButton("Обзор…", {
                    pickTelegramExecutable()?.let { file -> state.updateSettings { it.copy(telegramPath = file.absolutePath) } }
                })
            }

            GroupHeader("Сканирование")
            SettingsCard("Протоколы", description = "Какие типы прокси проверять и показывать", icon = Icons.Outlined.Tune) {
                ProxyProtocol.entries.forEach { protocol ->
                    ToggleChip(protocolLabel(protocol), protocol in settings.protocolFilter, { state.toggleProtocol(protocol) })
                }
            }
            SliderCard(
                "Потоков проверки: ${settings.scanThreads}",
                "Больше — быстрее, но сильнее нагружает сеть. TCP-префлайт использует втрое больше потоков.",
                Icons.Outlined.Speed,
                settings.scanThreads.toFloat(),
                16f..256f,
            ) { v -> state.updateSettings { it.copy(scanThreads = (v / 8f).roundToInt() * 8) } }
            SliderCard(
                "Таймаут соединения: ${settings.connectTimeoutMs} ms",
                "Меньше — быстрее скан, но медленные прокси будут отброшены",
                Icons.Outlined.Timer,
                settings.connectTimeoutMs.toFloat(),
                500f..3000f,
            ) { v -> state.updateSettings { it.copy(connectTimeoutMs = (v / 100f).roundToInt() * 100) } }
            SliderCard(
                "Замеров на прокси: ${settings.jitterSamples}",
                "1 — быстрее всего; 3 и больше — точнее jitter. Дополнительные замеры делаются только для рабочих прокси.",
                Icons.Outlined.History,
                settings.jitterSamples.toFloat(),
                1f..5f,
                steps = 3,
            ) { v -> state.updateSettings { it.copy(jitterSamples = v.roundToInt().coerceIn(1, 5)) } }
            SliderCard(
                "Максимум прокси за скан: ${settings.maxToCheck}",
                "Ограничивает число проверяемых записей из всех источников",
                Icons.Outlined.Layers,
                settings.maxToCheck.toFloat(),
                100f..10_000f,
            ) { v -> state.updateSettings { it.copy(maxToCheck = (v / 50f).roundToInt() * 50) } }
            SettingsCard("Избранные — первыми", description = "Избранные прокси всегда в начале списка", icon = Icons.Filled.Star) {
                ToggleSwitch(settings.favoritesFirst, { v -> state.updateSettings { it.copy(favoritesFirst = v) } })
            }
            SettingsCard("Сканировать при запуске", description = "Сразу проверять встроенные источники после открытия", icon = Icons.Outlined.PlayArrow) {
                ToggleSwitch(settings.scanOnStart, { v -> state.updateSettings { it.copy(scanOnStart = v) } })
            }
            SettingsCard("Уведомлять о завершении скана", description = "Уведомление в трее, когда окно свёрнуто", icon = Icons.Outlined.Notifications) {
                ToggleSwitch(settings.notifyOnScanEnd, { v -> state.updateSettings { it.copy(notifyOnScanEnd = v) } })
            }

            GroupHeader("Система")
            SettingsCard("Автозапуск при входе в систему", icon = Icons.Outlined.PowerSettingsNew) {
                ToggleSwitch(settings.autostart, { checked ->
                    val applied = AutoStartBridge.set(checked)
                    if (checked && !applied) state.errorMessage = "Не удалось включить автозапуск для текущей системы"
                    state.updateSettings { it.copy(autostart = checked && applied) }
                }, enabled = AutoStart.isSupported())
            }
            SettingsCard("Сворачивать в трей вместо закрытия", icon = Icons.Outlined.Layers) {
                ToggleSwitch(settings.minimizeToTray, { v -> state.updateSettings { it.copy(minimizeToTray = v) } })
            }
            SettingsCard("Запускаться свёрнутым в трей", icon = Icons.Outlined.Layers) {
                ToggleSwitch(settings.startMinimized, { v -> state.updateSettings { it.copy(startMinimized = v) } })
            }

            GroupHeader("Данные")
            SettingsCard("Статистика трафика", description = "Счётчики локального прокси за сессию", icon = Icons.Outlined.SwapVert) {
                FluentButton("Сбросить", { state.resetTraffic() })
            }
            SettingsCard("Избранное", description = "${settings.favorites.size} прокси", icon = Icons.Outlined.StarOutline) {
                FluentButton("Очистить", { state.clearFavorites() }, enabled = settings.favorites.isNotEmpty())
            }
            SettingsCard("Папка данных", description = AppPaths.dataDir().path, icon = Icons.Outlined.Folder) {
                FluentButton("Открыть", { SystemActions.openFolder(AppPaths.dataDir()) })
            }

            GroupHeader("О программе")
            SettingsCard("KupuProxy $DESKTOP_VERSION", description = "Поиск и проверка прокси для Telegram · Android и ПК", icon = Icons.Outlined.Info) {
                FluentButton(
                    if (state.updateChecking) "Проверка…" else "Проверить обновления",
                    { state.checkForUpdates() },
                    enabled = !state.updateChecking,
                )
                FluentButton("GitHub", { SystemActions.browse(UpdateChecker.RELEASES_URL) }, style = ButtonStyle.SUBTLE, icon = Icons.AutoMirrored.Outlined.OpenInNew)
            }
            SettingsCard("Проверять обновления при запуске", icon = Icons.Outlined.Refresh) {
                ToggleSwitch(settings.checkUpdates, { v -> state.updateSettings { it.copy(checkUpdates = v) } })
            }
            SettingsCard(
                "Горячие клавиши",
                description = "Ctrl+R / F5 — скан · Ctrl+F — поиск · Enter — открыть в Telegram · Ctrl+C — скопировать · " +
                    "↑/↓ — выбор · Ctrl+1…4 — разделы · Esc — остановить скан",
                icon = Icons.Outlined.Keyboard,
            )
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp))
    }
}

@Composable
private fun SliderCard(
    title: String,
    description: String,
    icon: ImageVector,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    SettingsCard(title, description = description, icon = icon) {
        FluentSlider(value, onChange, range, Modifier.width(220.dp), steps = steps)
    }
}

// endregion

private fun ScanState.progressOrNull(): Float? = if (total > 0) processed.toFloat() / total else null

private fun median(values: List<Int>): Int {
    if (values.isEmpty()) return 0
    val sorted = values.sorted()
    return sorted[sorted.size / 2]
}

private val timeFormat = DateTimeFormatter.ofPattern("dd.MM HH:mm")
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

/** «14:32» для сегодняшних проверок, «03.05 14:32» — для старых. */
internal fun formatTime(epochMs: Long): String {
    if (epochMs <= 0) return "—"
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    return if (time.toLocalDate() == today) "сегодня в ${clockFormat.format(time)}" else timeFormat.format(time)
}
