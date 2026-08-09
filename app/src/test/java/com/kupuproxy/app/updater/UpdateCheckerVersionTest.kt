package com.kupuproxy.app.updater

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerVersionTest {
    private val checker = UpdateChecker(null, OkHttpClient())

    @Test
    fun patchReleaseTransitionsAreDetected() {
        assertTrue(checker.isNewerVersion("1.3.3.3", "v1.3.3.4"))
        assertTrue(checker.isNewerVersion("1.3.3.4", "v1.3.3.5"))
        assertFalse(checker.isNewerVersion("1.3.3.5", "v1.3.3.5"))
        assertFalse(checker.isNewerVersion("1.3.3.5", "v1.3.3.4"))
    }

    @Test
    fun suffixAndFourPartVersionsRemainSupported() {
        assertTrue(checker.isNewerVersion("1.3.2", "v1.3.2-fix"))
        assertTrue(checker.isNewerVersion("1.3.2", "1.3.2.1"))
        assertFalse(checker.isNewerVersion("1.3.3", "v1.3.2-fix"))
    }

    @Test
    fun version140Detects1401FromGitHubRelease() = runTest {
        val checker = checkerFor { url ->
            when (url) {
                LATEST_URL -> release1401Json()
                RECENT_URL -> "[]"
                else -> error("Unexpected URL: $url")
            }
        }

        val result = checker.checkForUpdate("1.4.0")

        assertTrue("Unexpected result: $result", result is UpdateCheckResult.UpdateAvailable)
        val release = (result as UpdateCheckResult.UpdateAvailable).release
        assertEquals("v1.4.0.1", release.tagName)
        assertEquals("KupuProxy-v1.4.0.1.apk", release.apkName)
    }

    @Test
    fun version140Detects1401FromFallbackManifest() = runTest {
        val checker = checkerFor { url ->
            when (url) {
                LATEST_URL -> throw IOException("HTTP 503")
                RECENT_URL -> "[]"
                MANIFEST_URL -> fallback1401Json()
                else -> error("Unexpected URL: $url")
            }
        }

        val result = checker.checkForUpdate("1.4.0")

        assertTrue("Unexpected result: $result", result is UpdateCheckResult.UpdateAvailable)
        assertEquals(
            "v1.4.0.1",
            (result as UpdateCheckResult.UpdateAvailable).release.tagName
        )
    }

    @Test
    fun githubReleaseRejectsApkFromDifferentVersion() = runTest {
        val checker = checkerFor { url ->
            when (url) {
                LATEST_URL -> release1401Json(assetVersion = "1.4.0")
                RECENT_URL -> "[]"
                MANIFEST_URL -> throw IOException("HTTP 404")
                else -> error("Unexpected URL: $url")
            }
        }

        val result = checker.checkForUpdate("1.4.0")

        assertTrue("Unexpected result: $result", result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun githubReleaseRejectsChecksumFromDifferentVersion() = runTest {
        val checker = checkerFor { url ->
            when (url) {
                LATEST_URL -> release1401Json(includeDigest = false, checksumVersion = "1.4.0")
                RECENT_URL -> "[]"
                MANIFEST_URL -> throw IOException("HTTP 404")
                else -> error("Unexpected URL: $url")
            }
        }

        val result = checker.checkForUpdate("1.4.0")

        assertTrue("Unexpected result: $result", result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun fallbackManifestRejectsApkFromDifferentVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            checker.parseFallbackManifest(JSONObject(fallback1401Json(assetVersion = "1.4.0")))
        }
    }

    @Test
    fun parsesGitHubReleaseWithDigestAndChecksumFallback() {
        val release = checker.parseReleaseJson(
            JSONObject(
                """
                {
                  "tag_name": "v1.3.3.5",
                  "html_url": "https://github.com/Kirillka645/KupuProxy/releases/tag/v1.3.3.5",
                  "body": "Updater fix",
                  "assets": [
                    {
                      "name": "KupuProxy-v1.3.3.5.apk",
                      "browser_download_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v1.3.3.5/KupuProxy-v1.3.3.5.apk",
                      "size": 15600000,
                      "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    },
                    {
                      "name": "KupuProxy-v1.3.3.5.apk.sha256",
                      "browser_download_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v1.3.3.5/KupuProxy-v1.3.3.5.apk.sha256",
                      "size": 89
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals("v1.3.3.5", release.tagName)
        assertEquals("KupuProxy-v1.3.3.5.apk", release.apkName)
        assertEquals(15_600_000L, release.apkSize)
        assertEquals("a".repeat(64), release.sha256)
        assertTrue(release.sha256Url!!.endsWith(".apk.sha256"))
    }

    @Test
    fun releaseWithoutGitHubDigestStillUsesChecksumAsset() {
        val release = checker.parseReleaseJson(
            JSONObject(
                """
                {
                  "tag_name": "v1.3.3.5",
                  "html_url": "https://github.com/Kirillka645/KupuProxy/releases/tag/v1.3.3.5",
                  "assets": [
                    {
                      "name": "KupuProxy-v1.3.3.5.apk",
                      "browser_download_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v1.3.3.5/KupuProxy-v1.3.3.5.apk",
                      "size": 15600000
                    },
                    {
                      "name": "KupuProxy-v1.3.3.5.apk.sha256",
                      "browser_download_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v1.3.3.5/KupuProxy-v1.3.3.5.apk.sha256"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertNull(release.sha256)
        assertTrue(release.sha256Url!!.contains("v1.3.3.5"))
    }

    @Test
    fun checksumMustBelongToTheSelectedApkNameVariant() {
        val release = checker.parseReleaseJson(
            JSONObject(
                """
                {
                  "tag_name": "v1.4.0.1",
                  "html_url": "https://github.com/Kirillka645/KupuProxy/releases/tag/v1.4.0.1",
                  "assets": [
                    {
                      "name": "KupuProxy-v1.4.0.1.apk",
                      "browser_download_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v1.4.0.1/KupuProxy-v1.4.0.1.apk",
                      "size": 15600000
                    },
                    {
                      "name": "KupuProxy-1.4.0.1.apk.sha256",
                      "browser_download_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v1.4.0.1/KupuProxy-1.4.0.1.apk.sha256"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertNull(release.sha256Url)
    }

    @Test
    fun latestEndpointFailureFallsBackToRecentReleaseList() = runTest {
        val requests = mutableListOf<String>()
        val checker = checkerFor { url ->
            requests += url
            when (url) {
                LATEST_URL -> throw IOException("HTTP 503")
                RECENT_URL -> "[${releaseJson(includeApk = true, includeDigest = true)}]"
                else -> error("Unexpected URL: $url")
            }
        }
        val result = checker.checkForUpdate("1.3.3.4")

        assertTrue("Unexpected result: $result", result is UpdateCheckResult.UpdateAvailable)
        assertEquals("v1.3.3.5", (result as UpdateCheckResult.UpdateAvailable).release.tagName)
        assertEquals(listOf(LATEST_URL, RECENT_URL), requests)
    }

    @Test
    fun missingApkFallsBackToManifest() = runTest {
        val checker = checkerFor { url ->
            when (url) {
                LATEST_URL -> releaseJson(includeApk = false, includeDigest = false)
                RECENT_URL -> "[]"
                MANIFEST_URL -> fallbackManifestJson()
                else -> error("Unexpected URL: $url")
            }
        }
        val result = checker.checkForUpdate("1.3.3.4")

        assertTrue("Unexpected result: $result", result is UpdateCheckResult.UpdateAvailable)
        assertEquals("v1.3.3.5", (result as UpdateCheckResult.UpdateAvailable).release.tagName)
    }

    @Test
    fun releaseWithChecksumAssetSkipsManifestFallback() = runTest {
        val requests = mutableListOf<String>()
        val releaseWithChecksum = """
            {
              "tag_name": "v1.3.3.5",
              "html_url": "https://github.com/Kirillka645/KupuProxy/releases/tag/v1.3.3.5",
              "assets": [
                {
                  "name": "KupuProxy-v1.3.3.5.apk",
                  "browser_download_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v1.3.3.5/KupuProxy-v1.3.3.5.apk",
                  "size": 15600000
                },
                {
                  "name": "KupuProxy-v1.3.3.5.apk.sha256",
                  "browser_download_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v1.3.3.5/KupuProxy-v1.3.3.5.apk.sha256"
                }
              ]
            }
        """.trimIndent()
        val checker = checkerFor { url ->
            requests += url
            when (url) {
                LATEST_URL -> releaseWithChecksum
                RECENT_URL -> "[]"
                else -> error("Manifest fallback should not be requested")
            }
        }
        val result = checker.checkForUpdate("1.3.3.4")

        assertTrue("Unexpected result: $result", result is UpdateCheckResult.UpdateAvailable)
        assertEquals(listOf(LATEST_URL, RECENT_URL), requests)
    }

    @Test
    fun releaseWithoutDigestOrChecksumIsRejected() = runTest {
        val checker = checkerFor { url ->
            when (url) {
                LATEST_URL -> releaseJson(includeApk = true, includeDigest = false)
                RECENT_URL -> "[]"
                MANIFEST_URL -> throw IOException("HTTP 404")
                else -> error("Unexpected URL: $url")
            }
        }
        val result = checker.checkForUpdate("1.3.3.4")

        assertTrue("Unexpected result: $result", result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun totalMetadataFailureReturnsVisibleFailure() = runTest {
        val checker = checkerFor { throw IOException("HTTP 503") }
        val result = checker.checkForUpdate("1.3.3.4")

        assertTrue(result is UpdateCheckResult.Failure)
        assertTrue((result as UpdateCheckResult.Failure).message.contains("HTTP 503"))
    }

    @Test
    fun malformedOfficialApkUrlDoesNotBecomeUpdate() = runTest {
        val malformed = releaseJson(includeApk = true, includeDigest = true)
            .replace(
                "https://github.com/Kirillka645/KupuProxy/releases/download/",
                "https://example.com/releases/download/"
            )
        val checker = checkerFor { url ->
            when (url) {
                LATEST_URL -> malformed
                RECENT_URL -> "[]"
                MANIFEST_URL -> throw IOException("HTTP 404")
                else -> error("Unexpected URL: $url")
            }
        }
        val result = checker.checkForUpdate("1.3.3.4")

        assertTrue("Unexpected result: $result", result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun fallbackManifestRequiresOfficialAssetAndChecksum() {
        val release = checker.parseFallbackManifest(
            JSONObject(
                """
                {
                  "tag_name": "v1.3.3.5",
                  "release_url": "https://github.com/Kirillka645/KupuProxy/releases/tag/v1.3.3.5",
                  "apk_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v1.3.3.5/KupuProxy-v1.3.3.5.apk",
                  "apk_name": "KupuProxy-v1.3.3.5.apk",
                  "apk_size": 15600000,
                  "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                }
                """.trimIndent()
            )
        )

        assertEquals("b".repeat(64), release.sha256)
        assertEquals("v1.3.3.5", release.tagName)
    }

    private fun checkerFor(loader: (String) -> String): UpdateChecker = UpdateChecker(
        context = null,
        client = OkHttpClient(),
        endpoints = UpdateChecker.UpdateEndpoints(LATEST_URL, RECENT_URL, listOf(MANIFEST_URL)),
        metadataLoader = loader
    )

    private fun releaseJson(includeApk: Boolean, includeDigest: Boolean): String {
        val assets = if (!includeApk) {
            "[]"
        } else {
            val digest = if (includeDigest) {
                ", \"digest\": \"sha256:${"a".repeat(64)}\""
            } else {
                ""
            }
            """
            [
              {
                "name": "KupuProxy-v1.3.3.5.apk",
                "browser_download_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v1.3.3.5/KupuProxy-v1.3.3.5.apk",
                "size": 15600000$digest
              }
            ]
            """.trimIndent()
        }
        return """
            {
              "tag_name": "v1.3.3.5",
              "html_url": "https://github.com/Kirillka645/KupuProxy/releases/tag/v1.3.3.5",
              "body": "Updater fix",
              "assets": $assets
            }
        """.trimIndent()
    }

    private fun fallbackManifestJson(): String = """
        {
          "tag_name": "v1.3.3.5",
          "release_url": "https://github.com/Kirillka645/KupuProxy/releases/tag/v1.3.3.5",
          "apk_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v1.3.3.5/KupuProxy-v1.3.3.5.apk",
          "apk_name": "KupuProxy-v1.3.3.5.apk",
          "apk_size": 15600000,
          "sha256": "${"b".repeat(64)}"
        }
    """.trimIndent()

    private fun release1401Json(
        assetVersion: String = "1.4.0.1",
        includeDigest: Boolean = true,
        checksumVersion: String? = null
    ): String {
        val digest = if (includeDigest) {
            ", \"digest\": \"sha256:${"c".repeat(64)}\""
        } else {
            ""
        }
        val checksumAsset = checksumVersion?.let { version ->
            """
            ,{
              "name": "KupuProxy-v$version.apk.sha256",
              "browser_download_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v$version/KupuProxy-v$version.apk.sha256"
            }
            """.trimIndent()
        }.orEmpty()
        return """
            {
              "tag_name": "v1.4.0.1",
              "html_url": "https://github.com/Kirillka645/KupuProxy/releases/tag/v1.4.0.1",
              "body": "1.4.0.1",
              "assets": [
                {
                  "name": "KupuProxy-v$assetVersion.apk",
                  "browser_download_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v$assetVersion/KupuProxy-v$assetVersion.apk",
                  "size": 25196566$digest
                }
                $checksumAsset
              ]
            }
        """.trimIndent()
    }

    private fun fallback1401Json(assetVersion: String = "1.4.0.1"): String = """
        {
          "tag_name": "v1.4.0.1",
          "release_url": "https://github.com/Kirillka645/KupuProxy/releases/tag/v1.4.0.1",
          "apk_url": "https://github.com/Kirillka645/KupuProxy/releases/download/v$assetVersion/KupuProxy-v$assetVersion.apk",
          "apk_name": "KupuProxy-v$assetVersion.apk",
          "apk_size": 25196566,
          "sha256": "${"d".repeat(64)}"
        }
    """.trimIndent()

    private companion object {
        const val LATEST_URL = "https://updates.test/latest"
        const val RECENT_URL = "https://updates.test/recent"
        const val MANIFEST_URL = "https://updates.test/manifest"
    }
}
