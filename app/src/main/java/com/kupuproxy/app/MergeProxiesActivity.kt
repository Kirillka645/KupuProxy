package com.kupuproxy.app

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
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

class MergeProxiesActivity : ComponentActivity() {

    private var mergeJob: Job? = null
    private var state by mutableStateOf(MergeState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KupuProxyTheme { MergeScreen() } }
        startMerging()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MergeScreen() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Сбор общего списка", fontWeight = FontWeight.Bold) },
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
                Icon(
                    if (state.finished && !state.error) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(state.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(state.message, color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (state.total > 0) "Источник ${state.current} / ${state.total}" else "Подготовка")
                            Text("${state.count} уникальных", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        if (state.fileName.isNotBlank()) {
                            Text("Downloads/${state.fileName}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (state.finished && state.error) {
                    Button(onClick = {
                        state = MergeState()
                        startMerging()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Повторить") }
                }
                OutlinedButton(onClick = ::cancelOrClose, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.finished) "Закрыть" else "Отменить")
                }
            }
        }
    }

    private fun startMerging() {
        mergeJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                updateState(title = "Загрузка источников", message = "Пробую CDN и резервные зеркала…")
                val result = ProxyManager.fetchAllSources(this@MergeProxiesActivity) { index, total, name, count ->
                    updateState(
                        title = "Сбор списка",
                        message = "$name · +$count",
                        current = index,
                        total = total,
                        progress = if (total > 0) index.toFloat() / total else 0f
                    )
                }
                val proxies = result.proxies.ifEmpty { ProxyCache.loadSeedFromAssets(this@MergeProxiesActivity) }
                if (proxies.isEmpty()) {
                    updateState(
                        title = "Не удалось собрать список",
                        message = "Источники и встроенный seed недоступны.",
                        error = true,
                        finished = true
                    )
                    return@launch
                }
                updateState(title = "Сохранение", message = "Записываю ${proxies.size} прокси в Downloads и кэш…", count = proxies.size)
                val file = ProxyManager.saveProxiesEverywhere(this@MergeProxiesActivity, proxies)
                updateState(
                    title = "Готово",
                    message = if (file != null) {
                        "Список сохранён в Downloads и локальный кэш."
                    } else {
                        "Список сохранён в локальный кэш; Downloads недоступен."
                    },
                    count = proxies.size,
                    fileName = file?.name.orEmpty(),
                    progress = 1f,
                    finished = true
                )
            } catch (_: CancellationException) {
                updateState(title = "Сбор остановлен", message = "Операция отменена.", finished = true)
            } catch (error: Exception) {
                updateState(
                    title = "Ошибка",
                    message = error.message ?: "Не удалось собрать список",
                    error = true,
                    finished = true
                )
            }
        }
    }

    private fun updateState(
        title: String = state.title,
        message: String = state.message,
        current: Int = state.current,
        total: Int = state.total,
        count: Int = state.count,
        fileName: String = state.fileName,
        progress: Float = state.progress,
        error: Boolean = state.error,
        finished: Boolean = state.finished
    ) {
        runOnUiThread {
            state = MergeState(title, message, current, total, count, fileName, progress.coerceIn(0f, 1f), error, finished)
        }
    }

    private fun cancelOrClose() {
        if (!state.finished) mergeJob?.cancel() else finish()
    }

    private data class MergeState(
        val title: String = "Подготовка",
        val message: String = "Собираю источники…",
        val current: Int = 0,
        val total: Int = 0,
        val count: Int = 0,
        val fileName: String = "",
        val progress: Float = 0f,
        val error: Boolean = false,
        val finished: Boolean = false
    )
}
