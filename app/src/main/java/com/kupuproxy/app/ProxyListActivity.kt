package com.kupuproxy.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class ProxyListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var fabCopyTop10: ExtendedFloatingActionButton
    private lateinit var tvSubtitle: TextView

    private var proxiesList: List<ProxyWithPing> = emptyList()
    private var filteredList: List<ProxyWithPing> = emptyList()
    private var sourceName: String = ""
    private var maxPingFilter = Int.MAX_VALUE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_proxy_list)

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
        updateSubtitle()

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ProxyAdapter(this, filteredList)

        fabCopyTop10 = findViewById(R.id.fabCopyTop10)
        fabCopyTop10.setOnClickListener { copyTop10Proxies() }

        setupToolbarMenu()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
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
                recyclerView.adapter = ProxyAdapter(this, filteredList)
                updateSubtitle()
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
                    "https://github.com/${BuildConfig.GITHUB_REPO}"
            )
            .setPositiveButton("GitHub") { _, _ ->
                openUrl("https://github.com/${BuildConfig.GITHUB_REPO}")
            }
            .setNegativeButton("Закрыть", null)
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
