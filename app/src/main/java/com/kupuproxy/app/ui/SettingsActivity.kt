package com.kupuproxy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kupuproxy.app.R
import com.kupuproxy.app.core.util.TelegramIntents
import com.kupuproxy.app.ui.components.channel.ChannelSettingsListItem
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import com.kupuproxy.app.work.ProxyRefreshPreferences

class SettingsActivity : ComponentActivity() {
    private var refreshSettings by mutableStateOf(ProxyRefreshPreferences.Settings())
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshSettings = ProxyRefreshPreferences.load(this)
        setContent {
            KupuProxyTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.settings_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ChannelSettingsListItem(
                            onClick = { TelegramIntents.openTelegramChannel(this@SettingsActivity) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Мои источники прокси") },
                            supportingContent = {
                                Text(
                                    "Свои URL (txt/json) для мега-скана — работают без Telegram",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    startActivity(
                                        Intent(
                                            this@SettingsActivity,
                                            UserSourcesActivity::class.java
                                        )
                                    )
                                }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Автообновление прокси") },
                            supportingContent = { Text("Verified collector и основные GitHub-источники") },
                            trailingContent = {
                                Switch(
                                    checked = refreshSettings.enabled,
                                    onCheckedChange = { saveRefresh(refreshSettings.copy(enabled = it)) }
                                )
                            }
                        )
                        Text("Интервал", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(3L, 6L, 12L, 24L).forEach { hours ->
                                AssistChip(
                                    onClick = { saveRefresh(refreshSettings.copy(hours = hours)) },
                                    label = { Text(if (refreshSettings.hours == hours) "✓ ${hours}ч" else "${hours}ч") }
                                )
                            }
                        }
                        ListItem(
                            headlineContent = { Text("Только безлимитная сеть") },
                            supportingContent = { Text("Обычно Wi-Fi; Android определяет сеть как unmetered") },
                            trailingContent = {
                                Switch(
                                    checked = refreshSettings.wifiOnly,
                                    onCheckedChange = { saveRefresh(refreshSettings.copy(wifiOnly = it)) }
                                )
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Источник Kort Verified") },
                            supportingContent = {
                                Text("Публичные generated feeds kort0881/telegram-proxy-collector. KupuProxy независимо проверяет MTProto handshake.")
                            },
                            modifier = Modifier.clickable {
                                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/kort0881/telegram-proxy-collector")))
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Text(
                            text = stringResource(R.string.settings_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                        Text(
                            text = "Если Telegram недоступен: мега-скан берёт GitHub/CDN и зеркала каналов (Jina, RSSHub), не только t.me.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    private fun saveRefresh(settings: ProxyRefreshPreferences.Settings) {
        refreshSettings = settings
        ProxyRefreshPreferences.save(this, settings)
    }
}
