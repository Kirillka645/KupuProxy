package com.kupuproxy.desktop

import java.io.File

/**
 * Пути запускаемого файла. Нужны для автозапуска и ярлыков: системе требуется абсолютный путь
 * к исполняемому файлу, а не classpath.
 */
object AppPaths {

    private val os: String get() = System.getProperty("os.name").orEmpty().lowercase()

    /**
     * Команда запуска приложения: исполняемый файл jpackage либо `java -jar <jar>` для
     * uber-jar. Пустой список — запуск из IDE/classpath, автозапуск настроить нельзя.
     */
    fun launchCommand(
        appPath: String? = System.getProperty("jpackage.app.path"),
        codeSource: File? = codeSourceFile(),
        javaHome: String? = System.getProperty("java.home"),
    ): List<String> {
        // jpackage кладёт в jpackage.app.path путь к самому исполняемому файлу лаунчера
        // (KupuProxy.exe, …/bin/KupuProxy, …/Contents/MacOS/KupuProxy). Раньше он
        // трактовался как каталог, кандидат не находился, и автозапуск ломался.
        appPath?.takeIf { it.isNotBlank() }?.let(::File)?.let { file ->
            if (file.isFile) return listOf(file.absolutePath)
            if (file.isDirectory) {
                val candidate = when {
                    os.contains("win") -> File(file, "KupuProxy.exe")
                    os.contains("mac") -> File(file, "Contents/MacOS/KupuProxy")
                    else -> File(file, "bin/KupuProxy")
                }
                if (candidate.isFile) return listOf(candidate.absolutePath)
            }
        }

        if (codeSource != null && codeSource.isFile && codeSource.extension.equals("jar", true)) {
            // Внутри jpackage-образа jar лежит в app/ (Windows, macOS) или lib/app/ (Linux),
            // а лаунчер — на 2–3 уровня выше. jpackage.app.path задаётся не всеми версиями
            // лаунчера, поэтому ищем исполняемый файл по структуре образа.
            generateSequence(codeSource.parentFile) { it.parentFile }
                .drop(1)
                .take(3)
                .flatMap { root ->
                    sequenceOf(File(root, "KupuProxy.exe"), File(root, "bin/KupuProxy"), File(root, "MacOS/KupuProxy"))
                }
                .firstOrNull { it.isFile }
                ?.let { return listOf(it.absolutePath) }
            // Uber-jar: jar сам по себе не исполняемый, нужен java -jar.
            val java = javaHome?.let { home ->
                val exe = if (os.contains("win")) "bin/javaw.exe" else "bin/java"
                File(home, exe).takeIf { it.isFile }?.absolutePath
            } ?: "java"
            return listOf(java, "-jar", codeSource.absolutePath)
        }
        return emptyList()
    }

    /** Совместимость: путь к основному исполняемому файлу, если он есть. */
    fun launcher(): String? = launchCommand().firstOrNull()

    private fun codeSourceFile(): File? = runCatching {
        AppPaths::class.java.protectionDomain?.codeSource?.location?.toURI()?.let(::File)
    }.getOrNull()

    /** Каталог пользовательских данных клиента. */
    fun dataDir(): File = File(System.getProperty("user.home"), ".kupuproxy").apply { mkdirs() }
}
