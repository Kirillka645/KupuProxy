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
    private var sourceName: String = ""

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

        tvSubtitle = findViewById(R.id.tvListSubtitle)
        val avg = if (proxiesList.isNotEmpty()) {
            proxiesList.map { it.pingMs }.average().toInt()
        } else 0
        tvSubtitle.text = "${proxiesList.size} рабочих · средний пинг ${avg} ms"

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ProxyAdapter(this, proxiesList)

        fabCopyTop10 = findViewById(R.id.fabCopyTop10)
        fabCopyTop10.setOnClickListener { copyTop10Proxies() }

        setupToolbarMenu()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
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
                else -> false
            }
        }
    }

    private fun copyTop10Proxies() {
        val top = proxiesList.take(10)
        if (top.isEmpty()) {
            Toast.makeText(this, R.string.no_proxies, Toast.LENGTH_SHORT).show()
            return
        }
        copyToClipboard(formatWithFooter(top))
        Toast.makeText(this, "Скопировано ${top.size} прокси", Toast.LENGTH_SHORT).show()
    }

    private fun copyAllProxies() {
        if (proxiesList.isEmpty()) {
            Toast.makeText(this, R.string.no_proxies, Toast.LENGTH_SHORT).show()
            return
        }
        copyToClipboard(formatWithFooter(proxiesList))
        Toast.makeText(this, "Скопировано ${proxiesList.size} прокси", Toast.LENGTH_SHORT).show()
    }

    private fun formatWithFooter(proxies: List<ProxyWithPing>): String {
        val body = proxies.mapIndexed { i, p -> "${i + 1}. ${p.url}" }.joinToString("\n")
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
                "Парсер и проверка MTProto-прокси для Telegram.\n\n" +
                    "GitHub: https://github.com/${BuildConfig.GITHUB_REPO}"
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
