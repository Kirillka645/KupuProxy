package com.kupuproxy.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.kupuproxy.app.core.util.TelegramIntents
import com.kupuproxy.app.data.local.prefs.PromoPreferences
import com.kupuproxy.app.ui.AboutActivity
import com.kupuproxy.app.ui.components.channel.EmptyStateWithChannel
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import kotlinx.coroutines.launch

class ProxyListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var fabCopyTop10: ExtendedFloatingActionButton
    private lateinit var tvSubtitle: TextView
    private lateinit var emptyStateCompose: ComposeView
    private lateinit var promoPreferences: PromoPreferences

    private var proxiesList: List<ProxyWithPing> = emptyList()
    private var filteredList: List<ProxyWithPing> = emptyList()
    private var sourceName: String = ""
    private var maxPingFilter = Int.MAX_VALUE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_proxy_list)
        promoPreferences = PromoPreferences(this)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        sourceName = intent.getStringExtra(MainActivity.EXTRA_SOURCE_NAME) ?: "Прокси"
        supportActionBar?.title = sourceName

        @Suppress("UNCHECKED_CAST")
        proxiesList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(MainActivity.EXTRA_PROXIES, ArrayList::class.java)
                as? List<ProxyWithPing> ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(MainActivity.EXTRA_PROXIES) as? ArrayList<ProxyWithPing>
                ?: emptyList()
        }

        filteredList = proxiesList
        tvSubtitle = findViewById(R.id.tvListSubtitle)
        emptyStateCompose = findViewById(R.id.emptyStateCompose)
        updateSubtitle()

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        bindAdapter()

        fabCopyTop10 = findViewById(R.id.fabCopyTop10)
        fabCopyTop10.setOnClickListener { copyTop10Proxies() }

        setupToolbarMenu()
        setupEmptyState()
        refreshEmptyVisibility()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    private fun bindAdapter() {
        recyclerView.adapter = ProxyAdapter(this, filteredList) {
            lifecycleScope.launch { maybeShowChannelInvite() }
        }
    }

    private fun setupEmptyState() {
        emptyStateCompose.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        emptyStateCompose.setContent {
            KupuProxyTheme {
                EmptyStateWithChannel(
                    onOpenChannel = { TelegramIntents.openTelegramChannel(this@ProxyListActivity) }
                )
            }
        }
    }

    private fun refreshEmptyVisibility() {
        val empty = filteredList.isEmpty()
        emptyStateCompose.visibility = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
        fabCopyTop10.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private suspend fun maybeShowChannelInvite() {
        promoPreferences.recordSuccessfulConnect()
        if (!promoPreferences.shouldShowInviteDialog()) return
        promoPreferences.markInviteShown()
        runOnUiThread {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.channel_invite_title)
                .setMessage(R.string.channel_invite_body)
                .setPositiveButton(R.string.channel_subscribe) { _, _ ->
                    TelegramIntents.openTelegramChannel(this)
                }
                .setNegativeButton(R.string.later, null)
                .show()
        }
    }

    private fun updateSubtitle() {
        val list = filteredList
        val withPing = list.filter { it.pingMs > 0 }
        val avg = if (withPing.isNotEmpty()) withPing.map { it.pingMs }.average().toInt() else 0
        val profile = list.firstOrNull()?.profileLabel.orEmpty()
        tvSubtitle.text = buildString {
            append("${list.size} шт.")
            if (avg > 0) append(" · ср. $avg ms")
            if (profile.isNotBlank()) append(" · $profile")
            if (maxPingFilter < Int.MAX_VALUE) append(" · фильтр ≤ ${maxPingFilter}ms")
        }
    }

    private fun setupToolbarMenu() {
        toolbar.inflateMenu(R.menu.proxy_list_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_about -> {
                    showAboutDialog()
                    true
                }
                R.id.action_copy_all -> {
                    copyAllProxies()
                    true
                }
                R.id.action_filter -> {
                    showFilterDialog()
                    true
                }
                R.id.action_share -> {
                    shareList()
                    true
                }
                else -> false
            }
        }
    }

    private fun showFilterDialog() {
        val options = arrayOf("Все", "≤ 100 ms", "≤ 200 ms", "≤ 300 ms", "≤ 500 ms")
        val values = intArrayOf(Int.MAX_VALUE, 100, 200, 300, 500)
        MaterialAlertDialogBuilder(this)
            .setTitle("Фильтр по пингу")
            .setItems(options) { _, which ->
                maxPingFilter = values[which]
                filteredList = if (maxPingFilter == Int.MAX_VALUE) {
                    proxiesList
                } else {
                    proxiesList.filter { it.pingMs in 1..maxPingFilter }
                }
                bindAdapter()
                updateSubtitle()
                refreshEmptyVisibility()
            }
            .show()
    }

    private fun shareList() {
        val text = formatWithFooter(filteredList.take(50))
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Поделиться прокси"
            )
        )
    }

    private fun copyTop10Proxies() {
        val top = filteredList.take(10)
        if (top.isEmpty()) {
            Toast.makeText(this, R.string.no_proxies, Toast.LENGTH_SHORT).show()
            return
        }
        copyToClipboard(formatWithFooter(top))
        Toast.makeText(this, "Скопировано ${top.size} прокси", Toast.LENGTH_SHORT).show()
    }

    private fun copyAllProxies() {
        if (filteredList.isEmpty()) {
            Toast.makeText(this, R.string.no_proxies, Toast.LENGTH_SHORT).show()
            return
        }
        copyToClipboard(formatWithFooter(filteredList))
        Toast.makeText(this, "Скопировано ${filteredList.size} прокси", Toast.LENGTH_SHORT).show()
    }

    private fun formatWithFooter(proxies: List<ProxyWithPing>): String {
        val body = proxies.mapIndexed { i, p ->
            if (p.pingMs > 0) "${i + 1}. ${p.url}  (${p.pingMs}ms)"
            else "${i + 1}. ${p.url}"
        }.joinToString("\n")
        return "$body\n\nKupuProxy — https://github.com/${BuildConfig.GITHUB_REPO}"
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("KupuProxy", text))
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("KupuProxy v${BuildConfig.VERSION_NAME}")
            .setMessage(
                "MTProto-прокси для Telegram.\n" +
                    "CDN-зеркала, профили Wi‑Fi/LTE, seed и кэш.\n\n" +
                    "Канал: https://t.me/KupuProxy\n" +
                    "https://github.com/${BuildConfig.GITHUB_REPO}"
            )
            .setPositiveButton("GitHub") { _, _ ->
                openUrl("https://github.com/${BuildConfig.GITHUB_REPO}")
            }
            .setNeutralButton(R.string.channel_open) { _, _ ->
                TelegramIntents.openTelegramChannel(this)
            }
            .setNegativeButton("О приложении") { _, _ ->
                startActivity(Intent(this, AboutActivity::class.java))
            }
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
