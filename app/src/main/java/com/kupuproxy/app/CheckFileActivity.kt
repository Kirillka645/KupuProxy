package com.kupuproxy.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CheckFileActivity : ComponentActivity() {

    private var fileUri: Uri? = null
    private var profileMode = NetworkProfileMode.AUTO
    private var checkJob: Job? = null
    private var state by mutableStateOf(FileCheckState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(MainActivity.EXTRA_FILE_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(MainActivity.EXTRA_FILE_URI)
        }
        profileMode = runCatching {
            NetworkProfileMode.valueOf(
                intent.getStringExtra(MainActivity.EXTRA_PROFILE) ?: NetworkProfileMode.AUTO.name
            )
        }.getOrDefault(NetworkProfileMode.AUTO)

        setContent { KupuProxyTheme { FileCheckScreen() } }
        startChecking()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FileCheckScreen() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Проверка файла", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = ::cancelOrClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(state.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(state.message, color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (state.total > 0) "${state.processed} / ${state.total}" else "Подготовка")
                            Text("${state.working} рабочих", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (state.results.isNotEmpty()) {
                    Button(onClick = ::openResults, modifier = Modifier.fillMaxWidth()) {
                        Text("Открыть результаты (${state.results.size})")
                    }
                }
                OutlinedButton(onClick = ::cancelOrClose, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.finished) "Закрыть" else "Отменить")
                }
            }
        }
    }

    private fun startChecking() {
        val uri = fileUri
        if (uri == null) {
            state = state.copy(title = "Файл не выбран", message = "Вернитесь и выберите текстовый файл.", error = true, finished = true)
            return
        }
        val settings = ProfileSettings.forMode(profileMode, this)
        checkJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                updateState(title = "Чтение файла", message = "Ищу поддерживаемые форматы MTProto…")
                val proxies = ProxyManager.loadProxiesFromFile(contentResolver, uri)
                if (proxies.isEmpty()) {
                    updateState(title = "Прокси не найдены", message = "Файл пуст или имеет неподдерживаемый формат.", error = true, finished = true)
                    return@launch
                }
                val prepared = ProxyManager.prepareForProfile(proxies, settings)
                updateState(title = "Проверка MTProto", message = "Профиль ${settings.label}", total = prepared.size)
                val checked = ProxyManager.checkProxiesPingParallel(
                    prepared,
                    settings,
                    settings.label,
                    onProgress = { processed, total, working ->
                        updateState(
                            processed = processed,
                            total = total,
                            working = working,
                            progress = if (total > 0) processed.toFloat() / total else 0f
                        )
                    }
                )
                if (checked.isNotEmpty()) {
                    val effective = if (settings.mode == NetworkProfileMode.MOBILE) NetworkProfileMode.MOBILE else NetworkProfileMode.WIFI
                    ProxyCache.saveWorking(this@CheckFileActivity, effective, checked)
                }
                updateState(
                    title = if (checked.isEmpty()) "Рабочие прокси не найдены" else "Проверка завершена",
                    message = if (checked.isEmpty()) "Попробуйте другой профиль или список." else "Найдено ${checked.size} доступных прокси.",
                    processed = prepared.size,
                    total = prepared.size,
                    progress = 1f,
                    results = checked,
                    error = checked.isEmpty(),
                    finished = true
                )
            } catch (_: CancellationException) {
                updateState(title = "Проверка остановлена", message = "Операция отменена.", finished = true)
            } catch (error: Exception) {
                updateState(title = "Ошибка", message = error.message ?: "Не удалось проверить файл", error = true, finished = true)
            }
        }
    }

    private fun updateState(
        title: String = state.title,
        message: String = state.message,
        processed: Int = state.processed,
        total: Int = state.total,
        working: Int = state.working,
        progress: Float = state.progress,
        results: List<ProxyWithPing> = state.results,
        error: Boolean = state.error,
        finished: Boolean = state.finished
    ) {
        runOnUiThread {
            state = FileCheckState(title, message, processed, total, working, progress.coerceIn(0f, 1f), results, error, finished)
        }
    }

    private fun openResults() {
        startActivity(Intent(this, ProxyListActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_PROXIES, ArrayList(state.results))
            putExtra(MainActivity.EXTRA_SOURCE_NAME, "Из файла · ${ProfileSettings.forMode(profileMode, this@CheckFileActivity).label}")
        })
    }

    private fun cancelOrClose() {
        if (!state.finished) checkJob?.cancel() else finish()
    }

    private data class FileCheckState(
        val title: String = "Подготовка",
        val message: String = "Открываю файл…",
        val processed: Int = 0,
        val total: Int = 0,
        val working: Int = 0,
        val progress: Float = 0f,
        val results: List<ProxyWithPing> = emptyList(),
        val error: Boolean = false,
        val finished: Boolean = false
    )
}
