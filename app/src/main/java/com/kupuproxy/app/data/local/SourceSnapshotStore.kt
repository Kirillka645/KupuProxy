package com.kupuproxy.app.data.local

import android.content.Context
import android.util.AtomicFile
import com.kupuproxy.app.MAX_SCAN_PROXIES
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Last successful result of each source, used when only that source is temporarily unavailable. */
object SourceSnapshotStore {
    private const val DIRECTORY = "source_snapshots"
    private const val MAX_FILE_BYTES = 16L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
    private const val MAX_SNAPSHOT_FILES = 128
    const val MAX_AGE_MS = 72L * 60 * 60 * 1_000

    data class Snapshot(val urls: List<String>, val savedAt: Long)

    @Synchronized
    fun save(
        context: Context,
        sourceId: String,
        urls: List<String>,
        now: Long = System.currentTimeMillis(),
    ) {
        if (sourceId.isBlank() || urls.isEmpty()) return
        val array = JSONArray()
        urls.asSequence().filter(String::isNotBlank).distinct().take(MAX_SCAN_PROXIES).forEach(array::put)
        if (array.length() == 0) return
        val payload = JSONObject().put("savedAt", now).put("urls", array).toString().toByteArray(Charsets.UTF_8)
        if (payload.size > MAX_FILE_BYTES) return

        val atomic = AtomicFile(file(context, sourceId))
        var output: java.io.FileOutputStream? = null
        try {
            output = atomic.startWrite()
            output.write(payload)
            atomic.finishWrite(output)
        } catch (_: Exception) {
            runCatching { output?.let(atomic::failWrite) }
        }
    }

    @Synchronized
    fun load(
        context: Context,
        sourceId: String,
        now: Long = System.currentTimeMillis(),
        maxAgeMs: Long = MAX_AGE_MS,
    ): Snapshot? {
        if (sourceId.isBlank()) return null
        val target = file(context, sourceId)
        val atomic = AtomicFile(target)
        return try {
            val bytes = atomic.readFully()
            if (bytes.size.toLong() !in 1..MAX_FILE_BYTES) return null
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            val savedAt = root.optLong("savedAt", 0L)
            if (savedAt <= 0 || now - savedAt !in 0..maxAgeMs.coerceAtLeast(0L)) {
                atomic.delete()
                return null
            }
            val array = root.optJSONArray("urls") ?: return null
            val urls = buildList {
                for (index in 0 until minOf(array.length(), MAX_SCAN_PROXIES)) {
                    array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                }
            }.distinct()
            urls.takeIf(List<String>::isNotEmpty)?.let { Snapshot(it, savedAt) }
        } catch (_: Exception) {
            null
        }
    }

    /** Removes expired/corrupt snapshots and bounds the store without deleting fresh fallbacks. */
    @Synchronized
    fun cleanup(context: Context, now: Long = System.currentTimeMillis()) {
        cleanupDirectory(directory(context), now)
    }

    internal fun cleanupDirectory(directory: File, now: Long) {
        if (!directory.isDirectory) return
        recoverCompanionFiles(directory)
        data class Candidate(val file: File, val savedAt: Long, val size: Long)

        val valid =
            directory.listFiles { file -> file.isFile && file.extension == "json" }
                .orEmpty()
                .mapNotNull { file ->
                    val size = file.length()
                    val savedAt =
                        runCatching {
                            if (size !in 1..MAX_FILE_BYTES) 0L
                            else JSONObject(file.readText(Charsets.UTF_8)).optLong("savedAt", 0L)
                        }.getOrDefault(0L)
                    if (savedAt <= 0 || now - savedAt !in 0..MAX_AGE_MS) {
                        file.delete()
                        null
                    } else {
                        Candidate(file, savedAt, size)
                    }
                }
                .sortedByDescending(Candidate::savedAt)

        var keptBytes = 0L
        valid.forEachIndexed { index, candidate ->
            val fits = index < MAX_SNAPSHOT_FILES && keptBytes + candidate.size <= MAX_TOTAL_BYTES
            if (fits) keptBytes += candidate.size else candidate.file.delete()
        }
    }

    /** Mirrors AtomicFile recovery rules so sidecars cannot escape storage limits forever. */
    internal fun recoverCompanionFiles(directory: File) {
        directory.listFiles { file -> file.isFile && file.name.endsWith(".json.bak") }
            .orEmpty()
            .forEach { backup ->
                val base = File(directory, backup.name.removeSuffix(".bak"))
                if (base.exists()) base.delete()
                backup.renameTo(base)
            }
        directory.listFiles { file -> file.isFile && file.name.endsWith(".json.new") }
            .orEmpty()
            .forEach(File::delete)
    }

    internal fun keyFor(sourceId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(sourceId.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun file(context: Context, sourceId: String): File =
        File(directory(context), "${keyFor(sourceId)}.json")

    private fun directory(context: Context): File =
        File(context.filesDir, DIRECTORY).also(File::mkdirs)
}
