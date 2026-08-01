package com.kupuproxy.app.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import com.kupuproxy.app.HomeLayoutPreferences
import com.kupuproxy.app.HomeSourceLayout
import com.kupuproxy.app.R
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import com.kupuproxy.app.ui.theme.kupuSafeScreen

class HomeLayoutActivity : AppCompatActivity() {
    private var layout by mutableStateOf(HomeSourceLayout(emptyList(), emptySet()))

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        layout = HomeLayoutPreferences.load(this)
        setContent {
            KupuProxyTheme {
                Scaffold(
                    modifier = Modifier.kupuSafeScreen(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.home_layout_title)) },
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                }
                            },
                        )
                    },
                ) { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(vertical = 8.dp)) {
                        item { Text(stringResource(R.string.home_layout_summary), Modifier.padding(16.dp)) }
                        items(layout.order, key = { it }) { id ->
                            val index = layout.order.indexOf(id)
                            ListItem(
                                headlineContent = { Text(sourceName(id)) },
                                leadingContent = {
                                    Switch(
                                        checked = id !in layout.hidden,
                                        onCheckedChange = { visible ->
                                            save(layout.copy(hidden = layout.hidden.toMutableSet().apply {
                                                if (visible) remove(id) else add(id)
                                            }))
                                        },
                                    )
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(enabled = index > 0, onClick = { move(index, index - 1) }) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = null)
                                        }
                                        IconButton(enabled = index < layout.order.lastIndex, onClick = { move(index, index + 1) }) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = null)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun move(from: Int, to: Int) {
        val order = layout.order.toMutableList()
        val item = order.removeAt(from)
        order.add(to, item)
        save(layout.copy(order = order))
    }

    private fun save(value: HomeSourceLayout) {
        layout = value
        HomeLayoutPreferences.save(this, value)
    }

    @androidx.compose.runtime.Composable
    private fun sourceName(id: String): String = when (id) {
        "solispirit" -> "SoliSpirit Mega"
        "shablin_valid" -> "Shablin latency"
        "dubblebyte" -> "Dubblebyte free MTProto"
        "surfboard" -> "SurfboardV2ray"
        else -> "Argh94 Scraper"
    }
}
