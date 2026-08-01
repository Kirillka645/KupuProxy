package com.kupuproxy.app

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import com.kupuproxy.app.ui.theme.kupuSafeScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CheckFileActivity : AppCompatActivity() {

    private var fileUri: Uri? = null
    private var profileMode = NetworkProfileMode.AUTO
    private var checkJob: Job? = null
    private var state by mutableStateOf(FileCheckState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state =
            FileCheckState(
                title = getString(R.string.preparing),
                message = getString(R.string.file_initial_message),
            )
        fileUri =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(MainActivity.EXTRA_FILE_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(MainActivity.EXTRA_FILE_URI)
            }
        profileMode =
            runCatching {
                    NetworkProfileMode.valueOf(
                        intent.getStringExtra(MainActivity.EXTRA_PROFILE)
                            ?: NetworkProfileMode.AUTO.name
                    )
                }
                .getOrDefault(NetworkProfileMode.AUTO)

        setContent { KupuProxyTheme { FileCheckScreen() } }
        startChecking()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FileCheckScreen() {
        Scaffold(
            modifier = Modifier.kupuSafeScreen(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.file_check_title),
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
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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
                                if (state.total > 0) "${state.processed} / ${state.total}"
                                else stringResource(R.string.preparing)
                            )
                            Text(
                                stringResource(R.string.file_working_count, state.working),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (state.results.isNotEmpty()) {
                    Button(onClick = ::openResults, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.file_open_results, state.results.size))
                    }
                }
                OutlinedButton(onClick = ::cancelOrClose, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (state.finished) R.string.close else R.string.cancel))
                }
            }
        }
    }

    private fun startChecking() {
        val uri = fileUri
        if (uri == null) {
            state =
                state.copy(
                    title = getString(R.string.file_not_selected_title),
                    message = getString(R.string.file_not_selected_message),
                    error = true,
                    finished = true,
                )
            return
        }
        val settings = ProfileSettings.forMode(profileMode, this)
        checkJob =
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    updateState(
                        title = getString(R.string.file_reading_title),
                        message = getString(R.string.file_reading_message),
                    )
                    val proxies = ProxyManager.loadProxiesFromFile(contentResolver, uri)
                    if (proxies.isEmpty()) {
                        updateState(
                            title = getString(R.string.file_no_proxies_title),
                            message = getString(R.string.file_no_proxies_message),
                            error = true,
                            finished = true,
                        )
                        return@launch
                    }
                    val prepared = ProxyManager.prepareForProfile(proxies, settings)
                    updateState(
                        title = getString(R.string.file_checking_title),
                        message = getString(R.string.file_profile, settings.label),
                        total = prepared.size,
                    )
                    val checked =
                        ProxyManager.checkProxiesPingParallel(
                            prepared,
                            settings,
                            settings.label,
                            onProgress = { processed, total, working ->
                                updateState(
                                    processed = processed,
                                    total = total,
                                    working = working,
                                    progress = if (total > 0) processed.toFloat() / total else 0f,
                                )
                            },
                        )
                    if (checked.isNotEmpty()) {
                        val effective =
                            if (settings.mode == NetworkProfileMode.MOBILE)
                                NetworkProfileMode.MOBILE
                            else NetworkProfileMode.WIFI
                        ProxyCache.saveWorking(this@CheckFileActivity, effective, checked)
                    }
                    updateState(
                        title =
                            if (checked.isEmpty()) getString(R.string.file_none_working_title)
                            else getString(R.string.file_complete_title),
                        message =
                            if (checked.isEmpty()) getString(R.string.file_try_other)
                            else getString(R.string.file_found_count, checked.size),
                        processed = prepared.size,
                        total = prepared.size,
                        progress = 1f,
                        results = checked,
                        error = checked.isEmpty(),
                        finished = true,
                    )
                } catch (_: CancellationException) {
                    updateState(
                        title = getString(R.string.file_stopped_title),
                        message = getString(R.string.operation_cancelled),
                        finished = true,
                    )
                } catch (_: Exception) {
                    updateState(
                        title = getString(R.string.error_title),
                        message = getString(R.string.file_error),
                        error = true,
                        finished = true,
                    )
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
        finished: Boolean = state.finished,
    ) {
        runOnUiThread {
            state =
                FileCheckState(
                    title,
                    message,
                    processed,
                    total,
                    working,
                    progress.coerceIn(0f, 1f),
                    results,
                    error,
                    finished,
                )
        }
    }

    private fun openResults() {
        startActivity(
            Intent(this, ProxyListActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_PROXIES, ArrayList(state.results))
                putExtra(
                    MainActivity.EXTRA_SOURCE_NAME,
                    getString(
                        R.string.file_from_source,
                        ProfileSettings.forMode(profileMode, this@CheckFileActivity).label,
                    ),
                )
            }
        )
    }

    private fun cancelOrClose() {
        if (!state.finished) checkJob?.cancel() else finish()
    }

    private data class FileCheckState(
        val title: String = "",
        val message: String = "",
        val processed: Int = 0,
        val total: Int = 0,
        val working: Int = 0,
        val progress: Float = 0f,
        val results: List<ProxyWithPing> = emptyList(),
        val error: Boolean = false,
        val finished: Boolean = false,
    )
}
