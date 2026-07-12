package com.kupuproxy.app

import android.content.Intent
import android.net.Uri
import android.os.Build
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

class CheckFileActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvCount: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var btnCancel: MaterialButton

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_file)

        fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(MainActivity.EXTRA_FILE_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(MainActivity.EXTRA_FILE_URI)
        }

        tvStatus = findViewById(R.id.tvCheckStatus)
        tvCount = findViewById(R.id.tvCheckCount)
        progressBar = findViewById(R.id.checkProgressBar)
        btnCancel = findViewById(R.id.btnCancelCheck)
        btnCancel.setOnClickListener {
            scope.cancel()
            finish()
        }

        startChecking()
    }

    private fun startChecking() {
        val uri = fileUri
        if (uri == null) {
            showError("Файл не выбран")
            return
        }

        scope.launch {
            updateStatus("Чтение файла…", 0, 0)
            val proxies = ProxyManager.loadProxiesFromFile(contentResolver, uri)

            if (proxies.isEmpty()) {
                withContext(Dispatchers.Main) {
                    showError("Файл пуст или неверный формат")
                }
                return@launch
            }

            updateStatus("Проверка прокси…", 0, proxies.size)
            val checked = ProxyManager.checkProxiesPingParallel(proxies, 50) { processed, total, working ->
                updateStatus("Проверка прокси…", processed, total, working)
            }

            withContext(Dispatchers.Main) {
                if (checked.isNotEmpty()) {
                    startActivity(
                        Intent(this@CheckFileActivity, ProxyListActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_PROXIES, ArrayList(checked))
                            putExtra(MainActivity.EXTRA_SOURCE_NAME, "Из файла")
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
        runOnUiThread {
            tvStatus.text = message
            tvCount.text = "Нажмите «Отмена» для возврата"
            progressBar.visibility = View.GONE
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
