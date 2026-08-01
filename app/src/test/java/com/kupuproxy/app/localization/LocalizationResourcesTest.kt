package com.kupuproxy.app.localization

import com.kupuproxy.app.core.locale.AppLocale
import com.kupuproxy.app.core.locale.AppLocaleManager
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourcesTest {

    private val projectDir: File =
        File(System.getProperty("user.dir")!!).let { dir ->
            if (dir.name == "app") dir.parentFile else dir
        }

    private val resDir = File(projectDir, "app/src/main/res")

    @Test
    fun localeRegistryHasTwentyEntries() {
        Assert.assertEquals(20, AppLocaleManager.supportedLocales.size)
    }

    @Test
    fun allLocalesHaveDirectories() {
        val default = readStrings(File(resDir, "values/strings.xml"))
        Assert.assertTrue("Default catalog must not be empty", default.isNotEmpty())

        AppLocaleManager.supportedLocales.forEach { locale ->
            val dirName = resourceDirName(locale.languageTag)
            val catalogFile = File(resDir, "$dirName/strings.xml")
            Assert.assertTrue("Missing: $catalogFile", catalogFile.isFile())
            val catalog = readStrings(catalogFile)
            Assert.assertTrue(
                "Unknown keys in ${locale.languageTag}: ${catalog.keys - default.keys}",
                default.keys.containsAll(catalog.keys),
            )
        }
    }

    @Test
    fun localeRegistryMatchesConfig() {
        val configTags = parseConfig()
        val expected = AppLocaleManager.supportedLocales.map(AppLocale::languageTag)
        Assert.assertEquals("Registry and config mismatch", expected, configTags)
    }

    private fun parseConfig(): List<String> {
        val config = File(resDir, "xml/locales_config.xml")
        val document =
            DocumentBuilderFactory.newInstance()
                .apply {
                    isNamespaceAware = false
                }
                .newDocumentBuilder()
                .parse(config)
        val nodes = document.getElementsByTagName("locale")
        val result = mutableListOf<String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val name = el.getAttribute("android:name")
            if (name.isNotBlank()) result.add(name)
        }
        return result
    }

    private fun resourceDirName(languageTag: String): String =
        when (languageTag) {
            "en" -> "values"
            "pt-BR" -> "values-pt-rBR"
            "id" -> "values-in"
            "zh-CN" -> "values-zh-rCN"
            "zh-TW" -> "values-zh-rTW"
            else -> "values-$languageTag"
        }

    private fun readStrings(file: File): Map<String, String> {
        val document =
            DocumentBuilderFactory.newInstance()
                .apply {
                    isNamespaceAware = false
                }
                .newDocumentBuilder()
                .parse(file)
        val strings = document.getElementsByTagName("string")
        val result = linkedMapOf<String, String>()
        for (i in 0 until strings.length) {
            val el = strings.item(i) as Element
            val name = el.getAttribute("name")
            if (name.isNotBlank()) result[name] = el.textContent
        }
        return result
    }
}
