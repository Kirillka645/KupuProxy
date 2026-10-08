package com.kupuproxy.app.updater

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Манифест автообновления должен соответствовать собственным правилам `UpdateArtifactPolicy`
 * и фактическому файлу релиза.
 *
 * Ошибки здесь неочевидны и дороги: апдейтер не сообщает о них, он просто молча не предлагает
 * обновление. Например, имя файла без префикса `v` расходится с тем, что ждёт пользователь,
 * а размер и SHA-256 должны соответствовать загруженному APK, а не быть выдуманными.
 */
class UpdateManifestTest {

    private val repository = "Kirillka645/KupuProxy"

    private val manifest: JSONObject by lazy {
        // Файл лежит в корне репозитория, тесты запускаются из каталога app/.
        val file = File("../update_manifest.json")
        assertTrue("Не найден update_manifest.json (ожидался ${file.absolutePath})", file.exists())
        JSONObject(file.readText())
    }

    @Test
    fun versionAndTagAgree() {
        val version = manifest.getString("version")
        assertEquals("v$version", manifest.getString("tag_name"))
    }

    @Test
    fun apkNameMatchesTag() {
        assertTrue(
            "Имя APK не соответствует тегу",
            UpdateArtifactPolicy.isApkNameForTag(
                manifest.getString("apk_name"),
                manifest.getString("tag_name"),
            ),
        )
    }

    @Test
    fun checksumNameMatchesTag() {
        val url = manifest.getString("sha256_url")
        val name = url.substringAfterLast('/')
        assertTrue(
            "Имя файла контрольной суммы не соответствует тегу",
            UpdateArtifactPolicy.isChecksumNameForTag(name, manifest.getString("tag_name")),
        )
    }

    @Test
    fun downloadUrlsPointAtTheReleaseTag() {
        val tag = manifest.getString("tag_name")
        // apk_url и sha256_url — ссылки на скачивание, для них есть правило в политике.
        assertTrue(
            "apk_url указывает не на релиз",
            UpdateArtifactPolicy.isReleaseDownloadUrlForTag(manifest.getString("apk_url"), repository, tag),
        )

        // release_url — страница релиза, а не файл: путь другой, поэтому проверяем отдельно.
        val releaseUrl = manifest.getString("release_url")
        assertTrue(
            "release_url должен указывать на страницу релиза: $releaseUrl",
            releaseUrl.equals("https://github.com/$repository/releases/tag/$tag", ignoreCase = true),
        )
    }

    @Test
    fun checksumIsSha256Hex() {
        val sha = manifest.getString("sha256")
        assertEquals(64, sha.length)
        assertTrue("SHA-256 должен быть в hex", sha.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
    }

    @Test
    fun sizeIsPositiveAndPlausible() {
        val size = manifest.getLong("apk_size")
        assertTrue("Размер APK должен быть положительным", size > 0)
        assertTrue("Размер APK неправдоподобен: $size", size < 200L * 1024 * 1024)
    }

    @Test
    fun changelogIsPresent() {
        assertTrue("Описание релиза не должно быть пустым", manifest.getString("changelog").isNotBlank())
    }
}