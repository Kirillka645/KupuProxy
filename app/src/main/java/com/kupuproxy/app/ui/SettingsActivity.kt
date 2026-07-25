package com.kupuproxy.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.compose.material3.ListItem
import androidx.compose.foundation.clickable
import com.kupuproxy.app.R
import com.kupuproxy.app.core.util.TelegramIntents
import com.kupuproxy.app.ui.components.channel.ChannelSettingsListItem
import com.kupuproxy.app.ui.theme.KupuProxyTheme

class SettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KupuProxyTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.settings_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.Default.ArrowBack,
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
                    ) {
                        ChannelSettingsListItem(
                            onClick = { TelegramIntents.openTelegramChannel(this@SettingsActivity) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Мои источники прокси") },
                            supportingContent = { Text("Пользовательские URL для мега-скана") },
                            modifier = Modifier.clickable {
                                startActivity(
                                    Intent(this@SettingsActivity, UserSourcesActivity::class.java)
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Text(
                            text = stringResource(R.string.settings_hint),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
