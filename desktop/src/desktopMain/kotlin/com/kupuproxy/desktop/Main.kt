package com.kupuproxy.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import kotlinx.coroutines.delay

/** Версия десктоп-клиента. Держим в одном месте — она же показывается в трее и в окне. */
const val DESKTOP_VERSION = "1.4.0.2"

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
        val showWindow = remember { mutableStateOf(!trayOnly && !state.settings.startMinimized) }
        val trayState = rememberTrayState()
        val icon = painterResource("icon.png")

        // Поддержку трея проверяем через AWT: это тот же механизм, на котором строится Tray.
        if (java.awt.SystemTray.isSupported()) {
            Tray(
                state = trayState,
                icon = icon,
                tooltip = "KupuProxy $DESKTOP_VERSION",
                menu = {
                    Item(
                        text = "Открыть KupuProxy",
                        onClick = { showWindow.value = true },
                    )
                    Item(
                        text = if (state.localProxy.isRunning) {
                            "Остановить локальный прокси"
                        } else {
                            "Локальный прокси: выключен"
                        },
                        enabled = state.localProxy.isRunning,
                        onClick = { state.toggleLocalProxy() },
                    )
                    Separator()
                    Item(
                        text = "Выход",
                        onClick = {
                            state.dispose()
                            exitApplication()
                        },
                    )
                },
            )
        }

        if (showWindow.value) {
            Window(
                onCloseRequest = {
                    if (state.settings.minimizeToTray) {
                        showWindow.value = false
                    } else {
                        state.dispose()
                        exitApplication()
                    }
                },
                title = "KupuProxy $DESKTOP_VERSION",
                icon = icon,
                state = androidx.compose.ui.window.WindowState(
                    size = DpSize(1280.dp, 800.dp),
                    position = WindowPosition(Alignment.Center),
                ),
            ) {
                KupuDesktopTheme(darkTheme = state.isDarkTheme()) {
                    com.kupuproxy.desktop.ui.DesktopRoot(state, onOpenTelegram = ::openProxyInTelegram)
                }
            }
        }

        // Счётчик трафика идёт и при свёрнутом окне — иначе статистика замирает.
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                if (state.localProxy.isRunning) state.refreshTraffic()
            }
        }
    }
}

/**
 * Открывает выбранный прокси в Telegram. Для SOCKS5/HTTP/WEB прямая ссылка Telegram
 * неприменима — показываем канал проекта и подсказку про локальный прокси.
 */
private fun openProxyInTelegram(url: String) {
    val protocol = com.kupuproxy.shared.domain.parser.ProxyParser.fromUrl(url)?.protocol
    val target = if (protocol == null || protocol == com.kupuproxy.shared.domain.model.ProxyProtocol.MTPROTO) {
        url
    } else {
        "https://t.me/KupuProxy"
    }
    runCatching {
        java.awt.Desktop.getDesktop().browse(java.net.URI(target))
    }.onFailure {
        launchOnSwing {
            java.awt.Desktop.getDesktop().browse(java.net.URI(target))
        }
    }
}

/** Открывает системный диалог выбора файла со списком прокси. */
fun pickProxyFile(): java.io.File = launchOnSwing {
    val dialog = java.awt.FileDialog(
        null as java.awt.Frame?,
        "Выберите список прокси",
        java.awt.FileDialog.LOAD,
    )
    dialog.setFilenameFilter { _, name ->
        val lower = name.lowercase()
        lower.endsWith(".txt") || lower.endsWith(".json") || lower.endsWith(".yaml") ||
            lower.endsWith(".yml") || lower.endsWith(".csv") || lower.endsWith(".list")
    }
    dialog.isVisible = true
    val dir = dialog.directory ?: "."
    val name = dialog.file ?: return@launchOnSwing java.io.File(dir, "proxies.txt")
    java.io.File(dir, name)
}

/** Диалоги файлов и меню браузера работают только в EDT. */
private fun <T> launchOnSwing(block: () -> T): T {
    var result: T? = null
    var error: Throwable? = null
    java.awt.EventQueue.invokeAndWait {
        runCatching { result = block() }.onFailure { error = it }
    }
    error?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return result as T
}

/** Определяет тёмную тему по настройке, иначе — по цветам Look & Feel системы. */
private fun AppState.isDarkTheme(): Boolean = when (settings.themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> runCatching {
        val background = javax.swing.UIManager.getColor("Panel.background") ?: return@runCatching false
        val luminance = (0.299 * background.red + 0.587 * background.green + 0.114 * background.blue) / 255.0
        luminance < 0.5
    }.getOrDefault(false)
}

/**
 * Иконка приложения рисуется средствами Compose: не нужен бинарный ресурс в сборке
 * и не требуется конвертация AWT/Skia.
 */
