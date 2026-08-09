package com.kupuproxy.app.updater

/** Pure validation rules shared by update metadata parsing and APK verification. */
internal object UpdateArtifactPolicy {
    private const val APK_PREFIX = "KupuProxy-"

    fun normalizedVersion(value: String): String =
        value.trim().removePrefix("v").removePrefix("V").trim()

    fun isApkNameForTag(name: String, tagName: String): Boolean {
        val version = normalizedVersion(tagName)
        if (version.isBlank()) return false
        return name.equals("${APK_PREFIX}v$version.apk", ignoreCase = true) ||
            name.equals("$APK_PREFIX$version.apk", ignoreCase = true)
    }

    fun isChecksumNameForTag(name: String, tagName: String): Boolean =
        name.endsWith(".sha256", ignoreCase = true) &&
            isApkNameForTag(name.dropLast(".sha256".length), tagName)

    fun isReleaseDownloadUrlForTag(url: String, repository: String, tagName: String): Boolean {
        val prefix = "https://github.com/$repository/releases/download/"
        if (!url.startsWith(prefix, ignoreCase = true)) return false
        val path = url.substring(prefix.length)
        val urlTag = path.substringBefore('/', missingDelimiterValue = "")
        return urlTag.isNotBlank() &&
            normalizedVersion(urlTag).equals(normalizedVersion(tagName), ignoreCase = true)
    }

    fun isReleaseAssetUrlForTag(
        url: String,
        repository: String,
        tagName: String,
        assetName: String
    ): Boolean =
        isReleaseDownloadUrlForTag(url, repository, tagName) &&
            url.substringAfterLast('/').equals(assetName, ignoreCase = true)

    fun archiveVersionMatchesTag(archiveVersion: String, tagName: String): Boolean =
        normalizedVersion(archiveVersion).equals(normalizedVersion(tagName), ignoreCase = true)

    fun hasMatchingSigner(archiveCertificates: Collection<String>, installedCertificates: Collection<String>): Boolean =
        archiveCertificates.isNotEmpty() &&
            installedCertificates.isNotEmpty() &&
            archiveCertificates.any { archive ->
                installedCertificates.any { installed -> archive.equals(installed, ignoreCase = true) }
            }
}
