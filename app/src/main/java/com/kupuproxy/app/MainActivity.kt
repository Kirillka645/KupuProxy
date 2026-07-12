package com.kupuproxy.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kupuproxy.app.updater.GitHubRelease
import com.kupuproxy.app.updater.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class MainActivity : AppCompatActivity() {

    private lateinit var btnHelp: MaterialButton
    private lateinit var btnTheme: MaterialButton
    private lateinit var btnMegaScan: MaterialButton
    private lateinit var btnSoli: MaterialCardView
    private lateinit var btnRussia: MaterialCardView
    private lateinit var btnEurope: MaterialCardView
    private lateinit var btnSurf: MaterialCardView
    private lateinit var btnOfflineSeed: MaterialButton
    private lateinit var btnLastWifi: MaterialButton
    private lateinit var btnLastMobile: MaterialButton
    private lateinit var btnFavorites: MaterialButton
    private lateinit var btnMergeAll: MaterialButton
    private lateinit var btnCheckFile: MaterialButton
    private lateinit var btnSupport: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvVersion: TextView
    private lateinit var tvNetworkNow: TextView
    private lateinit var tvProfileHint: TextView
    private lateinit var profileToggle: MaterialButtonToggleGroup

    private val client = OkHttpClient()
    private lateinit var updateChecker: UpdateChecker

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { startCheckFileActivity(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        updateChecker = UpdateChecker(this, client)
        initViews()
        setupProfileToggle()
        setupClickListeners()
        setupVersion()
        refreshNetworkLabel()
        checkForUpdates()
    }

    override fun onResume() {
        super.onResume()
        refreshNetworkLabel()
        updateOfflineButtons()
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
        btnHelp = findViewById(R.id.btnHelp)
        btnTheme = findViewById(R.id.btnTheme)
        btnMegaScan = findViewById(R.id.btnMegaScan)
        btnSoli = findViewById(R.id.btn_soli_card)
        btnRussia = findViewById(R.id.btn_russia_card)
        btnEurope = findViewById(R.id.btn_europe_card)
        btnSurf = findViewById(R.id.btn_surf_card)
        btnOfflineSeed = findViewById(R.id.btnOfflineSeed)
        btnLastWifi = findViewById(R.id.btnLastWifi)
        btnLastMobile = findViewById(R.id.btnLastMobile)
        btnFavorites = findViewById(R.id.btnFavorites)
        btnMergeAll = findViewById(R.id.btnMergeAll)
        btnCheckFile = findViewById(R.id.btnCheckFile)
        btnSupport = findViewById(R.id.btn_support)
        tvStatus = findViewById(R.id.statusText)
        tvVersion = findViewById(R.id.tvVersion)
        tvNetworkNow = findViewById(R.id.tvNetworkNow)
        tvProfileHint = findViewById(R.id.tvProfileHint)
        profileToggle = findViewById(R.id.profileToggle)
    }

    private fun setupVersion() {
        tvVersion.text = try {
            "v${packageManager.getPackageInfo(packageName, 0).versionName}"
        } catch (_: Exception) {
            "v1.1.0"
        }
    }

    private fun setupProfileToggle() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val saved = prefs.getInt(KEY_PROFILE, 0)
        profileToggle.check(
            when (saved) {
                1 -> R.id.chipWifi
                2 -> R.id.chipMobile
                else -> R.id.chipAuto
            }
        )
        updateProfileHint()

        profileToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.chipWifi -> 1
                R.id.chipMobile -> 2
                else -> 0
            }
            prefs.edit().putInt(KEY_PROFILE, mode).apply()
            updateProfileHint()
            refreshNetworkLabel()
        }
    }

    private fun currentProfileMode(): NetworkProfileMode {
        return when (getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_PROFILE, 0)) {
            1 -> NetworkProfileMode.WIFI
            2 -> NetworkProfileMode.MOBILE
            else -> NetworkProfileMode.AUTO
        }
    }

    private fun updateProfileHint() {
        val settings = ProfileSettings.forMode(currentProfileMode(), this)
        tvProfileHint.text = when (settings.mode) {
            NetworkProfileMode.MOBILE ->
                "LTE: до ${settings.maxToCheck} · ${settings.batchSize} параллельно · стоп на ${settings.stopWhenFound} рабочих"
            else ->
                "Wi‑Fi: до ${settings.maxToCheck} · ${settings.batchSize} параллельно · стоп на ${settings.stopWhenFound} рабочих"
        }
    }

    private fun refreshNetworkLabel() {
        tvNetworkNow.text = ProfileSettings.currentLabel(this) +
            " · профиль: ${ProfileSettings.forMode(currentProfileMode(), this).label}"
    }

    private fun updateOfflineButtons() {
        val wifi = ProxyCache.loadWorking(this, NetworkProfileMode.WIFI).size
        val mobile = ProxyCache.loadWorking(this, NetworkProfileMode.MOBILE).size
        val fav = ProxyCache.getFavorites(this).size
        val seed = ProxyCache.loadSeedFromAssets(this).size
        val cache = ProxyCache.loadRawList(this).size

        btnLastWifi.text = if (wifi > 0) "📶 Последние Wi‑Fi ($wifi)" else "📶 Последние Wi‑Fi (пусто)"
        btnLastMobile.text = if (mobile > 0) "📱 Последние LTE ($mobile)" else "📱 Последние LTE (пусто)"
        btnFavorites.text = if (fav > 0) "⭐ Избранное ($fav)" else "⭐ Избранное"
        btnOfflineSeed.text = "📦 Seed из APK (~$seed) · кэш $cache"
    }

    private fun setupClickListeners() {
        btnMegaScan.setOnClickListener {
            startScan(mode = MODE_MEGA, title = "Мега-скан")
        }

        btnSoli.setOnClickListener { startScan(MODE_SOURCE, "SoliSpirit Mega", "solispirit") }
        btnRussia.setOnClickListener { startScan(MODE_SOURCE, "Россия (Kort)", "kort_ru") }
        btnEurope.setOnClickListener { startScan(MODE_SOURCE, "Европа (Kort)", "kort_eu") }
        btnSurf.setOnClickListener { startScan(MODE_SOURCE, "SurfboardV2ray", "surfboard") }

        btnOfflineSeed.setOnClickListener {
            startScan(MODE_SEED, "Seed (офлайн APK)")
        }

        btnLastWifi.setOnClickListener {
            openSavedList(NetworkProfileMode.WIFI, "Последние Wi‑Fi")
        }
        btnLastMobile.setOnClickListener {
            openSavedList(NetworkProfileMode.MOBILE, "Последние LTE")
        }
        btnFavorites.setOnClickListener { openFavorites() }

        btnMergeAll.setOnClickListener {
            startActivity(Intent(this, MergeProxiesActivity::class.java))
        }

        btnCheckFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
        }

        btnSupport.setOnClickListener {
            openUrl("https://github.com/${BuildConfig.GITHUB_REPO}")
        }

        btnHelp.setOnClickListener { showHelpDialog() }
        btnTheme.setOnClickListener { showThemeDialog() }
    }

    private fun startScan(mode: String, title: String, sourceId: String = "") {
        tvStatus.text = "Загрузка: $title"
        startActivity(
            Intent(this, ProxyLoadingActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_SOURCE_NAME, title)
                putExtra(EXTRA_SOURCE_ID, sourceId)
                putExtra(EXTRA_PROFILE, currentProfileMode().name)
            }
        )
    }

    private fun openSavedList(profile: NetworkProfileMode, title: String) {
        val list = ProxyCache.loadWorking(this, profile)
        if (list.isEmpty()) {
            Toast.makeText(this, "Пока пусто — сначала запустите проверку", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, ProxyListActivity::class.java).apply {
                putExtra(EXTRA_PROXIES, ArrayList(list))
                putExtra(EXTRA_SOURCE_NAME, title)
            }
        )
    }

    private fun openFavorites() {
        val urls = ProxyCache.getFavorites(this).toList()
        if (urls.isEmpty()) {
            Toast.makeText(this, "Избранное пусто — нажмите ⭐ на прокси", Toast.LENGTH_SHORT).show()
            return
        }
        val list = urls.map { ProxyWithPing(it, 0, "Избранное") }
        startActivity(
            Intent(this, ProxyListActivity::class.java).apply {
                putExtra(EXTRA_PROXIES, ArrayList(list))
                putExtra(EXTRA_SOURCE_NAME, "Избранное")
            }
        )
    }

    private fun startCheckFileActivity(uri: Uri) {
        startActivity(
            Intent(this, CheckFileActivity::class.java).apply {
                putExtra(EXTRA_FILE_URI, uri)
                putExtra(EXTRA_PROFILE, currentProfileMode().name)
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
            .setTitle("Справка KupuProxy")
            .setMessage(
                "• Проверка как в Telegram: MTProxy handshake + req_pq → resPQ.\n" +
                    "  В списке только статус «Доступен» (мёртвые secret/порты отсекаются).\n\n" +
                    "• Мега-скан: 6+ источников через CDN-зеркала.\n\n" +
                    "• Профили Wi‑Fi / LTE — разный batch и таймаут.\n\n" +
                    "• Seed ~580 в APK + локальный кэш.\n\n" +
                    "• ⭐ избранное, фильтр пинга, шаринг.\n\n" +
                    "100% совпадение с Telegram не гарантируется (DPI/блокировки клиента), " +
                    "но отсев «открытый порт / мёртвый proxy» значительно лучше TCP-пинга."
            )
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
        MaterialAlertDialogBuilder(this)
            .setTitle("Обновление ${release.tagName}")
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
        const val KEY_PROFILE = "network_profile"
        const val EXTRA_SOURCE_URL = "source_url"
        const val EXTRA_SOURCE_NAME = "source_name"
        const val EXTRA_SOURCE_ID = "source_id"
        const val EXTRA_URL_PREFIX = "url_prefix"
        const val EXTRA_FILE_URI = "file_uri"
        const val EXTRA_PROXIES = "proxies_list"
        const val EXTRA_MODE = "scan_mode"
        const val EXTRA_PROFILE = "profile_mode"
        const val MODE_MEGA = "mega"
        const val MODE_SOURCE = "source"
        const val MODE_SEED = "seed"
        const val MODE_CACHE = "cache"
    }
}
