package com.kupuproxy.desktop

import com.kupuproxy.shared.domain.model.ProxyProtocol
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelegramLauncherTest {

    private val originalRunner = TelegramLauncher.runner

    @AfterTest
    fun restore() {
        TelegramLauncher.runner = originalRunner
    }

    @Test
    fun mtprotoLinksUseTgAndTme() {
        val links = assertNotNull(ProxyLinks.of("tg://proxy?server=1.2.3.4&port=443&secret=dd00112233445566778899aabbccddeeff"))
        assertEquals(ProxyProtocol.MTPROTO, links.protocol)
        assertTrue(links.tg!!.startsWith("tg://proxy?server=1.2.3.4&port=443&secret="))
        assertTrue(links.tme!!.startsWith("https://t.me/proxy?server=1.2.3.4&port=443"))
        assertEquals(links.tme, links.primaryCopy)
    }

    @Test
    fun socksLinksUseTgSocksInsteadOfSocks5Scheme() {
        val links = assertNotNull(ProxyLinks.of("socks5://user:p%40ss@5.6.7.8:1080"))
        assertEquals("tg://socks?server=5.6.7.8&port=1080&user=user&pass=p%40ss", links.tg)
        assertEquals("https://t.me/socks?server=5.6.7.8&port=1080&user=user&pass=p%40ss", links.tme)
        assertEquals("user:p@ss@5.6.7.8:1080", links.address)
    }

    @Test
    fun httpHasNoTelegramLink() {
        val links = assertNotNull(ProxyLinks.of("http://9.9.9.9:8080"))
        assertNull(links.tg)
        assertEquals("9.9.9.9:8080", links.primaryCopy.removePrefix("http://"))
        assertEquals(TelegramOpenResult.UNSUPPORTED, TelegramLauncher.open("http://9.9.9.9:8080"))
    }

    @Test
    fun parsesRegistryHandler() {
        val output = """
            HKEY_CURRENT_USER\Software\Classes\tg\shell\open\command
                (Default)    REG_SZ    "C:\Users\me\AppData\Roaming\Telegram Desktop\Telegram.exe" -workdir "C:\Users\me\AppData\Roaming\Telegram Desktop/" -- "%1"
        """.trimIndent()
        assertEquals(
            "C:\\Users\\me\\AppData\\Roaming\\Telegram Desktop\\Telegram.exe",
            TelegramLauncher.parseRegistryCommand(output),
        )
        assertNull(TelegramLauncher.parseRegistryCommand("ERROR: The system was unable to find the specified registry key"))
    }

    @Test
    fun customPathLaunchesExecutableWithLink() {
        val exe = File.createTempFile("Telegram", ".exe").apply { deleteOnExit() }
        val launched = mutableListOf<List<String>>()
        TelegramLauncher.runner = { launched += it; true }
        val tg = "tg://proxy?server=1.2.3.4&port=443&secret=dd00112233445566778899aabbccddeeff"
        assertEquals(TelegramOpenResult.OPENED_APP, TelegramLauncher.openTg(tg, exe.absolutePath))
        assertEquals(listOf(exe.absolutePath, "--", tg), launched.first())
    }

    @Test
    fun neverFallsBackToBrowserWhenNothingStarts() {
        TelegramLauncher.runner = { false }
        val result = TelegramLauncher.openTg("tg://proxy?server=1.2.3.4&port=443&secret=00", "/nonexistent/Telegram.exe")
        // В CI Telegram не установлен: ссылка не должна уходить в браузер — только NOT_FOUND
        // (или OPENED_HANDLER, если на машине разработчика зарегистрирован tg://).
        assertTrue(result == TelegramOpenResult.NOT_FOUND || result == TelegramOpenResult.OPENED_HANDLER, result.toString())
    }
}
