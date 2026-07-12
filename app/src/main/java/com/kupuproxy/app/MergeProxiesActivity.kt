package com.kupuproxy.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MergeProxiesActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvCount: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var btnCancel: MaterialButton

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_merge_proxies)

        tvStatus = findViewById(R.id.tvMergeStatus)
        tvCount = findViewById(R.id.tvMergeCount)
        progressBar = findViewById(R.id.mergeProgressBar)
        btnCancel = findViewById(R.id.btnCancelMerge)
        btnCancel.setOnClickListener {
            scope.cancel()
            finish()
        }

        startMerging()
    }

    private fun startMerging() {
        scope.launch {
            try {
                updateStatus("Загрузка через CDN-зеркала…", 0, 0)

                val result = ProxyManager.fetchAllSources(this@MergeProxiesActivity) { index, total, name, count ->
                    updateStatus("[$index/$total] $name", index, total, count)
                }

                if (result.proxies.isEmpty()) {
                    // permanent fallbacks
                    val seed = ProxyCache.loadSeedFromAssets(this@MergeProxiesActivity)
                    if (seed.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            showError("Не удалось загрузить. Используйте Seed на главном экране.")
                        }
                        return@launch
                    }
                    updateStatus("Сеть недоступна — seed из APK", 0, seed.size)
                    val file = ProxyManager.saveProxiesEverywhere(this@MergeProxiesActivity, seed)
                    withContext(Dispatchers.Main) {
                        if (file != null) {
                            showSuccessDialog(seed.size, file.name, fromSeed = true)
                        } else {
                            showError("Сохранено в app-кэш (${seed.size}), Downloads недоступен")
                        }
                    }
                    return@launch
                }

                updateStatus("Сохранение (${result.proxies.size})…", 0, result.proxies.size)
                val file = ProxyManager.saveProxiesEverywhere(
                    this@MergeProxiesActivity,
                    result.proxies
                )

                withContext(Dispatchers.Main) {
                    if (file != null) {
                        showSuccessDialog(
                            result.proxies.size,
                            file.name,
                            mirrors = result.usedMirrors.size
                        )
                    } else {
                        // still saved to app cache
                        showSuccessDialog(result.proxies.size, "app cache", mirrors = result.usedMirrors.size)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Ошибка: ${e.message}")
                }
            }
        }
    }

    private fun updateStatus(
        message: String,
        current: Int = 0,
        total: Int = 0,
        count: Int = 0
    ) {
        runOnUiThread {
            tvStatus.text = message
            if (total > 0) {
                progressBar.progress = ((current * 100f) / total).toInt().coerceIn(0, 100)
                tvCount.text = if (count > 0) "Получено с источника: $count" else "Шаг $current / $total"
            } else {
                progressBar.progress = 0
                tvCount.text = ""
            }
        }
    }

    private fun showSuccessDialog(
        count: Int,
        fileName: String,
        fromSeed: Boolean = false,
        mirrors: Int = 0
    ) {
        val extra = buildString {
            if (fromSeed) append("\n(из seed APK — сеть не ответила)")
            if (mirrors > 0) append("\nЗеркал OK: $mirrors")
            append("\n\nТакже сохранено в локальный кэш приложения (навсегда, пока не очистите данные).")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Готово")
            .setMessage("Сохранено $count уникальных прокси\n\nФайл: $fileName\nПапка: Downloads$extra")
            .setPositiveButton("Закрыть") { _, _ -> finish() }
            .setCancelable(false)
            .show()
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
