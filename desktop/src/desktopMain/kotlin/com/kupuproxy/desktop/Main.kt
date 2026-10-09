package com.kupuproxy.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay

/** Версия десктоп-клиента. Держим в одном месте — она же показывается в трее и в окне. */
const val DESKTOP_VERSION = "1.4.0.4"

/** Мост к системному автозапуску: вынесен, чтобы UI не зависел от платформенного кода. */
object AutoStartBridge {
    fun set(enabled: Boolean): Boolean = AutoStart.setEnabled(enabled)
}

fun main(args: Array<String>) {
    val trayOnly = args.contains("--tray") || System.getenv("KUPU_TRAY") == "1"
    // --tab=<раздел> открывает клиент сразу на нужном экране: используется ярлыками и автотестами.
    val initialTab = args.firstOrNull { it.startsWith("--tab=") }
        ?.substringAfter('=')
        ?.let { key -> DesktopTab.entries.firstOrNull { it.name.equals(key, true) } }
        ?: DesktopTab.DASHBOARD

    application {
        val state = remember { AppState(initialTab) }
        // Без системного трея (часть окружений Linux) свернуть окно «в трей» некуда:
        // окно тогда всегда показывается, а закрытие завершает приложение.
        val traySupported = remember { runCatching { java.awt.SystemTray.isSupported() }.getOrDefault(false) }
        val showWindow = remember {
            mutableStateOf(!traySupported || (!trayOnly && !state.settings.startMinimized))
        }
        val trayState = rememberTrayState()
        val icon = painterResource("icon.png")
        // Состояние окна запоминается: раньше WindowState создавался на каждой рекомпозиции,
        // и размер/позиция окна сбрасывались.
        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 800.dp),
            position = WindowPosition(Alignment.Center),
        )

        fun quit() {
            state.dispose()
            exitApplication()
        }

        if (traySupported) {
            Tray(
                state = trayState,
                icon = icon,
                tooltip = "KupuProxy $DESKTOP_VERSION",
                onAction = { showWindow.value = true },
                menu = {
                    Item(
                        text = "Открыть KupuProxy",
                        onClick = { showWindow.value = true },
                    )
                    Item(
                        text = "Быстрый скан",
                        enabled = !state.busy,
                        onClick = { state.scanStock() },
                    )
                    Item(
                        text = state.rows.firstOrNull()?.let { "Открыть лучший прокси в Telegram (${it.latencyMs} ms)" }
                            ?: "Открыть лучший прокси в Telegram",
                        enabled = state.rows.isNotEmpty(),
                        onClick = { state.rows.firstOrNull()?.let { state.openInTelegram(it.url) } },
                    )
                    Item(
                        text = "Скопировать лучший прокси",
                        enabled = state.rows.isNotEmpty(),
                        onClick = { state.rows.firstOrNull()?.let { state.copyProxy(it.url) } },
                    )
                    Item(
                        text = if (state.localProxyRunning) {
                            "Остановить локальный прокси (127.0.0.1:${state.localProxy.localPort})"
                        } else {
                            "Локальный прокси: выключен"
                        },
                        enabled = state.localProxyRunning,
                        onClick = { state.toggleLocalProxy() },
                    )
                    Separator()
                    Item(
                        text = "Выход",
                        onClick = { quit() },
                    )
                },
            )
        }

        if (showWindow.value) {
            Window(
                onCloseRequest = {
                    if (traySupported && state.settings.minimizeToTray) {
                        showWindow.value = false
                    } else {
                        quit()
                    }
                },
                title = "KupuProxy $DESKTOP_VERSION",
                icon = icon,
                state = windowState,
                onPreviewKeyEvent = { event -> event.type == KeyEventType.KeyDown && handleGlobalShortcut(state, event) },
                onKeyEvent = { event -> event.type == KeyEventType.KeyDown && handleListShortcut(state, event) },
            ) {
                window.minimumSize = java.awt.Dimension(1040, 640)
                KupuDesktopTheme(darkTheme = state.isDarkTheme(), accent = state.settings.accent) {
                    com.kupuproxy.desktop.ui.DesktopRoot(state)
                }
            }
        }

        // Уведомление в трее о завершении скана, если окно свёрнуто.
        LaunchedEffect(state.scanFinishedCount) {
            if (state.scanFinishedCount > 0 && traySupported && !showWindow.value && state.settings.notifyOnScanEnd) {
                val best = state.rows.firstOrNull()
                trayState.sendNotification(
                    Notification(
                        "KupuProxy: скан завершён",
                        if (best == null) "Рабочих прокси не найдено" else "Найдено ${state.rows.size}. Лучший: ${best.label} · ${best.latencyMs} ms",
                        Notification.Type.Info,
                    ),
                )
            }
        }

        LaunchedEffect(Unit) {
            if (state.settings.scanOnStart) state.scanStock()
        }

        // Счётчик трафика идёт и при свёрнутом окне — иначе статистика замирает.
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                if (state.localProxy.isRunning || state.localProxyRunning) state.refreshTraffic()
            }
        }
    }
}

/** Сочетания, работающие из любого места окна (даже из поля поиска). */
private fun handleGlobalShortcut(state: AppState, event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    return when {
        (ctrl && event.key == Key.R) || event.key == Key.F5 -> {
            if (!state.busy) state.scanStock()
            true
        }
        ctrl && event.key == Key.F -> {
            state.requestSearchFocus()
            true
        }
        ctrl && event.key in TAB_KEYS -> {
            state.tab = DesktopTab.entries[TAB_KEYS.indexOf(event.key)]
            true
        }
        event.key == Key.Escape && state.busy -> {
            state.cancelScan()
            true
        }
        else -> false
    }
}

private val TAB_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four)

/** Сочетания для списка — срабатывают, только если их не обработало поле ввода. */
private fun handleListShortcut(state: AppState, event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (state.tab != DesktopTab.PROXIES && state.tab != DesktopTab.DASHBOARD) return false
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    return when {
        ctrl && event.key == Key.C && state.selectedUrl != null -> {
            state.copyProxy()
            true
        }
        (event.key == Key.Enter || event.key == Key.NumPadEnter) && state.selectedUrl != null -> {
            state.openInTelegram()
            true
        }
        event.key == Key.DirectionDown && state.tab == DesktopTab.PROXIES -> {
            state.selectRelative(1)
            true
        }
        event.key == Key.DirectionUp && state.tab == DesktopTab.PROXIES -> {
            state.selectRelative(-1)
            true
        }
        else -> false
    }
}

/** Системные действия: ссылка в браузере (только для GitHub/релизов) и папка в проводнике. */
object SystemActions {
    fun browse(target: String): Boolean {
        val viaDesktop = runCatching {
            val desktop = java.awt.Desktop.getDesktop()
            if (!desktop.isSupported(java.awt.Desktop.Action.BROWSE)) error("browse unsupported")
            desktop.browse(java.net.URI(target))
        }.isSuccess
        if (viaDesktop) return true
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val command = when {
            os.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", target)
            os.contains("mac") -> listOf("open", target)
            else -> listOf("xdg-open", target)
        }
        return runCatching { ProcessBuilder(command).start(); true }.getOrDefault(false)
    }

    fun openFolder(dir: java.io.File): Boolean {
        val viaDesktop = runCatching { java.awt.Desktop.getDesktop().open(dir) }.isSuccess
        if (viaDesktop) return true
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val command = when {
            os.contains("win") -> listOf("explorer", dir.absolutePath)
            os.contains("mac") -> listOf("open", dir.absolutePath)
            else -> listOf("xdg-open", dir.absolutePath)
        }
        return runCatching { ProcessBuilder(command).start(); true }.getOrDefault(false)
    }
}

/** Диалог сохранения списка прокси; `null` — отмена. */
fun pickExportFile(): java.io.File? = launchOnSwing {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Сохранить список прокси", java.awt.FileDialog.SAVE)
    dialog.file = "kupuproxy-${java.time.LocalDate.now()}.txt"
    dialog.isVisible = true
    val dir = dialog.directory ?: return@launchOnSwing null
    val name = dialog.file ?: return@launchOnSwing null
    java.io.File(dir, if (name.contains('.')) name else "$name.txt")
}

/** Выбор исполняемого файла Telegram (portable-версия не регистрирует tg://). */
fun pickTelegramExecutable(): java.io.File? = launchOnSwing {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Укажите Telegram Desktop", java.awt.FileDialog.LOAD)
    dialog.isVisible = true
    val dir = dialog.directory ?: return@launchOnSwing null
    val name = dialog.file ?: return@launchOnSwing null
    java.io.File(dir, name).takeIf { it.exists() }
}

/** Открывает системный диалог выбора файла со списком прокси; `null` — пользователь отменил выбор. */
fun pickProxyFile(): java.io.File? = launchOnSwing {
    val dialog = java.awt.FileDialog(
        null as java.awt.Frame?,
        "Выберите список прокси",
        java.awt.FileDialog.LOAD,
    )
    // Фильтр имён FileDialog не работает на Windows, поэтому расширение проверяется и ниже.
    dialog.setFilenameFilter { _, name -> isProxyListFile(name) }
    dialog.isVisible = true
    val dir = dialog.directory ?: return@launchOnSwing null
    val name = dialog.file ?: return@launchOnSwing null
    java.io.File(dir, name).takeIf { it.isFile }
}

fun isProxyListFile(name: String): Boolean {
    val lower = name.lowercase()
    return listOf(".txt", ".json", ".yaml", ".yml", ".csv", ".list", ".md", ".html", ".htm").any(lower::endsWith)
}

/** Диалоги файлов и меню браузера работают только в EDT. */
private fun <T> launchOnSwing(block: () -> T): T {
    if (java.awt.EventQueue.isDispatchThread()) return block()
    var result: T? = null
    var error: Throwable? = null
    java.awt.EventQueue.invokeAndWait {
        runCatching { result = block() }.onFailure { error = it }
    }
    error?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return result as T
}

/** Определяет тёмную тему по настройке, иначе — по теме операционной системы. */
private fun AppState.isDarkTheme(): Boolean = when (settings.themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> SystemTheme.isDark
}

/**
 * Тема ОС. Раньше бралась яркость фона Swing Look & Feel, но стандартный Metal всегда
 * светлый, поэтому «Системная» тема никогда не становилась тёмной.
 */
internal object SystemTheme {
    val isDark: Boolean by lazy { runCatching { detect() }.getOrDefault(false) }

    private fun detect(): Boolean {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            os.contains("win") -> run(
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme",
            )?.let { Regex("""AppsUseLightTheme\s+REG_DWORD\s+0x0\b""").containsMatchIn(it) } ?: false
            os.contains("mac") -> run("defaults", "read", "-g", "AppleInterfaceStyle")?.contains("Dark", true) ?: false
            else -> {
                val scheme = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme").orEmpty()
                val gtk = run("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme").orEmpty()
                scheme.contains("dark", true) || gtk.contains("dark", true)
            }
        }
    }

    private fun run(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroy()
            null
        } else {
            output
        }
    }.getOrNull()
}
