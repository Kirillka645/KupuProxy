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
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay

/** Версия десктоп-клиента. Держим в одном месте — она же показывается в трее и в окне. */
const val DESKTOP_VERSION = "1.4.0.3"

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
                if (state.localProxy.isRunning || state.localProxyRunning) state.refreshTraffic()
            }
        }
    }
}

/**
 * Открывает выбранный прокси в Telegram. Для SOCKS5/HTTP/WEB прямая ссылка Telegram
 * неприменима — показываем канал проекта и подсказку про локальный прокси.
 */
private fun openProxyInTelegram(url: String) {
    val entry = com.kupuproxy.shared.domain.parser.ProxyParser.fromUrl(url)
    val targets = when {
        entry == null -> listOf(url)
        entry.protocol == com.kupuproxy.shared.domain.model.ProxyProtocol.MTPROTO -> listOf(
            com.kupuproxy.shared.domain.parser.ProxyParser.toTgUrl(entry.host, entry.port, entry.secret),
            // Если tg:// не зарегистрирован (Telegram не установлен), откроется t.me в браузере.
            com.kupuproxy.shared.domain.parser.ProxyParser.toTmeUrl(entry.host, entry.port, entry.secret),
        )
        entry.protocol == com.kupuproxy.shared.domain.model.ProxyProtocol.SOCKS5 -> listOf(
            com.kupuproxy.shared.domain.parser.ProxyParser.toSocksUrl(entry.host, entry.port, entry.username, entry.password),
        )
        else -> listOf("https://t.me/KupuProxy")
    }
    for (target in targets) {
        if (openUri(target)) return
    }
}

/** Открывает URI системным обработчиком. Desktop API есть не везде — на Linux пробуем xdg-open. */
private fun openUri(target: String): Boolean {
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
