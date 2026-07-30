package com.kupuproxy.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kupuproxy.app.BuildConfig
import com.kupuproxy.app.core.util.readUtf8Bounded
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: GitHubRelease) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failure(val message: String) : UpdateCheckResult
}

class UpdateChecker(
    private val context: Context?,
    private val client: OkHttpClient,
    private val endpoints: UpdateEndpoints = UpdateEndpoints.production(),
    private val metadataLoader: ((String) -> String)? = null
) {
    private companion object {
        const val MAX_METADATA_BYTES = 512L * 1024
        val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
        val VERSION_REGEX = Regex("""^(\d+(?:\.\d+)*)(?:[-_.+]?(.*))?$""")
        val LEADING_NUMBER_REGEX = Regex("""^\d+""")
        val ANY_NUMBER_REGEX = Regex("""\d+""")
    }

    data class UpdateEndpoints(
        val latestReleaseUrl: String,
        val recentReleasesUrl: String,
        val fallbackManifestUrls: List<String>
    ) {
        companion object {
            fun production(): UpdateEndpoints = UpdateEndpoints(
                latestReleaseUrl = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest",
                recentReleasesUrl = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases?per_page=8",
                fallbackManifestUrls = listOf(
                    "https://raw.githubusercontent.com/${BuildConfig.GITHUB_REPO}/main/update_manifest.json",
                    "https://cdn.jsdelivr.net/gh/${BuildConfig.GITHUB_REPO}@main/update_manifest.json",
                    "https://raw.githack.com/${BuildConfig.GITHUB_REPO}/main/update_manifest.json"
                )
            )
        }
    }

    suspend fun checkForUpdate(currentVersionName: String): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val errors = mutableListOf<String>()
                val candidates = linkedMapOf<String, GitHubRelease>()
                var endpointSucceeded = false

                runCatching { fetchLatestRelease() }
                    .onSuccess {
                        endpointSucceeded = true
                        candidates[it.tagName] = it
                    }
                    .onFailure { errors += it.safeMessage() }

                runCatching { fetchRecentReleases() }
                    .onSuccess {
                        endpointSucceeded = true
                        it.forEach { release -> candidates[release.tagName] = release }
                    }
                    .onFailure { errors += it.safeMessage() }

                endpoints.fallbackManifestUrls.forEach { url ->
                    if (candidates.values.any { isInstallableRelease(it) && isNewerVersion(currentVersionName, it.tagName) }) {
                        return@forEach
                    }
                    runCatching { parseFallbackManifest(getJsonObject(url)) }
                        .onSuccess {
                            endpointSucceeded = true
                            candidates[it.tagName] = it
                        }
                        .onFailure { errors += it.safeMessage() }
                }

                val best = candidates.values
                    .filter(::isInstallableRelease)
                    .maxWithOrNull(compareBy { parseVersion(it.tagName) })

                when {
                    best != null && isNewerVersion(currentVersionName, best.tagName) -> {
                        UpdateCheckResult.UpdateAvailable(best)
                    }
                    endpointSucceeded -> UpdateCheckResult.UpToDate
                    else -> UpdateCheckResult.Failure(
                        errors.firstOrNull() ?: "Update metadata is unavailable"
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                UpdateCheckResult.Failure(error.safeMessage())
            }
        }

    fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean =
        parseVersion(latestVersion) > parseVersion(currentVersion)

    fun parseVersion(raw: String): VersionParts {
        val value = raw.trim().removePrefix("v").removePrefix("V").trim()
        val match = VERSION_REGEX.matchEntire(value)
        val numericPart = match?.groupValues?.getOrNull(1) ?: value
        val suffix = (match?.groupValues?.getOrNull(2) ?: "").lowercase().trim()
        val numbers = numericPart.split('.')
            .map { segment -> LEADING_NUMBER_REGEX.find(segment)?.value?.toIntOrNull() ?: 0 }
            .ifEmpty { listOf(0) }
        return VersionParts(numbers, suffixWeight(suffix), suffix)
    }

    data class VersionParts(
        val numbers: List<Int>,
        val suffixWeight: Int,
        val suffix: String
    ) : Comparable<VersionParts> {
        override fun compareTo(other: VersionParts): Int {
            val max = maxOf(numbers.size, other.numbers.size)
            for (index in 0 until max) {
                val left = numbers.getOrElse(index) { 0 }
                val right = other.numbers.getOrElse(index) { 0 }
                if (left != right) return left.compareTo(right)
            }
            return suffixWeight.compareTo(other.suffixWeight)
        }
    }

    internal fun parseReleaseJson(json: JSONObject): GitHubRelease {
        val assets = json.optJSONArray("assets") ?: JSONArray()
        var apkUrl = ""
        var apkName = ""
        var apkSize = -1L
        var digest: String? = null
        var digestUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            if (url.isBlank() || !isExpectedReleaseAssetUrl(url)) continue
            if (name.endsWith(".apk.sha256", ignoreCase = true)) {
                digestUrl = url
                continue
            }
            if (!name.endsWith(".apk", ignoreCase = true) ||
                !name.startsWith("KupuProxy-", ignoreCase = true) || apkUrl.isNotBlank()
            ) continue
            apkUrl = url
            apkName = name
            apkSize = asset.optLong("size", -1L)
            digest = asset.optString("digest", "")
                .removePrefix("sha256:")
                .takeIf { it.matches(SHA256_REGEX) }
        }

        return GitHubRelease(
            tagName = json.getString("tag_name"),
            changelog = json.optString("body", ""),
            apkUrl = apkUrl,
            htmlUrl = json.getString("html_url"),
            apkName = apkName,
            apkSize = apkSize,
            sha256 = digest,
            sha256Url = digestUrl
        )
    }

    internal fun parseFallbackManifest(json: JSONObject): GitHubRelease {
        val tagName = json.getString("tag_name")
        val apkUrl = json.getString("apk_url")
        val apkName = json.getString("apk_name")
        val releaseUrl = json.getString("release_url")
        require(isExpectedReleaseAssetUrl(apkUrl)) { "Unexpected update APK URL" }
        require(apkName.startsWith("KupuProxy-", true) && apkName.endsWith(".apk", true)) {
            "Unexpected update APK name"
        }
        val digest = json.optString("sha256", "")
            .removePrefix("sha256:")
            .takeIf { it.matches(SHA256_REGEX) }
        val digestUrl = json.optString("sha256_url", "")
            .takeIf { it.isNotBlank() && isExpectedReleaseAssetUrl(it) }
        require(digest != null || digestUrl != null) { "Update checksum is missing" }
        return GitHubRelease(
            tagName = tagName,
            changelog = json.optString("changelog", ""),
            apkUrl = apkUrl,
            htmlUrl = releaseUrl,
            apkName = apkName,
            apkSize = json.optLong("apk_size", -1L),
            sha256 = digest,
            sha256Url = digestUrl
        )
    }

    private fun suffixWeight(suffix: String): Int {
        if (suffix.isBlank()) return 0
        return when {
            suffix.startsWith("fix") -> 10 + (ANY_NUMBER_REGEX.find(suffix)?.value?.toIntOrNull() ?: 0)
            suffix.startsWith("hotfix") -> 11
            suffix.startsWith("patch") -> 9
            suffix.startsWith("rc") -> -2
            suffix.startsWith("beta") -> -3
            suffix.startsWith("alpha") -> -4
            else -> 5
        }
    }

    private fun fetchLatestRelease(): GitHubRelease =
        parseReleaseJson(getJsonObject(endpoints.latestReleaseUrl))

    private fun fetchRecentReleases(): List<GitHubRelease> {
        val array = getJsonArray(endpoints.recentReleasesUrl)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (item.optBoolean("draft", false) || item.optBoolean("prerelease", false)) continue
                add(parseReleaseJson(item))
            }
        }
    }

    private fun isInstallableRelease(release: GitHubRelease): Boolean =
        release.apkUrl.isNotBlank() &&
            release.apkName.endsWith(".apk", ignoreCase = true) &&
            (release.sha256 != null || release.sha256Url != null)

    private fun getJsonObject(url: String): JSONObject = JSONObject(loadMetadata(url))

    private fun getJsonArray(url: String): JSONArray = JSONArray(loadMetadata(url))

    private fun loadMetadata(url: String): String = metadataLoader?.invoke(url) ?: httpGet(url)

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json,application/json")
            .header("User-Agent", "KupuProxy-Android/${BuildConfig.VERSION_NAME}")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val declared = body.contentLength()
            if (declared > MAX_METADATA_BYTES) throw IOException("Response is too large")
            body.source().readUtf8Bounded(MAX_METADATA_BYTES)
        }
    }

    private fun isExpectedReleaseAssetUrl(url: String): Boolean {
        val expected = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/download/"
        return url.startsWith(expected, ignoreCase = true)
    }

    private fun Throwable.safeMessage(): String = when (this) {
        is IOException -> message ?: "Network error"
        is IllegalArgumentException -> message ?: "Invalid update metadata"
        else -> "Update check failed"
    }

    fun openReleasePage(releaseUrl: String) {
        val appContext = requireNotNull(context) { "Context is required to open a release page" }
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
