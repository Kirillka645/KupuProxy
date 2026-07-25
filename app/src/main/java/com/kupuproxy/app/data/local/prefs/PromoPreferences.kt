package com.kupuproxy.app.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.promoDataStore by preferencesDataStore(name = "kupu_promo")

class PromoPreferences(private val context: Context) {

    private val keyDismissed = booleanPreferencesKey("promo_card_dismissed")
    private val keyConnects = intPreferencesKey("successful_connects")
    private val keyLastInvite = longPreferencesKey("last_channel_invite_at")

    val promoCardDismissed: Flow<Boolean> = context.promoDataStore.data.map {
        it[keyDismissed] ?: false
    }

    suspend fun isPromoDismissed(): Boolean = promoCardDismissed.first()

    suspend fun dismissPromoCard() {
        context.promoDataStore.edit { it[keyDismissed] = true }
    }

    suspend fun successfulConnects(): Int =
        context.promoDataStore.data.map { it[keyConnects] ?: 0 }.first()

    suspend fun recordSuccessfulConnect() {
        context.promoDataStore.edit {
            val n = (it[keyConnects] ?: 0) + 1
            it[keyConnects] = n
        }
    }

    suspend fun shouldShowInviteDialog(): Boolean {
        val connects = successfulConnects()
        if (connects < 3) return false
        val last = context.promoDataStore.data.map { it[keyLastInvite] ?: 0L }.first()
        val week = 7L * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - last >= week
    }

    suspend fun markInviteShown() {
        context.promoDataStore.edit {
            it[keyLastInvite] = System.currentTimeMillis()
        }
    }
}
