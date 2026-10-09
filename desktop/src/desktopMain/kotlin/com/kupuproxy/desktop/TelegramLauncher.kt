package com.kupuproxy.desktop

import com.kupuproxy.shared.domain.model.ProxyProtocol
import com.kupuproxy.shared.domain.model.RawProxyEntry
import com.kupuproxy.shared.domain.parser.ProxyParser
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Ссылки для одного прокси: что открывать в Telegram и что копировать в буфер.
 *
 * В 1.4.0.3 SOCKS5 «открывался» ссылкой `socks5://…`, которую Telegram не понимает, а
 * MTProto при незарегистрированном `tg://` уходил на t.me — то есть в браузер.
 */
data class ProxyLinks(
    val protocol: ProxyProtocol,
    /** `tg://proxy?…` / `tg://socks?…`; `null` — Telegram этот тип прокси не принимает. */
    val tg: String?,
    /** `https://t.me/proxy?…` / `https://t.me/socks?…` — удобно отправить другу. */
    val tme: String?,
    /** `host:port` либо `host:port:secret` / `user:pass@host:port`. */
    val address: String,
    /** Исходная ссылка (socks5://, http://, https://, tg://). */
    val original: String,
) {
    /** Что копирует кнопка «Скопировать прокси». */
    val primaryCopy: String get() = tme ?: original

    companion object {
        fun of(url: String): ProxyLinks? = ProxyParser.fromUrl(url)?.let(::of)

        fun of(entry: RawProxyEntry): ProxyLinks {
            val hostPort = "${entry.host}:${entry.port}"
            return when (entry.protocol) {
                ProxyProtocol.MTPROTO -> ProxyLinks(
                    protocol = entry.protocol,
                    tg = ProxyParser.toTgUrl(entry.host, entry.port, entry.secret),
                    tme = ProxyParser.toTmeUrl(entry.host, entry.port, entry.secret),
                    address = "$hostPort:${entry.secret}",
                    original = entry.url,
                )
                ProxyProtocol.SOCKS5 -> {
                    val query = buildString {
                        append("server=").append(entry.host).append("&port=").append(entry.port)
                        entry.username?.takeIf { it.isNotEmpty() }?.let { append("&user=").append(enc(it)) }
                        entry.password?.takeIf { it.isNotEmpty() }?.let { append("&pass=").append(enc(it)) }
                    }
                    ProxyLinks(
                        protocol = entry.protocol,
                        tg = "tg://socks?$query",
                        tme = "https://t.me/socks?$query",
                        address = credentials(entry) + hostPort,
                        original = ProxyParser.toSocksUrl(entry.host, entry.port, entry.username, entry.password),
                    )
                }
                // HTTP/HTTPS-прокси Telegram Desktop ссылкой добавить не умеет.
                ProxyProtocol.HTTP, ProxyProtocol.WEB -> ProxyLinks(
                    protocol = entry.protocol,
                    tg = null,
                    tme = null,
                    address = credentials(entry) + hostPort,
                    original = entry.url,
                )
            }
        }

        private fun credentials(entry: RawProxyEntry): String = when {
            entry.username.isNullOrEmpty() -> ""
            entry.password.isNullOrEmpty() -> "${entry.username}@"
            else -> "${entry.username}:${entry.password}@"
        }

        private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}

/** Итог попытки открыть прокси в Telegram. */
enum class TelegramOpenResult {
    /** Запущен найденный исполняемый файл Telegram с прокси-ссылкой. */
    OPENED_APP,

    /** Ссылка передана системному обработчику `tg://` (Telegram зарегистрирован в системе). */
    OPENED_HANDLER,

    /** Telegram не найден — ссылку нужно скопировать. Браузер не открывается. */
    NOT_FOUND,

    /** Для этого протокола нет tg-ссылки (HTTP/WEB). */
    UNSUPPORTED,
}

/**
 * Открывает прокси прямо в Telegram Desktop, без браузера.
 *
 * Порядок: путь из настроек → обработчик `tg://` из реестра / xdg-mime → запущенный процесс
 * Telegram → типовые пути установки → системный обработчик `tg://`. Если ничего не нашлось,
 * возвращается [TelegramOpenResult.NOT_FOUND] — вызывающий код копирует ссылку в буфер.
 */
object TelegramLauncher {

    private val os = System.getProperty("os.name").orEmpty().lowercase()
    private val isWindows = os.contains("win")
    private val isMac = os.contains("mac")

    /** Переопределяется в тестах, чтобы не запускать реальные процессы. */
    internal var runner: (List<String>) -> Boolean = ::startProcess

    fun open(url: String, customPath: String? = null): TelegramOpenResult {
        val links = ProxyLinks.of(url) ?: return TelegramOpenResult.NOT_FOUND
        val tg = links.tg ?: return TelegramOpenResult.UNSUPPORTED
        return openTg(tg, customPath)
    }

    fun openTg(tg: String, customPath: String? = null): TelegramOpenResult {
        val custom = customPath?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)
        if (custom != null && custom.exists() && runner(launchCommand(custom, tg))) {
            return TelegramOpenResult.OPENED_APP
        }
        detectExecutable()?.let { exe ->
            if (runner(launchCommand(exe, tg))) return TelegramOpenResult.OPENED_APP
        }
        if (hasTgHandler() && openWithHandler(tg)) return TelegramOpenResult.OPENED_HANDLER
        return TelegramOpenResult.NOT_FOUND
    }

    /** Найденный Telegram Desktop (для экрана настроек); `null` — не найден. */
    fun detectExecutable(): File? = runCatching {
        when {
            isWindows -> windowsCandidates()
            isMac -> macCandidates()
            else -> linuxCandidates()
        }.firstOrNull { it.exists() }
    }.getOrNull()

    /** Команда запуска: `Telegram.exe -- <ссылка>` — так же Telegram регистрирует свой обработчик. */
    internal fun launchCommand(exe: File, tg: String): List<String> = when {
        isMac && exe.name.endsWith(".app") -> listOf("open", "-a", exe.absolutePath, tg)
        exe.path == FLATPAK_MARKER -> listOf("flatpak", "run", "org.telegram.desktop", "--", tg)
        else -> listOf(exe.absolutePath, "--", tg)
    }

    // region Поиск

    private fun windowsCandidates(): List<File> {
        val out = ArrayList<File>()
        // 1. Обработчик tg:// из реестра: "C:\…\Telegram.exe" -workdir "…" -- "%1".
        for (root in listOf("HKCU\\Software\\Classes\\tg\\shell\\open\\command", "HKCR\\tg\\shell\\open\\command")) {
            val text = run("reg", "query", root, "/ve") ?: continue
            parseRegistryCommand(text)?.let { out += File(it) }
        }
        // 2. Уже запущенный Telegram (portable-версия обработчик не регистрирует).
        run(
            "powershell", "-NoProfile", "-NonInteractive", "-Command",
            "(Get-Process -Name Telegram,AyuGram,Kotatogram,64Gram -ErrorAction SilentlyContinue | Select-Object -First 1).Path",
        )?.lineSequence()?.map(String::trim)?.firstOrNull { it.endsWith(".exe", true) }?.let { out += File(it) }
        // 3. Типовые пути установки.
        val appData = System.getenv("APPDATA")
        val localAppData = System.getenv("LOCALAPPDATA")
        val programFiles = listOfNotNull(System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"))
        listOfNotNull(appData, localAppData).forEach { base ->
            out += File(base, "Telegram Desktop\\Telegram.exe")
            out += File(base, "Programs\\Telegram Desktop\\Telegram.exe")
        }
        programFiles.forEach { out += File(it, "Telegram Desktop\\Telegram.exe") }
        return out
    }

    private fun macCandidates(): List<File> {
        val home = System.getProperty("user.home")
        return listOf("Telegram.app", "Telegram Desktop.app", "Telegram Lite.app").flatMap { name ->
            listOf(File("/Applications", name), File(home, "Applications/$name"))
        }
    }

    private fun linuxCandidates(): List<File> {
        val home = System.getProperty("user.home")
        val out = ArrayList<File>()
        val path = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).filter(String::isNotBlank)
        for (name in listOf("telegram-desktop", "Telegram", "telegram")) {
            path.forEach { out += File(it, name) }
        }
        out += File(home, ".local/share/TelegramDesktop/Telegram")
        out += File("/opt/telegram/Telegram")
        out += File("/opt/Telegram/Telegram")
        out += File("/snap/bin/telegram-desktop")
        if (File("/var/lib/flatpak/app/org.telegram.desktop").exists() ||
            File(home, ".local/share/flatpak/app/org.telegram.desktop").exists()
        ) {
            out += FlatpakFile
        }
        return out
    }

    /** Есть ли в системе обработчик схемы tg://. */
    private fun hasTgHandler(): Boolean = when {
        isWindows -> run("reg", "query", "HKCR\\tg", "/ve")?.contains("URL", ignoreCase = true) == true ||
            run("reg", "query", "HKCU\\Software\\Classes\\tg", "/ve") != null
        // macOS: `open` сам вернёт ошибку, если обработчика нет.
        isMac -> true
        else -> run("xdg-mime", "query", "default", "x-scheme-handler/tg")?.trim()?.isNotEmpty() == true
    }

    private fun openWithHandler(tg: String): Boolean = when {
        isWindows -> runner(listOf("rundll32", "url.dll,FileProtocolHandler", tg))
        isMac -> runAndWait(listOf("open", tg))
        else -> runner(listOf("xdg-open", tg))
    }

    /** Достаёт путь к exe из значения по умолчанию ключа реестра. */
    internal fun parseRegistryCommand(output: String): String? {
        val line = output.lineSequence().firstOrNull { it.contains("REG_SZ") || it.contains("REG_EXPAND_SZ") } ?: return null
        val value = line.substringAfter("REG_EXPAND_SZ", line.substringAfter("REG_SZ")).trim()
        val exe = if (value.startsWith("\"")) {
            value.removePrefix("\"").substringBefore('"')
        } else {
            value.substringBefore(" -").substringBefore(" \"").trim()
        }
        return exe.takeIf { it.endsWith(".exe", ignoreCase = true) }
            ?.replace(Regex("%([^%]+)%")) { m -> System.getenv(m.groupValues[1]) ?: m.value }
    }

    // endregion

    // region Процессы

    private const val FLATPAK_MARKER = "flatpak:org.telegram.desktop"

    private object FlatpakFile : File(FLATPAK_MARKER) {
        override fun exists(): Boolean = true
    }

    private fun startProcess(command: List<String>): Boolean = runCatching {
        ProcessBuilder(command).redirectErrorStream(true).start()
        true
    }.getOrDefault(false)

    private fun runAndWait(command: List<String>): Boolean = runCatching {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
    }.getOrDefault(false)

    private fun run(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(4, TimeUnit.SECONDS)) {
            process.destroy()
            null
        } else if (process.exitValue() != 0) {
            null
        } else {
            output
        }
    }.getOrNull()

    // endregion
}

/** Системный буфер обмена. */
object Clipboard {
    fun copy(text: String): Boolean = runCatching {
        val selection = java.awt.datatransfer.StringSelection(text)
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        true
    }.getOrDefault(false)

    fun read(): String? = runCatching {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
    }.getOrNull()
}
