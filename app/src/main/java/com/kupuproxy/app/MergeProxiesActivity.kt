package com.kupuproxy.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MergeProxiesActivity : AppCompatActivity() {

    private var mergeJob: Job? = null
    private var state by mutableStateOf(MergeState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state =
            MergeState(
                title = getString(R.string.preparing),
                message = getString(R.string.merge_initial_message),
            )
        setContent { KupuProxyTheme { MergeScreen() } }
        startMerging()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MergeScreen() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.merge_screen_title),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = ::cancelOrClose) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Icon(
                    if (state.finished && !state.error) Icons.Default.CheckCircle
                    else Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint =
                        if (state.error) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                )
                Text(
                    state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    state.message,
                    color =
                        if (state.error) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                if (state.total > 0)
                                    stringResource(
                                        R.string.merge_source_progress,
                                        state.current,
                                        state.total,
                                    )
                                else stringResource(R.string.preparing)
                            )
                            Text(
                                stringResource(R.string.merge_unique_count, state.count),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (state.fileName.isNotBlank()) {
                            Text(
                                "Downloads/${state.fileName}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (state.finished && state.error) {
                    Button(
                        onClick = {
                            state = MergeState()
                            startMerging()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
                OutlinedButton(onClick = ::cancelOrClose, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (state.finished) R.string.close else R.string.cancel))
                }
            }
        }
    }

    private fun startMerging() {
        mergeJob =
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    updateState(
                        title = getString(R.string.merge_loading_title),
                        message = getString(R.string.merge_loading_message),
                    )
                    val result =
                        ProxyManager.fetchAllSources(this@MergeProxiesActivity) {
                            index,
                            total,
                            name,
                            count ->
                            updateState(
                                title = getString(R.string.merge_collecting_title),
                                message = getString(R.string.merge_source_added, name, count),
                                current = index,
                                total = total,
                                progress = if (total > 0) index.toFloat() / total else 0f,
                            )
                        }
                    val proxies =
                        result.proxies.ifEmpty {
                            ProxyCache.loadSeedFromAssets(this@MergeProxiesActivity)
                        }
                    if (proxies.isEmpty()) {
                        updateState(
                            title = getString(R.string.merge_failed_title),
                            message = getString(R.string.merge_failed_message),
                            error = true,
                            finished = true,
                        )
                        return@launch
                    }
                    updateState(
                        title = getString(R.string.merge_saving_title),
                        message = getString(R.string.merge_saving_message, proxies.size),
                        count = proxies.size,
                    )
                    val file =
                        ProxyManager.saveProxiesEverywhere(this@MergeProxiesActivity, proxies)
                    updateState(
                        title = getString(R.string.merge_done_title),
                        message =
                            if (file != null) {
                                getString(R.string.merge_saved_downloads)
                            } else {
                                getString(R.string.merge_saved_cache)
                            },
                        count = proxies.size,
                        fileName = file?.name.orEmpty(),
                        progress = 1f,
                        finished = true,
                    )
                } catch (_: CancellationException) {
                    updateState(
                        title = getString(R.string.merge_stopped_title),
                        message = getString(R.string.operation_cancelled),
                        finished = true,
                    )
                } catch (_: Exception) {
                    updateState(
                        title = getString(R.string.error_title),
                        message = getString(R.string.merge_error),
                        error = true,
                        finished = true,
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
        finished: Boolean = state.finished,
    ) {
        runOnUiThread {
            state =
                MergeState(
                    title,
                    message,
                    current,
                    total,
                    count,
                    fileName,
                    progress.coerceIn(0f, 1f),
                    error,
                    finished,
                )
        }
    }

    private fun cancelOrClose() {
        if (!state.finished) mergeJob?.cancel() else finish()
    }

    private data class MergeState(
        val title: String = "",
        val message: String = "",
        val current: Int = 0,
        val total: Int = 0,
        val count: Int = 0,
        val fileName: String = "",
        val progress: Float = 0f,
        val error: Boolean = false,
        val finished: Boolean = false,
    )
}
