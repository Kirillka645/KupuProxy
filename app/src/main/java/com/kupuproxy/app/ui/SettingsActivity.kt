package com.kupuproxy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kupuproxy.app.MainActivity
import com.kupuproxy.app.R
import com.kupuproxy.app.core.locale.AppLocaleManager
import com.kupuproxy.app.core.util.TelegramIntents
import com.kupuproxy.app.ui.components.channel.ChannelSettingsListItem
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import com.kupuproxy.app.ui.theme.kupuSafeScreen
import com.kupuproxy.app.work.ProxyRefreshPreferences
import com.kupuproxy.app.work.UpdatePreferences

class SettingsActivity : AppCompatActivity() {
    private var refreshSettings by mutableStateOf(ProxyRefreshPreferences.Settings())
    private var languageDialogVisible by mutableStateOf(false)
    private var autoUpdateEnabled by mutableStateOf(true)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshSettings = ProxyRefreshPreferences.load(this)
        autoUpdateEnabled = UpdatePreferences.isAutoCheckEnabled(this)
        setContent {
            KupuProxyTheme {
                Scaffold(
                    modifier = Modifier.kupuSafeScreen(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.settings_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(
                        modifier =
                            Modifier.fillMaxSize()
                                .padding(padding)
                                .verticalScroll(rememberScrollState())
                    ) {
                        LanguageListItem()
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.appearance_title)) },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.appearance_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Default.Palette, contentDescription = null)
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.fillMaxWidth().clickable {
                                    startActivity(
                                        Intent(
                                            this@SettingsActivity,
                                            AppearanceActivity::class.java,
                                        )
                                    )
                                },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ChannelSettingsListItem(
                            onClick = { TelegramIntents.openTelegramChannel(this@SettingsActivity) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.settings_custom_sources))
                            },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.settings_custom_sources_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.fillMaxWidth().clickable {
                                    startActivity(
                                        Intent(
                                            this@SettingsActivity,
                                            UserSourcesActivity::class.java,
                                        )
                                    )
                                },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.settings_proxy_refresh))
                            },
                            supportingContent = {
                                Text(stringResource(R.string.settings_proxy_refresh_summary))
                            },
                            trailingContent = {
                                Switch(
                                    checked = refreshSettings.enabled,
                                    onCheckedChange = {
                                        saveRefresh(refreshSettings.copy(enabled = it))
                                    },
                                )
                            },
                        )
                        Text(
                            stringResource(R.string.settings_interval),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(3L, 6L, 12L, 24L).forEach { hours ->
                                AssistChip(
                                    onClick = { saveRefresh(refreshSettings.copy(hours = hours)) },
                                    label = {
                                        Text(
                                            stringResource(
                                                if (refreshSettings.hours == hours)
                                                    R.string.settings_hours_selected
                                                else R.string.settings_hours,
                                                hours,
                                            )
                                        )
                                    },
                                )
                            }
                        }
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.settings_unmetered_only))
                            },
                            supportingContent = {
                                Text(stringResource(R.string.settings_unmetered_summary))
                            },
                            trailingContent = {
                                Switch(
                                    checked = refreshSettings.wifiOnly,
                                    onCheckedChange = {
                                        saveRefresh(refreshSettings.copy(wifiOnly = it))
                                    },
                                )
                            },
                        )
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.settings_kort_source))
                            },
                            supportingContent = {
                                Text(stringResource(R.string.settings_kort_source_summary))
                            },
                            modifier =
                                Modifier.clickable {
                                    startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            android.net.Uri.parse(
                                                "https://github.com/kort0881/telegram-proxy-collector"
                                            ),
                                        )
                                    )
                                },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.settings_auto_update))
                            },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.settings_auto_update_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = autoUpdateEnabled,
                                    onCheckedChange = { enabled ->
                                        autoUpdateEnabled = enabled
                                        UpdatePreferences.setAutoCheckEnabled(
                                            this@SettingsActivity,
                                            enabled,
                                        )
                                    },
                                )
                            },
                        )
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.settings_check_updates))
                            },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.settings_check_updates_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.fillMaxWidth().clickable {
                                    startActivity(
                                        Intent(this@SettingsActivity, MainActivity::class.java)
                                            .apply {
                                                addFlags(
                                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                                )
                                                putExtra(MainActivity.EXTRA_CHECK_UPDATES, true)
                                            }
                                    )
                                },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Text(
                            text = stringResource(R.string.settings_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.settings_bypass_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                if (languageDialogVisible) LanguageDialog()
            }
        }
    }

    @Composable
    private fun LanguageListItem() {
        val selectedTag = AppLocaleManager.currentTag()
        val selectedName =
            AppLocaleManager.supportedLocales
                .firstOrNull { it.languageTag == selectedTag }
                ?.nativeName ?: stringResource(R.string.language_follow_system)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_language)) },
            supportingContent = {
                Column {
                    Text(selectedName)
                    Text(
                        stringResource(R.string.settings_language_summary),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth().clickable { languageDialogVisible = true },
        )
    }

    @Composable
    private fun LanguageDialog() {
        val selectedTag = AppLocaleManager.currentTag()
        AlertDialog(
            onDismissRequest = { languageDialogVisible = false },
            title = { Text(stringResource(R.string.language_dialog_title)) },
            text = {
                LazyColumn {
                    item {
                        LanguageOption(
                            title = stringResource(R.string.language_follow_system),
                            selected = selectedTag == null,
                            onClick = { selectLanguage(null) },
                        )
                    }
                    items(AppLocaleManager.supportedLocales, key = { it.languageTag }) { locale ->
                        LanguageOption(
                            title = locale.nativeName,
                            selected = selectedTag == locale.languageTag,
                            onClick = { selectLanguage(locale.languageTag) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languageDialogVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    @Composable
    private fun LanguageOption(
        title: String,
        selected: Boolean,
        onClick: () -> Unit,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(title, modifier = Modifier.padding(start = 8.dp))
        }
    }

    private fun selectLanguage(languageTag: String?) {
        languageDialogVisible = false
        AppLocaleManager.apply(languageTag)
    }

    private fun saveRefresh(settings: ProxyRefreshPreferences.Settings) {
        refreshSettings = settings
        ProxyRefreshPreferences.save(this, settings)
    }
}
