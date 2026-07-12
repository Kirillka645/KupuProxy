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

    private var sourceUrl = ""
    private var sourceName = ""
    private var urlPrefix = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_proxy_loading)

        sourceUrl = intent.getStringExtra(MainActivity.EXTRA_SOURCE_URL).orEmpty()
        sourceName = intent.getStringExtra(MainActivity.EXTRA_SOURCE_NAME) ?: "Источник"
        urlPrefix = intent.getStringExtra(MainActivity.EXTRA_URL_PREFIX) ?: "tg://proxy?"

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
        scope.launch {
            updateStatus("Загрузка списка прокси…", 0, 0)

            val raw = try {
                ProxyManager.fetchProxies(sourceUrl, urlPrefix)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Ошибка: ${e.message ?: "неизвестно"}")
                }
                return@launch
            }

            if (raw.isEmpty()) {
                withContext(Dispatchers.Main) { showError("Прокси не найдены") }
                return@launch
            }

            val unique = ProxyManager.deduplicateProxies(raw)
            updateStatus("Проверяю доступность…", 0, unique.size)

            val working = ProxyManager.checkProxiesPingParallel(unique, 50) { processed, total, count ->
                updateStatus("Проверяю доступность…", processed, total, count)
            }

            withContext(Dispatchers.Main) {
                if (working.isNotEmpty()) {
                    startActivity(
                        Intent(this@ProxyLoadingActivity, ProxyListActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_PROXIES, ArrayList(working))
                            putExtra(MainActivity.EXTRA_SOURCE_NAME, sourceName)
                        }
                    )
                    finish()
                } else {
                    showError("Нет доступных прокси")
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
                progressBar.progress = (current * 100) / total
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
