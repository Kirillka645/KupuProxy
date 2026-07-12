package com.kupuproxy.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kupuproxy.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class UpdateChecker(
    private val context: Context,
    private val client: OkHttpClient
) {

    suspend fun checkForUpdate(currentVersionName: String): GitHubRelease? =
        withContext(Dispatchers.IO) {
            try {
                val latest = fetchLatestRelease()
                if (isNewerVersion(currentVersionName, latest.tagName)) latest else null
            } catch (_: Exception) {
                null
            }
        }

    private fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
        val current = currentVersion.removePrefix("v").split(".")
        val latest = latestVersion.removePrefix("v").split(".")
        val max = maxOf(current.size, latest.size)

        for (i in 0 until max) {
            val c = current.getOrNull(i)?.toIntOrNull() ?: 0
            val l = latest.getOrNull(i)?.toIntOrNull() ?: 0
            if (l != c) return l > c
        }
        return false
    }

    private fun fetchLatestRelease(): GitHubRelease {
        val repo = BuildConfig.GITHUB_REPO
        val url = "https://api.github.com/repos/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "KupuProxy-Android")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("GitHub API error: ${response.code}")
        }

        val json = JSONObject(response.body?.string() ?: throw IOException("Empty response"))
        val assets = json.getJSONArray("assets")
        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk")) {
                apkUrl = asset.getString("browser_download_url")
                break
            }
        }

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
