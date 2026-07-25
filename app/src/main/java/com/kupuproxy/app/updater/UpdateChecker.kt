package com.kupuproxy.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kupuproxy.app.BuildConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class UpdateChecker(
    private val context: Context,
    private val client: OkHttpClient
) {

    suspend fun checkForUpdate(currentVersionName: String): GitHubRelease? =
        withContext(Dispatchers.IO) {
            try {
                val candidates = fetchRecentReleases()
                val best = candidates
                    .filter { it.apkUrl.isNotBlank() }
                    .maxWithOrNull(compareBy { parseVersion(it.tagName) })
                    ?: return@withContext null
                if (isNewerVersion(currentVersionName, best.tagName)) best else null
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Сравнение с поддержкой `1.3.2-fix`, `v1.3.3`, `1.3.2.1`.
     * Числа сравниваются по сегментам; при равенстве любой суффикс (fix/rc)
     * у latest считается новее «чистой» версии; одинаковые суффиксы — равны.
     */
    fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
        return parseVersion(latestVersion) > parseVersion(currentVersion)
    }

    fun parseVersion(raw: String): VersionParts {
        val s = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .trim()
        // "1.3.2-fix" / "1.3.2_fix" / "1.3.2.fix" / "1.3.2fix"
        val match = Regex(
            """^(\d+(?:\.\d+)*)(?:[-_.+]?(.*))?$"""
        ).matchEntire(s)
        val numPart = match?.groupValues?.getOrNull(1) ?: s
        val suffix = (match?.groupValues?.getOrNull(2) ?: "").lowercase().trim()
        val nums = numPart.split('.')
            .map { segment ->
                // на случай "2fix" без разделителя — берём ведущие цифры
                Regex("""^\d+""").find(segment)?.value?.toIntOrNull() ?: 0
            }
            .ifEmpty { listOf(0) }
        return VersionParts(nums, suffixWeight(suffix), suffix)
    }

    /** Чем больше вес — тем «новее» при равных числах. Чистая версия = 0. */
    private fun suffixWeight(suffix: String): Int {
        if (suffix.isBlank()) return 0
        return when {
            suffix.startsWith("fix") -> 10 + (Regex("""\d+""").find(suffix)?.value?.toIntOrNull() ?: 0)
            suffix.startsWith("hotfix") -> 11
            suffix.startsWith("patch") -> 9
            suffix.startsWith("rc") -> -2
            suffix.startsWith("beta") -> -3
            suffix.startsWith("alpha") -> -4
            else -> 5
        }
    }

    data class VersionParts(
        val numbers: List<Int>,
        val suffixWeight: Int,
        val suffix: String
    ) : Comparable<VersionParts> {
        override fun compareTo(other: VersionParts): Int {
            val max = maxOf(numbers.size, other.numbers.size)
            for (i in 0 until max) {
                val a = numbers.getOrElse(i) { 0 }
                val b = other.numbers.getOrElse(i) { 0 }
                if (a != b) return a.compareTo(b)
            }
            return suffixWeight.compareTo(other.suffixWeight)
        }
    }

    private fun fetchLatestRelease(): GitHubRelease {
        val repo = BuildConfig.GITHUB_REPO
        val url = "https://api.github.com/repos/$repo/releases/latest"
        return parseReleaseJson(getJsonObject(url))
    }

    /** Несколько последних релизов — если latest без APK или tag кривой. */
    private fun fetchRecentReleases(): List<GitHubRelease> {
        val repo = BuildConfig.GITHUB_REPO
        return try {
            val url = "https://api.github.com/repos/$repo/releases?per_page=8"
            val arr = getJsonArray(url)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optBoolean("draft", false)) continue
                    if (o.optBoolean("prerelease", false)) continue
                    add(parseReleaseJson(o))
                }
            }.ifEmpty { listOf(fetchLatestRelease()) }
        } catch (_: Exception) {
            listOf(fetchLatestRelease())
        }
    }

    private fun getJsonObject(url: String): JSONObject {
        val body = httpGet(url)
        return JSONObject(body)
    }

    private fun getJsonArray(url: String): JSONArray {
        val body = httpGet(url)
        return JSONArray(body)
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "KupuProxy-Android/${BuildConfig.VERSION_NAME}")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("GitHub API error: ${response.code}")
        }
        return response.body?.string() ?: throw IOException("Empty response")
    }

    private fun parseReleaseJson(json: JSONObject): GitHubRelease {
        val assets = json.optJSONArray("assets") ?: JSONArray()
        var apkUrl = ""
        // предпочитаем APK с "KupuProxy" в имени, иначе любой .apk
        var fallback = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            if (!name.endsWith(".apk", ignoreCase = true) || url.isBlank()) continue
            if (name.contains("KupuProxy", ignoreCase = true)) {
                apkUrl = url
                break
            }
            if (fallback.isBlank()) fallback = url
        }
        if (apkUrl.isBlank()) apkUrl = fallback

        return GitHubRelease(
            tagName = json.getString("tag_name"),
            changelog = json.optString("body", ""),
            apkUrl = apkUrl,
            htmlUrl = json.getString("html_url")
        )
    }

    fun openReleasePage(releaseUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
