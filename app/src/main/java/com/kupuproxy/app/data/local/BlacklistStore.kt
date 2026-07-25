package com.kupuproxy.app.data.local

import android.content.Context
import com.kupuproxy.app.data.local.db.AppDatabase
import com.kupuproxy.app.data.local.db.BlacklistEntity

class BlacklistStore(context: Context) {
    private val dao = AppDatabase.get(context).blacklistDao()

    suspend fun isBlocked(dedupeKey: String): Boolean {
        val now = System.currentTimeMillis()
        dao.purgeExpired(now)
        return dao.active(now).any { it.dedupeKey == dedupeKey }
    }

    suspend fun block(dedupeKey: String, hours: Int = 6, reason: String = "fail_streak") {
        val until = System.currentTimeMillis() + hours * 3_600_000L
        dao.upsert(BlacklistEntity(dedupeKey, until, reason))
    }

    suspend fun activeKeys(): Set<String> {
        val now = System.currentTimeMillis()
        dao.purgeExpired(now)
        return dao.active(now).map { it.dedupeKey }.toSet()
    }
}
