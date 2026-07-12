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
                updateStatus("Загрузка всех источников…", 0, 0)

                val all = ProxyManager.fetchAllSources { index, total, count ->
                    updateStatus("Источник $index из $total…", index, total, count)
                }

                if (all.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        showError("Не удалось загрузить прокси")
                    }
                    return@launch
                }

                updateStatus("Удаление дубликатов…", 0, all.size)
                val unique = ProxyManager.deduplicateProxies(all)

                updateStatus("Сохранение файла…", 0, unique.size)
                val file = ProxyManager.saveProxiesToFile(unique)

                withContext(Dispatchers.Main) {
                    if (file != null) {
                        showSuccessDialog(unique.size, file.name)
                    } else {
                        showError("Не удалось сохранить файл")
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
                progressBar.progress = (current * 100) / total
                tvCount.text = if (count > 0) {
                    "Получено: $count"
                } else {
                    "Шаг: $current / $total"
                }
            } else {
                progressBar.progress = 0
                tvCount.text = ""
            }
        }
    }

    private fun showSuccessDialog(count: Int, fileName: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Готово")
            .setMessage("Сохранено $count уникальных прокси\n\nФайл: $fileName\nПапка: Downloads")
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
