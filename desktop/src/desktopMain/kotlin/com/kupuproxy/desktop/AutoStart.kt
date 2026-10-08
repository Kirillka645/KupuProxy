package com.kupuproxy.desktop

import java.io.File

/**
 * Автозапуск при входе в систему.
 *
 * Используется системный механизм ОС (реестр / .desktop / LaunchAgent), а не копирование exe
 * в автозагрузку: так клиент всегда запускается актуальной версии.
 */
object AutoStart {

    fun isSupported(): Boolean = os() != null

    private fun os(): String? {
        val name = System.getProperty("os.name")?.lowercase() ?: return null
        return when {
            name.contains("win") -> "windows"
            name.contains("mac") || name.contains("darwin") -> "mac"
            name.contains("nix") || name.contains("nux") -> "linux"
            else -> null
        }
    }

    fun isEnabled(): Boolean = when (os()) {
        "windows" -> windowsEntry().exists()
        "mac" -> macPlist().exists()
        "linux" -> linuxDesktopFile().exists()
        else -> false
    }

    fun setEnabled(enabled: Boolean): Boolean = runCatching {
        when (os()) {
            "windows" -> setWindows(enabled)
            "mac" -> setMac(enabled)
            "linux" -> setLinux(enabled)
            else -> false
        }
    }.getOrDefault(false)

    // region Windows

    private fun windowsEntry(): File {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        return File(appData, "Microsoft\\Windows\\Start Menu\\Programs\\Startup\\KupuProxy.lnk")
    }

    private fun setWindows(enabled: Boolean): Boolean {
        val entry = windowsEntry()
        if (!enabled) {
            entry.delete()
            return true
        }
        val command = AppPaths.launchCommand().ifEmpty { return false }
        val target = command.first()
        val arguments = (command.drop(1).map(::quoteWindowsArg) + "--tray").joinToString(" ")
        val workDir = java.io.File(command.last { it != "--tray" }).parentFile?.absolutePath ?: "."
        val ps = "New-Object -ComObject WScript.Shell | ForEach-Object { " +
            "\$s = \$_.CreateShortcut('${entry.absolutePath.psQuote()}'); " +
            "\$s.TargetPath = '${target.psQuote()}'; " +
            "\$s.Arguments = '${arguments.psQuote()}'; " +
            "\$s.WorkingDirectory = '${workDir.psQuote()}'; \$s.Save() }"
        entry.parentFile?.mkdirs()
        return runCatching {
            val process = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command", ps,
            ).redirectErrorStream(true).start()
            // Вывод PowerShell нужно вычитывать, иначе процесс может заблокироваться на полном буфере.
            process.inputStream.readBytes()
            process.waitFor() == 0 && entry.exists()
        }.getOrDefault(false)
    }

    private fun String.psQuote(): String = replace("'", "''")

    private fun quoteWindowsArg(arg: String): String =
        if (arg.any { it == ' ' || it == '\t' }) "\"$arg\"" else arg

    // endregion

    // region macOS

    private fun macPlist(): File {
        val home = System.getProperty("user.home")
        return File(home, "Library/LaunchAgents/app.kupuproxy.desktop.plist")
    }

    private fun setMac(enabled: Boolean): Boolean {
        val plist = macPlist()
        if (!enabled) {
            runCatching {
                ProcessBuilder("launchctl", "unload", plist.absolutePath)
                    .redirectErrorStream(true).start().waitFor()
            }
            plist.delete()
            return true
        }
        val command = AppPaths.launchCommand().ifEmpty { return false }
        val programArguments = (command + "--tray").joinToString("") { "<string>${it.xmlEscape()}</string>" }
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>Label</key><string>app.kupuproxy.desktop</string>
              <key>ProgramArguments</key>
              <array>$programArguments</array>
              <key>RunAtLoad</key><true/>
            </dict>
            </plist>
        """.trimIndent()
        plist.parentFile?.mkdirs()
        plist.writeText(content)
        // RunAtLoad срабатывает при следующем входе в систему; launchctl load сразу запустил
        // бы вторую копию клиента, поэтому файла LaunchAgent достаточно.
        return plist.exists()
    }

    private fun String.xmlEscape(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // endregion

    // region Linux

    private fun linuxDesktopFile(): File {
        val home = System.getProperty("user.home")
        return File(home, ".config/autostart/kupuproxy.desktop")
    }

    private fun setLinux(enabled: Boolean): Boolean {
        val entry = linuxDesktopFile()
        if (!enabled) {
            entry.delete()
            return true
        }
        val command = AppPaths.launchCommand().ifEmpty { return false }
        // Exec из спецификации Desktop Entry: аргументы с пробелами берутся в кавычки.
        val exec = (command + "--tray").joinToString(" ") { arg ->
            if (arg.any { it == ' ' || it == '"' || it == '\\' || it == '$' || it == '`' }) {
                "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`") + "\""
            } else {
                arg
            }
        }
        entry.parentFile?.mkdirs()
        entry.writeText(
            """
            [Desktop Entry]
            Type=Application
            Name=KupuProxy
            Comment=KupuProxy — поиск и проверка Telegram-прокси
            Exec=$exec
            Terminal=false
            X-GNOME-Autostart-enabled=true
            """.trimIndent(),
        )
        return true
    }

    // endregion
}