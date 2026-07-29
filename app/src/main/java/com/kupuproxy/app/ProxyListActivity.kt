package com.kupuproxy.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kupuproxy.app.core.util.TelegramIntents
import com.kupuproxy.app.data.local.prefs.PromoPreferences
import com.kupuproxy.app.ui.components.ProxyResultCard
import com.kupuproxy.app.ui.components.channel.ChannelInviteDialog
import com.kupuproxy.app.ui.components.channel.EmptyStateWithChannel
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import kotlinx.coroutines.launch

class ProxyListActivity : ComponentActivity() {

    private lateinit var promoPreferences: PromoPreferences
    private var sourceName by mutableStateOf("Прокси")
    private var proxies by mutableStateOf<List<ProxyWithPing>>(emptyList())
    private var maxPing by mutableIntStateOf(Int.MAX_VALUE)
    private var filterMenu by mutableStateOf(false)
    private var showInvite by mutableStateOf(false)
    private var favoriteVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        promoPreferences = PromoPreferences(this)
        sourceName = intent.getStringExtra(MainActivity.EXTRA_SOURCE_NAME) ?: "Прокси"
        proxies = readProxies()
        setContent { KupuProxyTheme { ProxyListScreen() } }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readProxies(): List<ProxyWithPing> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getSerializableExtra(MainActivity.EXTRA_PROXIES, ArrayList::class.java) as? List<ProxyWithPing> ?: emptyList()
    } else {
        @Suppress("DEPRECATION")
        intent.getSerializableExtra(MainActivity.EXTRA_PROXIES) as? ArrayList<ProxyWithPing> ?: emptyList()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ProxyListScreen() {
        favoriteVersion
        val filtered = if (maxPing == Int.MAX_VALUE) proxies else proxies.filter { it.pingMs in 1..maxPing }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(sourceName, fontWeight = FontWeight.Bold)
                            Text(
                                listSubtitle(filtered),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = ::finish) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        IconButton(onClick = { shareList(filtered) }) {
                            Icon(Icons.Default.Share, contentDescription = "Поделиться")
                        }
                        IconButton(onClick = { copyAll(filtered) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                        }
                        IconButton(onClick = { filterMenu = true }) {
                            Icon(Icons.Default.FilterAlt, contentDescription = "Фильтр")
                        }
                        DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                            filterOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.first) },
                                    onClick = {
                                        maxPing = option.second
                                        filterMenu = false
                                    }
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (filtered.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = { copyTop(filtered) },
                        icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        text = { Text("Топ-10") }
                    )
                }
            }
        ) { padding ->
            if (filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmptyStateWithChannel(onOpenChannel = { TelegramIntents.openTelegramChannel(this@ProxyListActivity) })
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = ProxyWithPing::url) { proxy ->
                        ProxyResultCard(
                            proxy = proxy,
                            favorite = ProxyCache.isFavorite(this@ProxyListActivity, proxy.url),
                            onConnect = { connect(proxy.url) },
                            onToggleFavorite = {
                                ProxyCache.toggleFavorite(this@ProxyListActivity, proxy.url)
                                favoriteVersion++
                            },
                            onCopy = {
                                copyToClipboard(proxy.url)
                                Toast.makeText(this@ProxyListActivity, "Прокси скопирован", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        if (showInvite) {
            ChannelInviteDialog(
                onSubscribe = {
                    showInvite = false
                    TelegramIntents.openTelegramChannel(this)
                },
                onDismiss = { showInvite = false }
            )
        }
    }

    private fun listSubtitle(list: List<ProxyWithPing>): String {
        val measured = list.filter { it.pingMs > 0 }
        val avg = if (measured.isNotEmpty()) measured.map { it.pingMs }.average().toInt() else 0
        return buildString {
            append("${list.size} прокси")
            if (avg > 0) append(" · средний $avg ms")
            if (maxPing < Int.MAX_VALUE) append(" · ≤ $maxPing ms")
        }
    }

    private fun connect(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onSuccess {
                lifecycleScope.launch {
                    promoPreferences.recordSuccessfulConnect()
                    if (promoPreferences.shouldShowInviteDialog()) {
                        promoPreferences.markInviteShown()
                        showInvite = true
                    }
                }
            }
            .onFailure { Toast.makeText(this, "Не удалось открыть Telegram", Toast.LENGTH_SHORT).show() }
    }

    private fun copyTop(list: List<ProxyWithPing>) {
        val top = list.take(10)
        copyToClipboard(formatWithFooter(top))
        Toast.makeText(this, "Скопировано ${top.size} прокси", Toast.LENGTH_SHORT).show()
    }

    private fun copyAll(list: List<ProxyWithPing>) {
        if (list.isEmpty()) return
        copyToClipboard(formatWithFooter(list))
        Toast.makeText(this, "Скопировано ${list.size} прокси", Toast.LENGTH_SHORT).show()
    }

    private fun shareList(list: List<ProxyWithPing>) {
        if (list.isEmpty()) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, formatWithFooter(list.take(50)))
        }, "Поделиться прокси"))
    }

    private fun formatWithFooter(list: List<ProxyWithPing>): String {
        val body = list.mapIndexed { index, proxy ->
            if (proxy.pingMs > 0) "${index + 1}. ${proxy.url} (${proxy.pingMs} ms)" else "${index + 1}. ${proxy.url}"
        }.joinToString("\n")
        return "$body\n\nKupuProxy — https://github.com/${BuildConfig.GITHUB_REPO}"
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("KupuProxy", text))
    }

    private val filterOptions = listOf(
        "Все" to Int.MAX_VALUE,
        "До 100 ms" to 100,
        "До 200 ms" to 200,
        "До 300 ms" to 300,
        "До 500 ms" to 500
    )
}
