import java.time.LocalDate
import java.time.ZoneOffset
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
            implementation("org.json:json:20240303")
            // QR-коды прокси (та же версия, что в Android-клиенте).
            implementation("com.google.zxing:core:3.5.3")
        }
        // Снимок встроенных источников (proxy-feeds/) вшивается в ресурсы: клиент работает
        // и без доступа к GitHub. Каталог генерируется задачей bundleStockFeeds.
        desktopMain.resources.srcDir(layout.buildDirectory.dir("generated/stockFeeds"))
        desktopMain.kotlin.srcDir(layout.buildDirectory.dir("generated/version"))
        val desktopTest by getting
        desktopTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
        // Ресурсы (icon.png) подхватываются из src/desktopMain/resources по умолчанию.
    }
}

compose.desktop {
    application {
        mainClass = "com.kupuproxy.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "KupuProxy"
            // jpackage требует MAJOR.MINOR.BUILD, поэтому «маркетинговая» версия 1.4.0.3
            // упаковывается как 1.4.3 (последняя цифра → BUILD). Номер обязан расти от
            // релиза к релизу, иначе установщик .msi не обновит прошлую версию (1.4.0).
            // Полная версия показывается в заголовке окна и в трее (DESKTOP_VERSION).
            packageVersion = providers.gradleProperty("kupuPackageVersion").get()
            // WiX uses an ANSI MSI database by default (LGHT0311 for Cyrillic metadata).
            // Keep installer metadata ASCII; this does not affect localized application UI.
            description = "KupuProxy - Telegram proxy discovery and validation"
            vendor = "KupuProxy"

            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
                menu = true
                // Portable-сборка: установщик на пользователя, без прав администратора.
                perUserInstall = true
                menuGroup = "KupuProxy"
            }
            linux {
                iconFile.set(project.file("src/desktopMain/resources/icon.png"))
                menuGroup = "KupuProxy"
                debMaintainer = "dev@kupuproxy.app"
            }
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
                bundleID = "app.kupuproxy.desktop"
            }
        }
    }
}
// region Встроенные источники

/** Фиды, которые клиент использует как «стоковые». Список совпадает со StockFeeds.all. */
val stockFeedFiles = listOf("mtproto_merged.txt", "kort_socks5.txt", "hookzof_socks5.txt")

val bundleStockFeeds by tasks.registering {
    description = "Копирует proxy-feeds/ в ресурсы десктоп-клиента как офлайн-снимок."
    val sourceDir = rootProject.layout.projectDirectory.dir("proxy-feeds")
    val outputDir = layout.buildDirectory.dir("generated/stockFeeds/stock-feeds")
    inputs.files(stockFeedFiles.map { sourceDir.file(it) })
    outputs.dir(outputDir)
    doLast {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        stockFeedFiles.forEach { name ->
            val src = sourceDir.file(name).asFile
            if (!src.isFile) throw GradleException("Нет файла proxy-feeds/$name — снимок встроенных источников не собрать")
            src.copyTo(out.resolve(name), overwrite = true)
        }
        // Дата снимка — из заголовка «# Updated: YYYY-MM-DD» фида Kort; иначе дата сборки.
        val updated = Regex("""Updated:\s*(\d{4}-\d{2}-\d{2})""")
        val stamp = stockFeedFiles.asSequence()
            .mapNotNull { updated.find(sourceDir.file(it).asFile.readText())?.groupValues?.get(1) }
            .firstOrNull()
            ?: LocalDate.now(ZoneOffset.UTC).toString()
        out.resolve("snapshot.properties").writeText("generated=$stamp\n")
    }
}

tasks.matching { it.name == "desktopProcessResources" }.configureEach { dependsOn(bundleStockFeeds) }

// endregion

// Generated constant: the UI, updater and packaged CLI share gradle.properties.
val generateDesktopVersion by tasks.registering {
    val version = providers.gradleProperty("kupuVersion")
    val output = layout.buildDirectory.file("generated/version/com/kupuproxy/desktop/DesktopVersion.kt")
    inputs.property("version", version)
    outputs.file(output)
    doLast {
        val value = version.get()
        check(Regex("[0-9]+(\\.[0-9]+){3}").matches(value)) { "Expected a four-part kupuVersion" }
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText("package com.kupuproxy.desktop\n\nconst val DESKTOP_VERSION = \"$value\"\n")
        }
    }
}
tasks.matching { it.name == "compileKotlinDesktop" }.configureEach { dependsOn(generateDesktopVersion) }
