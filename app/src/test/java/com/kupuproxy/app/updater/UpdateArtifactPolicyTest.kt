package com.kupuproxy.app.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateArtifactPolicyTest {
    @Test
    fun releaseAssetsMustMatchTagAndFileName() {
        assertTrue(UpdateArtifactPolicy.isApkNameForTag("KupuProxy-v1.4.0.1.apk", "v1.4.0.1"))
        assertFalse(UpdateArtifactPolicy.isApkNameForTag("KupuProxy-v1.4.0.apk", "v1.4.0.1"))
        assertTrue(
            UpdateArtifactPolicy.isReleaseAssetUrlForTag(
                "https://github.com/Kirillka645/KupuProxy/releases/download/v1.4.0.1/KupuProxy-v1.4.0.1.apk",
                "Kirillka645/KupuProxy",
                "v1.4.0.1",
                "KupuProxy-v1.4.0.1.apk"
            )
        )
        assertFalse(
            UpdateArtifactPolicy.isReleaseAssetUrlForTag(
                "https://github.com/Kirillka645/KupuProxy/releases/download/v1.4.0/KupuProxy-v1.4.0.apk",
                "Kirillka645/KupuProxy",
                "v1.4.0.1",
                "KupuProxy-v1.4.0.1.apk"
            )
        )
    }

    @Test
    fun archiveVersionMustMatchReleaseTag() {
        assertTrue(UpdateArtifactPolicy.archiveVersionMatchesTag("1.4.0.1", "v1.4.0.1"))
        assertFalse(UpdateArtifactPolicy.archiveVersionMatchesTag("1.4.0", "v1.4.0.1"))
    }

    @Test
    fun installedSignerMustBePresentInArchiveSigners() {
        assertTrue(UpdateArtifactPolicy.hasMatchingSigner(listOf("ABC123"), listOf("abc123")))
        assertFalse(UpdateArtifactPolicy.hasMatchingSigner(listOf("new-cert"), listOf("debug-cert")))
        assertFalse(UpdateArtifactPolicy.hasMatchingSigner(emptyList(), listOf("debug-cert")))
    }
}
