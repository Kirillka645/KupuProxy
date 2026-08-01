package com.kupuproxy.app.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kupuproxy.app.MAX_SCAN_PROXIES
import com.kupuproxy.app.R
import com.kupuproxy.app.ScanConfiguration
import com.kupuproxy.app.ScanMode
import com.kupuproxy.app.ScanPreferences
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import com.kupuproxy.app.ui.theme.kupuImeAware
import com.kupuproxy.app.ui.theme.kupuSafeScreen

class ScanSettingsActivity : AppCompatActivity() {
    private var configuration by mutableStateOf(ScanConfiguration())
    private var limitText by mutableStateOf("")
    private var workersText by mutableStateOf("")

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration = ScanPreferences.load(this)
        limitText = configuration.customLimit.toString()
        workersText = configuration.customWorkers.toString()
        setContent {
            KupuProxyTheme {
                Scaffold(
                    modifier = Modifier.kupuSafeScreen(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.scan_settings_title)) },
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                }
                            },
                        )
                    },
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).kupuImeAware(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(ScanMode.entries, key = ScanMode::name) { mode ->
                            Card(
                                onClick = { save(configuration.copy(mode = mode)) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = configuration.mode == mode,
                                        onClick = { save(configuration.copy(mode = mode)) },
                                    )
                                    Column(Modifier.padding(start = 10.dp)) {
                                        Text(modeLabel(mode), style = MaterialTheme.typography.titleMedium)
                                        Text(modeSummary(mode), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        if (configuration.mode == ScanMode.CUSTOM) {
                            item {
                                OutlinedTextField(
                                    value = limitText,
                                    onValueChange = { value ->
                                        if (value.all(Char::isDigit) && value.length <= 5) {
                                            limitText = value
                                            value.toIntOrNull()?.takeIf { it in 100..MAX_SCAN_PROXIES }?.let {
                                                save(configuration.copy(customLimit = it))
                                            }
                                        }
                                    },
                                    label = { Text(stringResource(R.string.scan_custom_limit)) },
                                    supportingText = { Text(stringResource(R.string.scan_custom_limit_hint, MAX_SCAN_PROXIES)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            item {
                                OutlinedTextField(
                                    value = workersText,
                                    onValueChange = { value ->
                                        if (value.all(Char::isDigit) && value.length <= 2) {
                                            workersText = value
                                            value.toIntOrNull()?.takeIf { it in 16..96 }?.let {
                                                save(configuration.copy(customWorkers = it))
                                            }
                                        }
                                    },
                                    label = { Text(stringResource(R.string.scan_custom_workers)) },
                                    supportingText = { Text(stringResource(R.string.scan_custom_workers_hint)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun save(value: ScanConfiguration) {
        configuration = value
        ScanPreferences.save(this, value)
    }

    @androidx.compose.runtime.Composable
    private fun modeLabel(mode: ScanMode) = stringResource(
        when (mode) {
            ScanMode.QUICK -> R.string.scan_mode_quick
            ScanMode.BALANCED -> R.string.scan_mode_balanced
            ScanMode.FULL -> R.string.scan_mode_full
            ScanMode.CUSTOM -> R.string.scan_mode_custom
        }
    )

    @androidx.compose.runtime.Composable
    private fun modeSummary(mode: ScanMode) = stringResource(
        when (mode) {
            ScanMode.QUICK -> R.string.scan_mode_quick_summary
            ScanMode.BALANCED -> R.string.scan_mode_balanced_summary
            ScanMode.FULL -> R.string.scan_mode_full_summary
            ScanMode.CUSTOM -> R.string.scan_mode_custom_description
        }
    )
}
