package com.kupuproxy.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProxyLoadingActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvCount: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var btnCancel: MaterialButton

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var mode = MainActivity.MODE_MEGA
    private var sourceName = "Прокси"
    private var sourceId = ""
    private var profileMode = NetworkProfileMode.AUTO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_proxy_loading)

        mode = intent.getStringExtra(MainActivity.EXTRA_MODE) ?: MainActivity.MODE_MEGA
        sourceName = intent.getStringExtra(MainActivity.EXTRA_SOURCE_NAME) ?: "Прокси"
        sourceId = intent.getStringExtra(MainActivity.EXTRA_SOURCE_ID).orEmpty()
        profileMode = try {
            NetworkProfileMode.valueOf(
                intent.getStringExtra(MainActivity.EXTRA_PROFILE) ?: NetworkProfileMode.AUTO.name
            )
        } catch (_: Exception) {
            NetworkProfileMode.AUTO
        }

        tvStatus = findViewById(R.id.tvLoadingStatus)
        tvCount = findViewById(R.id.tvProxyCount)
        progressBar = findViewById(R.id.loadingProgressBar)
        btnCancel = findViewById(R.id.btnCancel)
        btnCancel.setOnClickListener {
            scope.cancel()
            finish()
        }

        startLoading()
    }

    private fun startLoading() {
        val settings = ProfileSettings.forMode(profileMode, this)

        scope.launch {
            updateStatus("Профиль: ${settings.label}", 0, 0)

            val raw = when (mode) {
                MainActivity.MODE_SEED -> {
                    updateStatus("Чтение seed из APK…", 0, 0)
                    ProxyCache.loadSeedFromAssets(this@ProxyLoadingActivity)
                }
                MainActivity.MODE_CACHE -> {
                    updateStatus("Чтение локального кэша…", 0, 0)
                    ProxyCache.loadRawList(this@ProxyLoadingActivity)
                }
                MainActivity.MODE_SOURCE -> {
                    updateStatus("Загрузка $sourceName (зеркала)…", 0, 0)
                    ProxyManager.fetchSourceById(sourceId, this@ProxyLoadingActivity)
                }
                else -> {
                    updateStatus("Мега-скан: все источники…", 0, 0)
                    val result = ProxyManager.fetchAllSources(this@ProxyLoadingActivity) { i, total, name, count ->
                        updateStatus("Источник $i/$total: $name (+$count)", i, total, count)
                    }
                    val note = buildString {
                        if (result.fromCache) append(" · +кэш")
                        if (result.fromSeed) append(" · +seed")
                    }
                    updateStatus("Собрано ${result.proxies.size} уникальных$note", 0, 0)
                    result.proxies
                }
            }

            if (raw.isEmpty()) {
                withContext(Dispatchers.Main) {
                    showError("Прокси не найдены. Проверьте сеть или откройте Seed.")
                }
                return@launch
            }

            val prepared = ProxyManager.prepareForProfile(raw, settings)
            updateStatus(
                "MTProto-проверка ${prepared.size} из ${raw.size} (${settings.label})…\nкак в Telegram: handshake + resPQ",
                0,
                prepared.size
            )

            val working = ProxyManager.checkProxiesPingParallel(
                prepared,
                settings,
                settings.label
            ) { processed, total, count ->
                updateStatus(
                    "MTProto · ${settings.label}\nТолько «Доступен» (как Telegram)",
                    processed,
                    total,
                    count
                )
            }

            // Persist for offline / profile history
            if (working.isNotEmpty()) {
                val effective = if (settings.mode == NetworkProfileMode.MOBILE) {
                    NetworkProfileMode.MOBILE
                } else {
                    NetworkProfileMode.WIFI
                }
                ProxyCache.saveWorking(this@ProxyLoadingActivity, effective, working)
                ProxyCache.saveRawList(
                    this@ProxyLoadingActivity,
                    working.map { it.url }
                )
            }

            withContext(Dispatchers.Main) {
                if (working.isNotEmpty()) {
                    startActivity(
                        Intent(this@ProxyLoadingActivity, ProxyListActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_PROXIES, ArrayList(working))
                            putExtra(
                                MainActivity.EXTRA_SOURCE_NAME,
                                "$sourceName · ${settings.label}"
                            )
                        }
                    )
                    finish()
                } else {
                    showError("Нет доступных прокси на ${settings.label}")
                }
            }
        }
    }

    private fun updateStatus(
        message: String,
        current: Int = 0,
        total: Int = 0,
        working: Int = 0
    ) {
        runOnUiThread {
            tvStatus.text = message
            if (total > 0) {
                progressBar.progress = ((current * 100f) / total).toInt().coerceIn(0, 100)
                tvCount.text = if (working > 0) {
                    "Проверено: $current / $total · Работает: $working"
                } else {
                    "Проверено: $current / $total"
                }
            } else {
                progressBar.progress = 0
                tvCount.text = ""
            }
        }
    }

    private fun showError(message: String) {
        tvStatus.text = message
        tvCount.text = "Нажмите «Отмена» для возврата"
        progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
