package com.kupuproxy.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Radar
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kupuproxy.app.ui.components.ProxyResultCard
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProxyLoadingActivity : ComponentActivity() {

    private var mode = MainActivity.MODE_MEGA
    private var sourceName = "Прокси"
    private var sourceId = ""
    private var profileMode = NetworkProfileMode.AUTO
    private var scanJob: Job? = null

    private var uiState by mutableStateOf(ScanUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(MainActivity.EXTRA_MODE) ?: MainActivity.MODE_MEGA
        sourceName = intent.getStringExtra(MainActivity.EXTRA_SOURCE_NAME) ?: "Прокси"
        sourceId = intent.getStringExtra(MainActivity.EXTRA_SOURCE_ID).orEmpty()
        profileMode = runCatching {
            NetworkProfileMode.valueOf(
                intent.getStringExtra(MainActivity.EXTRA_PROFILE) ?: NetworkProfileMode.AUTO.name
            )
        }.getOrDefault(NetworkProfileMode.AUTO)

        setContent { KupuProxyTheme { ScanScreen() } }
        startLoading()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ScanScreen() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(sourceName, fontWeight = FontWeight.Bold)
                            Text(
                                uiState.phase,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = ::cancelOrClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                )
            },
            bottomBar = {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = ::cancelOrClose, modifier = Modifier.weight(1f)) {
                        Text(if (uiState.finished) "Закрыть" else "Остановить")
                    }
                    Button(
                        onClick = ::openFullResults,
                        enabled = uiState.found.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Результаты (${uiState.found.size})")
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { ScanSummaryCard(uiState) }
                if (uiState.error != null) {
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Не удалось завершить скан", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(uiState.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (uiState.found.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Radar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(if (uiState.finished) "Рабочие прокси не найдены" else "Результаты появятся здесь")
                            Text(
                                "Можно оставить экран открытым и подключаться к найденным адресам сразу.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            "Рабочие прокси",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(uiState.found, key = ProxyWithPing::url) { proxy ->
                        ProxyResultCard(
                            proxy = proxy,
                            favorite = ProxyCache.isFavorite(this@ProxyLoadingActivity, proxy.url),
                            onConnect = { connect(proxy.url) },
                            onToggleFavorite = {
                                ProxyCache.toggleFavorite(this@ProxyLoadingActivity, proxy.url)
                                uiState = uiState.copy(found = uiState.found.toList())
                            }
                        )
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    @Composable
    private fun ScanSummaryCard(state: ScanUiState) {
        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (state.finished && state.found.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Radar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.padding(5.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.message, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (state.total > 0) "Проверено ${state.processed} / ${state.total}" else "Подготовка источников",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("${state.found.size} ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    private fun startLoading() {
        val settings = ProfileSettings.forMode(profileMode, this)
        scanJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                updateState(message = "Профиль: ${settings.label}", phase = "Сбор адресов")
                val raw = when (mode) {
                    MainActivity.MODE_SEED -> {
                        updateState(message = "Чтение встроенного seed…")
                        ProxyCache.loadSeedFromAssets(this@ProxyLoadingActivity)
                    }
                    MainActivity.MODE_CACHE -> {
                        updateState(message = "Чтение локального кэша…")
                        ProxyCache.loadRawList(this@ProxyLoadingActivity)
                    }
                    MainActivity.MODE_SOURCE -> {
                        updateState(message = "Загрузка $sourceName…")
                        ProxyManager.fetchSourceById(sourceId, this@ProxyLoadingActivity)
                    }
                    else -> {
                        val result = ProxyManager.fetchAllSources(this@ProxyLoadingActivity) { index, total, name, count ->
                            updateState(
                                message = "[$index/$total] $name · +$count",
                                processed = index,
                                total = total,
                                progress = if (total > 0) index.toFloat() / total else 0f
                            )
                        }
                        result.proxies
                    }
                }
                if (raw.isEmpty()) {
                    updateState(error = "Прокси не найдены. Проверьте сеть или используйте Seed.", finished = true)
                    return@launch
                }
                if (!isActive) return@launch

                val prepared = ProxyManager.prepareForProfile(raw, settings)
                updateState(
                    message = "Проверка ${prepared.size} адресов · ${settings.label}",
                    phase = "MTProto handshake",
                    processed = 0,
                    total = prepared.size,
                    progress = 0f
                )
                val working = ProxyManager.checkProxiesPingParallel(
                    prepared,
                    settings,
                    settings.label,
                    onProgress = { processed, total, workingCount ->
                        updateState(
                            message = "Проверка · найдено $workingCount",
                            processed = processed,
                            total = total,
                            progress = if (total > 0) processed.toFloat() / total else 0f
                        )
                    },
                    onFound = ::addLiveResult
                )
                if (working.isNotEmpty()) {
                    val effective = if (settings.mode == NetworkProfileMode.MOBILE) NetworkProfileMode.MOBILE else NetworkProfileMode.WIFI
                    ProxyCache.saveWorking(this@ProxyLoadingActivity, effective, working)
                    ProxyCache.saveRawList(this@ProxyLoadingActivity, working.map { it.url })
                }
                working.forEach(::addLiveResult)
                updateState(
                    message = if (working.isEmpty()) "Нет доступных прокси" else "Готово · ${working.size} доступных",
                    phase = "Завершено",
                    progress = 1f,
                    finished = true,
                    error = if (working.isEmpty()) "Попробуйте другой профиль или повторите позже." else null
                )
            } catch (_: CancellationException) {
                updateState(message = "Скан остановлен", phase = "Остановлено", finished = true)
                persistCurrentResults()
            } catch (error: Exception) {
                updateState(error = error.message ?: "Ошибка сканирования", finished = true)
            }
        }
    }

    private fun updateState(
        message: String = uiState.message,
        phase: String = uiState.phase,
        processed: Int = uiState.processed,
        total: Int = uiState.total,
        progress: Float = uiState.progress,
        finished: Boolean = uiState.finished,
        error: String? = uiState.error
    ) {
        runOnUiThread {
            uiState = uiState.copy(
                message = message,
                phase = phase,
                processed = processed,
                total = total,
                progress = progress.coerceIn(0f, 1f),
                finished = finished,
                error = error
            )
        }
    }

    private fun addLiveResult(proxy: ProxyWithPing) {
        runOnUiThread {
            if (uiState.found.any { it.url == proxy.url }) return@runOnUiThread
            uiState = uiState.copy(found = (uiState.found + proxy).sortedBy { it.pingMs })
        }
    }

    private fun persistCurrentResults() {
        val found = uiState.found
        if (found.isEmpty()) return
        val effective = if (ProfileSettings.forMode(profileMode, this).mode == NetworkProfileMode.MOBILE) {
            NetworkProfileMode.MOBILE
        } else NetworkProfileMode.WIFI
        ProxyCache.saveWorking(this, effective, found)
    }

    private fun cancelOrClose() {
        if (!uiState.finished) {
            scanJob?.cancel()
            updateState(message = "Остановка…", phase = "Завершение")
        } else finish()
    }

    private fun openFullResults() {
        if (uiState.found.isEmpty()) return
        startActivity(Intent(this, ProxyListActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_PROXIES, ArrayList(uiState.found))
            putExtra(MainActivity.EXTRA_SOURCE_NAME, sourceName)
        })
    }

    private fun connect(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "Не удалось открыть Telegram", Toast.LENGTH_SHORT).show() }
    }

    private data class ScanUiState(
        val phase: String = "Подготовка",
        val message: String = "Запуск сканирования…",
        val processed: Int = 0,
        val total: Int = 0,
        val progress: Float = 0f,
        val found: List<ProxyWithPing> = emptyList(),
        val finished: Boolean = false,
        val error: String? = null
    )
}
