package com.kupuproxy.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UiInsetsSourceTest {
    private val projectDir =
        File(System.getProperty("user.dir")!!).let { dir ->
            if (dir.name == "app") dir.parentFile else dir
        }
    private val sourceRoot = File(projectDir, "app/src/main/java/com/kupuproxy/app")

    @Test
    fun everyPrimaryScaffoldUsesSafeDrawingInsets() {
        val screens =
            listOf(
                "MainActivity.kt",
                "ProxyLoadingActivity.kt",
                "ProxyListActivity.kt",
                "MergeProxiesActivity.kt",
                "CheckFileActivity.kt",
                "ui/SettingsActivity.kt",
                "ui/AppearanceActivity.kt",
                "ui/UserSourcesActivity.kt",
                "ui/AboutActivity.kt",
            )
        screens.forEach { path ->
            val source = File(sourceRoot, path).readText()
            assertTrue("Missing safe drawing insets in $path", "kupuSafeScreen()" in source)
        }
    }

    @Test
    fun bottomActionsAndEditableDialogsAreImeAware() {
        val scan = File(sourceRoot, "ProxyLoadingActivity.kt").readText()
        val appearance = File(sourceRoot, "ui/AppearanceActivity.kt").readText()
        val sources = File(sourceRoot, "ui/UserSourcesActivity.kt").readText()
        assertTrue("Scan actions can be covered", "kupuBottomActions()" in scan)
        assertTrue("Color dialog can be covered", "kupuImeAware()" in appearance)
        assertTrue("Source dialog can be covered", "kupuImeAware()" in sources)
        assertTrue("Source dialog must scroll on short screens", "verticalScroll(" in sources)
    }
}
