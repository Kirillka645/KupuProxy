package com.kupuproxy.desktop

import java.io.File

/**
 * Пути запускаемого файла. Нужны для автозапуска и ярлыков: системе требуется абсолютный путь
 * к исполняемому файлу, а не classpath.
 */
object AppPaths {

    /**
     * Абсолютный путь к исполняемому файлу приложения.
     *
     * Порядок: jpackage-проп property (задаётся лаунчером) → директория текущего jar → рабочий каталог.
     */
    fun launcher(): String? {
        val appPath = System.getProperty("jpackage.app.path")?.takeIf { it.isNotBlank() }
        if (appPath != null) {
            val dir = File(appPath)
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val candidate = when {
                os.contains("win") -> File(dir, "KupuProxy.exe")
                os.contains("mac") -> File(dir, "Contents/MacOS/KupuProxy")
                else -> File(dir, "bin/KupuProxy")
            }
            if (candidate.exists()) return candidate.absolutePath
        }

        val source = runCatching {
            AppPaths::class.java.protectionDomain?.codeSource?.location?.toURI()?.let(::File)
        }.getOrNull()
        if (source != null && source.exists()) {
            val parent = source.parentFile ?: return null
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val candidates = buildList {
                add(File(parent, if (os.contains("mac")) "Contents/MacOS/KupuProxy" else "bin/KupuProxy"))
                if (os.contains("win")) add(File(parent, "KupuProxy.exe"))
                add(File(parent, "KupuProxy"))
                source.takeIf { it.extension == "jar" }
            }
            candidates.firstOrNull { it.exists() && it.absolutePath != source.absolutePath }
                ?.let { return it.absolutePath }
            // Запуск из исходников: отдаём модуль запуска JVM.
            if (source.extension == "jar") return source.absolutePath
        }
        return null
    }

    /** Каталог пользовательских данных клиента. */
    fun dataDir(): File = File(System.getProperty("user.home"), ".kupuproxy").apply { mkdirs() }
}