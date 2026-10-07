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
        }
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
            // jpackage требует MAJOR.MINOR.BUILD, поэтому «маркетинговая» версия 1.4.0.2
            // упаковывается как 1.4.0. Полная версия доступна внутри приложения
            // (KupuProxyDesktopVersion.properties) и в окне «О программе».
            packageVersion = "1.4.0"
            description = "KupuProxy — поиск и проверка Telegram-прокси"
            vendor = "KupuProxy"

            windows {
                menu = true
                // Portable-сборка: установщик на пользователя, без прав администратора.
                perUserInstall = true
                menuGroup = "KupuProxy"
            }
            linux {
                menuGroup = "KupuProxy"
                debMaintainer = "dev@kupuproxy.app"
            }
            macOS {
                bundleID = "app.kupuproxy.desktop"
            }
        }
    }
}