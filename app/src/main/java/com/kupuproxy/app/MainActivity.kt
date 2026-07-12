package com.kupuproxy.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kupuproxy.app.updater.GitHubRelease
import com.kupuproxy.app.updater.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class MainActivity : AppCompatActivity() {

    private lateinit var btnSurfboard: MaterialButton
    private lateinit var btnRussiaCard: MaterialCardView
    private lateinit var btnEuropeCard: MaterialCardView
    private lateinit var btnSupport: MaterialButton
    private lateinit var btnHelp: MaterialButton
    private lateinit var btnTheme: MaterialButton
    private lateinit var btnMergeAll: MaterialButton
    private lateinit var btnCheckFile: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvVersion: TextView

    private val client = OkHttpClient()
    private lateinit var updateChecker: UpdateChecker

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { startCheckFileActivity(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        updateChecker = UpdateChecker(this, client)
        initViews()
        setupClickListeners()
        setupVersion()
        checkForUpdates()
    }

    private fun applySavedTheme() {
        val themeMode = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_THEME, 0)
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun initViews() {
        btnSurfboard = findViewById(R.id.btn_surfboard)
        btnRussiaCard = findViewById(R.id.btn_russia_card)
        btnEuropeCard = findViewById(R.id.btn_europe_card)
        btnSupport = findViewById(R.id.btn_support)
        btnHelp = findViewById(R.id.btnHelp)
        btnTheme = findViewById(R.id.btnTheme)
        btnMergeAll = findViewById(R.id.btnMergeAll)
        btnCheckFile = findViewById(R.id.btnCheckFile)
        tvStatus = findViewById(R.id.statusText)
        tvVersion = findViewById(R.id.tvVersion)
    }

    private fun setupVersion() {
        tvVersion.text = try {
            "v${packageManager.getPackageInfo(packageName, 0).versionName}"
        } catch (_: Exception) {
            "v1.0.0"
        }
    }

    private fun setupClickListeners() {
        btnRussiaCard.setOnClickListener {
            startLoadingActivity(
                ProxyManager.SOURCES[0].url,
                ProxyManager.SOURCES[0].name,
                ProxyManager.SOURCES[0].prefix
            )
        }

        btnEuropeCard.setOnClickListener {
            startLoadingActivity(
                ProxyManager.SOURCES[1].url,
                ProxyManager.SOURCES[1].name,
                ProxyManager.SOURCES[1].prefix
            )
        }

        btnSurfboard.setOnClickListener {
            startLoadingActivity(
                ProxyManager.SOURCES[2].url,
                ProxyManager.SOURCES[2].name,
                ProxyManager.SOURCES[2].prefix
            )
        }

        btnMergeAll.setOnClickListener {
            startActivity(Intent(this, MergeProxiesActivity::class.java))
        }

        btnCheckFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("text/plain", "text/*"))
        }

        btnSupport.setOnClickListener {
            openUrl("https://github.com/${BuildConfig.GITHUB_REPO}")
        }

        btnHelp.setOnClickListener { showHelpDialog() }
        btnTheme.setOnClickListener { showThemeDialog() }
    }

    private fun startLoadingActivity(url: String, name: String, prefix: String) {
        tvStatus.text = "Загрузка: $name"
        startActivity(
            Intent(this, ProxyLoadingActivity::class.java).apply {
                putExtra(EXTRA_SOURCE_URL, url)
                putExtra(EXTRA_SOURCE_NAME, name)
                putExtra(EXTRA_URL_PREFIX, prefix)
            }
        )
    }

    private fun startCheckFileActivity(uri: Uri) {
        startActivity(
            Intent(this, CheckFileActivity::class.java).apply {
                putExtra(EXTRA_FILE_URI, uri)
            }
        )
    }

    private fun showThemeDialog() {
        val themes = arrayOf("Системная", "Светлая", "Тёмная")
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val current = prefs.getInt(KEY_THEME, 0)

        MaterialAlertDialogBuilder(this)
            .setTitle("Тема оформления")
            .setSingleChoiceItems(themes, current) { dialog, which ->
                prefs.edit().putInt(KEY_THEME, which).apply()
                AppCompatDelegate.setDefaultNightMode(
                    when (which) {
                        1 -> AppCompatDelegate.MODE_NIGHT_NO
                        2 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Справка")
            .setMessage(R.string.help_message)
            .setPositiveButton("GitHub") { _, _ ->
                openUrl("https://github.com/${BuildConfig.GITHUB_REPO}")
            }
            .setNeutralButton("Закрыть", null)
            .show()
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            try {
                val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
                val release = updateChecker.checkForUpdate(version)
                release?.let {
                    withContext(Dispatchers.Main) { showUpdateDialog(it) }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun showUpdateDialog(release: GitHubRelease) {
        val versionName = release.tagName.removePrefix("v")
        MaterialAlertDialogBuilder(this)
            .setTitle("Обновление v$versionName")
            .setMessage(release.changelog.ifBlank { "Доступна новая версия KupuProxy" })
            .setPositiveButton("Открыть релиз") { _, _ ->
                updateChecker.openReleasePage(release.htmlUrl)
            }
            .setNegativeButton("Позже", null)
            .show()
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    companion object {
        const val PREFS = "kupu_settings"
        const val KEY_THEME = "theme"
        const val EXTRA_SOURCE_URL = "source_url"
        const val EXTRA_SOURCE_NAME = "source_name"
        const val EXTRA_URL_PREFIX = "url_prefix"
        const val EXTRA_FILE_URI = "file_uri"
        const val EXTRA_PROXIES = "proxies_list"
    }
}
