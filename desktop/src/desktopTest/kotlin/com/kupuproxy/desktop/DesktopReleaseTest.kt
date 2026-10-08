package com.kupuproxy.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Проверки, которые ловят рассинхрон версий до того, как тег уйдёт в релиз. */
class DesktopReleaseTest {

    @Test
    fun desktopVersionMatchesAndroidVersionName() {
        val app = File("../app/build.gradle.kts").readText()
        val versionName = Regex("""versionName = "([^"]+)"""").find(app)!!.groupValues[1]
        assertEquals(versionName, DESKTOP_VERSION, "DESKTOP_VERSION должен совпадать с versionName (тег релиза)")
    }

    @Test
    fun packageVersionIsDerivedFromMarketingVersion() {
        val script = File("build.gradle.kts").readText()
        val packageVersion = Regex("""packageVersion = "([^"]+)"""").find(script)!!.groupValues[1]
        val (major, minor, _, build) = DESKTOP_VERSION.split('.').map(String::toInt)
        assertEquals("$major.$minor.$build", packageVersion)
    }

    @Test
    fun releaseNotesExistForCurrentVersion() {
        assertTrue(File("../RELEASE_NOTES_$DESKTOP_VERSION.md").isFile, "нет RELEASE_NOTES_$DESKTOP_VERSION.md")
    }

    @Test
    fun launchCommandUsesJpackageExecutable() {
        val exe = File.createTempFile("KupuProxy", ".exe").apply { deleteOnExit() }
        assertEquals(listOf(exe.absolutePath), AppPaths.launchCommand(appPath = exe.absolutePath, codeSource = null))
    }

    @Test
    fun launchCommandRunsUberJarThroughJava() {
        val dir = createTempDir()
        val jar = File(dir, "KupuProxy-Desktop.jar").apply { writeText("x") }
        val command = AppPaths.launchCommand(appPath = null, codeSource = jar, javaHome = System.getProperty("java.home"))
        assertEquals(listOf("-jar", jar.absolutePath), command.drop(1))
        dir.deleteRecursively()
    }

    @Test
    fun launchCommandFindsLinuxImageLauncher() {
        val root = createTempDir()
        val launcher = File(root, "bin/KupuProxy").apply { parentFile.mkdirs(); writeText("#!/bin/sh") }
        val jar = File(root, "lib/app/desktop.jar").apply { parentFile.mkdirs(); writeText("x") }
        assertEquals(listOf(launcher.absolutePath), AppPaths.launchCommand(appPath = null, codeSource = jar))
        root.deleteRecursively()
    }
}
