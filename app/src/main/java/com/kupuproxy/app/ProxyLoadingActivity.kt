package com.kupuproxy.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProxyLoadingActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvFoundBadge: TextView
    private lateinit var tvEmptyHint: TextView
    private lateinit var tvLiveLabel: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var circularProgress: CircularProgressIndicator
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnDone: MaterialButton
    private lateinit var recyclerLive: RecyclerView

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val liveList = mutableListOf<ProxyWithPing>()
    private lateinit var liveAdapter: ProxyAdapter

    private var mode = MainActivity.MODE_MEGA
    private var sourceName = "Прокси"
    private var sourceId = ""
    private var profileMode = NetworkProfileMode.AUTO
    private var scanFinished = false

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
        tvTitle = findViewById(R.id.tvTitle)
        tvFoundBadge = findViewById(R.id.tvFoundBadge)
        tvEmptyHint = findViewById(R.id.tvEmptyHint)
        tvLiveLabel = findViewById(R.id.tvLiveLabel)
        progressBar = findViewById(R.id.loadingProgressBar)
        circularProgress = findViewById(R.id.circularProgress)
        btnCancel = findViewById(R.id.btnCancel)
        btnDone = findViewById(R.id.btnDone)
        recyclerLive = findViewById(R.id.recyclerLive)

        tvTitle.text = sourceName
        liveAdapter = ProxyAdapter(this, liveList)
        recyclerLive.layoutManager = LinearLayoutManager(this)
        recyclerLive.adapter = liveAdapter
        recyclerLive.itemAnimator = null

        btnCancel.setOnClickListener {
            if (scanFinished) {
                finish()
            } else {
                scope.cancel()
                onScanStopped()
            }
        }

        btnDone.setOnClickListener {
            finishWithResults()
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
                    updateStatus("Загрузка $sourceName…", 0, 0)
                    ProxyManager.fetchSourceById(sourceId, this@ProxyLoadingActivity)
                }
                else -> {
                    updateStatus("Мега-скан: источники…", 0, 0)
                    val result = ProxyManager.fetchAllSources(this@ProxyLoadingActivity) { i, total, name, count ->
                        updateStatus("[$i/$total] $name (+$count)", i, total, count)
                    }
                    val note = buildString {
                        if (result.fromCache) append(" · +кэш")
                        if (result.fromSeed) append(" · +seed")
                    }
                    updateStatus("Собрано ${result.proxies.size}$note · старт проверки", 0, 0)
                    result.proxies
                }
            }

            if (raw.isEmpty()) {
                withContext(Dispatchers.Main) {
                    showError("Прокси не найдены. Проверьте сеть или Seed.")
                }
                return@launch
            }

            if (!isActive) return@launch

            val prepared = ProxyManager.prepareForProfile(raw, settings)
            val stopHint = if (settings.stopWhenFound > 0) {
                " · цель ${settings.stopWhenFound}+"
            } else ""
            updateStatus(
                "Проверка ${prepared.size} · ${settings.label}$stopHint",
                0,
                prepared.size
            )

            val working = ProxyManager.checkProxiesPingParallel(
                prepared,
                settings,
                settings.label,
                onProgress = { processed, total, count ->
                    updateStatus(
                        "Скан · ${settings.label} · ✓ $count$stopHint",
                        processed,
                        total,
                        count
                    )
                },
                onFound = { found ->
                    addLiveResult(found)
                }
            )

            // Persist
            if (working.isNotEmpty()) {
                val effective = if (settings.mode == NetworkProfileMode.MOBILE) {
                    NetworkProfileMode.MOBILE
                } else {
                    NetworkProfileMode.WIFI
                }
                ProxyCache.saveWorking(this@ProxyLoadingActivity, effective, working)
                ProxyCache.saveRawList(this@ProxyLoadingActivity, working.map { it.url })
            }

            withContext(Dispatchers.Main) {
                onScanComplete(working)
            }
        }
    }

    private fun addLiveResult(proxy: ProxyWithPing) {
        // insert sorted by ping
        val idx = liveList.indexOfFirst { it.pingMs > proxy.pingMs }.let {
            if (it < 0) liveList.size else it
        }
        // avoid dups
        if (liveList.any { it.url == proxy.url }) return
        liveList.add(idx, proxy)
        liveAdapter.notifyItemInserted(idx)
        recyclerLive.scrollToPosition(0.coerceAtMost(idx))

        tvEmptyHint.visibility = View.GONE
        tvFoundBadge.text = "${liveList.size} ✓"
        tvLiveLabel.text = "Рабочие · ${liveList.size} (можно подключать сейчас)"
        btnDone.isEnabled = true
        btnDone.text = "Готово (${liveList.size})"
    }

    private fun onScanComplete(working: List<ProxyWithPing>) {
        scanFinished = true
        circularProgress.visibility = View.GONE
        progressBar.progress = 100
        btnCancel.text = "Закрыть"
        btnDone.isEnabled = working.isNotEmpty() || liveList.isNotEmpty()

        if (liveList.isEmpty() && working.isEmpty()) {
            showError("Нет доступных прокси")
            btnDone.isEnabled = false
        } else {
            // sync if any missing
            working.forEach { w ->
                if (liveList.none { it.url == w.url }) addLiveResult(w)
            }
            tvStatus.text = "Готово · ${liveList.size} доступных"
            tvCount.text = "Можно подключать или нажать «Готово»"
            tvFoundBadge.text = "${liveList.size} ✓"
            btnDone.text = "Готово (${liveList.size})"
            Toast.makeText(this, "Найдено ${liveList.size} рабочих", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onScanStopped() {
        scanFinished = true
        circularProgress.visibility = View.GONE
        btnCancel.text = "Закрыть"
        if (liveList.isNotEmpty()) {
            tvStatus.text = "Остановлено · ${liveList.size} уже найдены"
            btnDone.isEnabled = true
            btnDone.text = "Готово (${liveList.size})"
            val effective = if (
                ProfileSettings.forMode(profileMode, this).mode == NetworkProfileMode.MOBILE
            ) NetworkProfileMode.MOBILE else NetworkProfileMode.WIFI
            ProxyCache.saveWorking(this, effective, liveList.toList())
        } else {
            tvStatus.text = "Остановлено"
            showError("Скан прерван, рабочих нет")
        }
    }

    private fun finishWithResults() {
        if (liveList.isEmpty()) {
            finish()
            return
        }
        // Stay on this screen — already has the list. Or open full list activity.
        // User already can connect from live list. Just finish.
        finish()
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
                tvCount.text = if (working > 0 || liveList.isNotEmpty()) {
                    "Проверено $current / $total · в списке ${liveList.size.coerceAtLeast(working)}"
                } else {
                    "Проверено $current / $total"
                }
            } else {
                progressBar.progress = 0
                tvCount.text = ""
            }
            if (working > 0) {
                tvFoundBadge.text = "${liveList.size.coerceAtLeast(working)} ✓"
            }
        }
    }

    private fun showError(message: String) {
        tvStatus.text = message
        tvCount.text = "Нажмите «Закрыть»"
        progressBar.visibility = View.GONE
        circularProgress.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
