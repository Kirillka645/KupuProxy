package com.kupuproxy.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kupuproxy.app.InsightsStore
import com.kupuproxy.app.ProxyInsight
import com.kupuproxy.app.R
import com.kupuproxy.app.SourceInsight
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import com.kupuproxy.app.ui.theme.kupuSafeScreen

class InsightsActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val proxies = InsightsStore.topReliable(this)
        val sources = InsightsStore.loadSourceInsights(this).sortedByDescending { it.lastCount }
        setContent {
            KupuProxyTheme {
                Scaffold(
                    modifier = Modifier.kupuSafeScreen(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.insights_title)) },
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back),
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { Section(stringResource(R.string.insights_sources)) }
                        if (sources.isEmpty()) item { EmptyHint() }
                        items(sources, key = SourceInsight::name) { SourceCard(it) }
                        item { Section(stringResource(R.string.insights_reliable)) }
                        if (proxies.isEmpty()) item { EmptyHint() }
                        items(proxies, key = ProxyInsight::url) { ProxyCard(it) }
                    }
                }
            }
        }
    }

    @Composable
    private fun Section(text: String) = Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp),
    )

    @Composable
    private fun EmptyHint() = Text(
        stringResource(R.string.insights_empty),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    @Composable
    private fun SourceCard(item: SourceInsight) {
        val sourceTitle =
            sourceNameResource(item.name)?.let { stringResource(it) } ?: item.name
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sourceTitle, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.insights_source_count, item.lastCount))
                }
            }
        }
    }

    @Composable
    private fun ProxyCard(item: ProxyInsight) {
        Card(onClick = { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) } }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(
                            R.string.insights_proxy_score,
                            item.reliability,
                            item.successes,
                            item.failures,
                            item.lastPingMs,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.connect),
                )
            }
        }
    }
}
