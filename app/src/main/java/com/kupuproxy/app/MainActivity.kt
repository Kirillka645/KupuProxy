package com.kupuproxy.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kupuproxy.app.core.util.TelegramIntents
import com.kupuproxy.app.data.local.prefs.PromoPreferences
import com.kupuproxy.app.ui.AboutActivity
import com.kupuproxy.app.ui.SettingsActivity
import com.kupuproxy.app.ui.components.channel.ChannelPromoHost
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import com.kupuproxy.app.updater.ApkDownloader
import com.kupuproxy.app.updater.GitHubRelease
import com.kupuproxy.app.updater.UpdateChecker
import com.kupuproxy.app.work.ProxyRescanWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private lateinit var updateChecker: UpdateChecker
    private lateinit var apkDownloader: ApkDownloader
    private lateinit var promoPreferences: PromoPreferences

    private var selectedProfile by mutableStateOf(NetworkProfileMode.AUTO)
    private var networkLabel by mutableStateOf("")
    private var counts by mutableStateOf(HomeCounts())
    private var kortStatus by mutableStateOf(ProxyCache.KortStatus())
    private var statusText by mutableStateOf("Готов к поиску прокси")
    private var promoDismissed by mutableStateOf<Boolean?>(null)
    private var themeDialogVisible by mutableStateOf(false)
    private var helpDialogVisible by mutableStateOf(false)
    private var updateRelease by mutableStateOf<GitHubRelease?>(null)
    private var pendingUpdate: GitHubRelease? = null
    private var downloadProgress by mutableIntStateOf(-1)
    private var downloadError by mutableStateOf<String?>(null)

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::startCheckFileActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        updateChecker = UpdateChecker(this, client)
        apkDownloader = ApkDownloader(this)
        promoPreferences = PromoPreferences(this)
        selectedProfile = savedProfileMode()

        setContent {
            KupuProxyTheme {
                HomeScreen()
            }
        }

        lifecycleScope.launch { promoDismissed = promoPreferences.isPromoDismissed() }
        checkForUpdates()
        ProxyRescanWorker.schedule(this, com.kupuproxy.app.work.ProxyRefreshPreferences.load(this))
    }

    override fun onResume() {
        super.onResume()
        refreshHomeState()
        pendingUpdate?.let { release ->
            if (apkDownloader.canInstallPackages()) {
                pendingUpdate = null
                startApkDownloadAndInstall(release)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HomeScreen() {
        val profileSettings = ProfileSettings.forMode(selectedProfile, this)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("KupuProxy", fontWeight = FontWeight.Bold)
                            Text(
                                "v${BuildConfig.VERSION_NAME} · $networkLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { helpDialogVisible = true }) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Справка")
                        }
                        IconButton(onClick = { themeDialogVisible = true }) {
                            Icon(Icons.Default.DarkMode, contentDescription = "Тема")
                        }
                        IconButton(onClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    HeroCard(
                        status = statusText,
                        profile = profileSettings.label,
                        onScan = { startScan(MODE_MEGA, "Мега-скан") }
                    )
                }
                item {
                    ProfileSelector(selectedProfile) { mode ->
                        selectedProfile = mode
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putInt(KEY_PROFILE, mode.preferenceValue()).apply()
                        refreshHomeState()
                    }
                }
                if (promoDismissed == false) {
                    item {
                        ChannelPromoHost(
                            dismissed = false,
                            onDismissForever = {
                                promoDismissed = true
                                lifecycleScope.launch { promoPreferences.dismissPromoCard() }
                            }
                        )
                    }
                }
                item { KortCollectorCard(kortStatus) }
                item { SectionTitle("Быстрый старт", "Выберите один источник или запустите полный сбор") }
                items(homeSources, key = HomeSource::id) { source ->
                    ActionCard(
                        icon = source.icon,
                        title = source.title,
                        subtitle = source.subtitle,
                        onClick = { startScan(MODE_SOURCE, source.title, source.id) }
                    )
                }
                item { SectionTitle("Офлайн и сохранённое", "Результаты остаются доступны без сети") }
                item {
                    ActionCard(
                        Icons.Default.Download,
                        "Seed из APK",
                        "${counts.seed} встроенных · кэш ${counts.cache}",
                        onClick = { startScan(MODE_SEED, "Seed (офлайн APK)") }
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompactAction(
                            Modifier.weight(1f), Icons.Default.Wifi, "Wi-Fi", counts.wifi.toString()
                        ) { openSavedList(NetworkProfileMode.WIFI, "Последние Wi-Fi") }
                        CompactAction(
                            Modifier.weight(1f), Icons.Default.Smartphone, "LTE", counts.mobile.toString()
                        ) { openSavedList(NetworkProfileMode.MOBILE, "Последние LTE") }
                        CompactAction(
                            Modifier.weight(1f), Icons.Default.Star, "Избранное", counts.favorites.toString()
                        ) { openFavorites() }
                    }
                }
                item { SectionTitle("Инструменты", "Импорт, объединение и экспорт списков") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToolButton(Modifier.weight(1f), Icons.AutoMirrored.Filled.MergeType, "Собрать файл") {
                            startActivity(Intent(this@MainActivity, MergeProxiesActivity::class.java))
                        }
                        ToolButton(Modifier.weight(1f), Icons.Default.FileOpen, "Проверить файл") {
                            filePickerLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { openUrl("https://github.com/${BuildConfig.GITHUB_REPO}") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("GitHub")
                        }
                        OutlinedButton(
                            onClick = { startActivity(Intent(this@MainActivity, AboutActivity::class.java)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("О приложении")
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        if (themeDialogVisible) ThemeDialog()
        if (helpDialogVisible) HelpDialog()
        updateRelease?.let { UpdateDialog(it) }
        if (downloadProgress >= 0) DownloadDialog()
        downloadError?.let { error ->
            AlertDialog(
                onDismissRequest = { downloadError = null },
                title = { Text("Не удалось обновить") },
                text = { Text(error) },
                confirmButton = { TextButton(onClick = { downloadError = null }) { Text("Закрыть") } }
            )
        }
    }

    @Composable
    private fun HeroCard(status: String, profile: String, onScan: () -> Unit) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Рабочие MTProto-прокси", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(status, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(profile) }, leadingIcon = {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    })
                    AssistChip(onClick = {}, label = { Text("MTProto handshake") })
                }
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Запустить мега-скан", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun KortCollectorCard(status: ProxyCache.KortStatus) {
        val age = if (status.refreshedAtMs <= 0) {
            "ещё не обновлялся"
        } else {
            val minutes = ((System.currentTimeMillis() - status.refreshedAtMs).coerceAtLeast(0) / 60_000)
            when {
                minutes < 60 -> "$minutes мин назад"
                minutes < 1_440 -> "${minutes / 60} ч назад"
                else -> "${minutes / 1_440} дн назад"
            }
        }
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Kort Verified Collector", fontWeight = FontWeight.Bold)
                        Text(
                            "${if (status.isStale()) "Данные устарели" else "Обновлено $age"} · ${status.proxyCount} MTProto",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.isStale()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Все" to "kort_verified",
                        "RU" to "kort_ru",
                        "EU" to "kort_eu",
                        "US" to "kort_us",
                        "Asia" to "kort_asia"
                    ).forEach { (label, id) ->
                        AssistChip(
                            onClick = { startScan(MODE_SOURCE, "Kort $label", id) },
                            label = { Text("$label ${status.regionalCounts[label.lowercase()].orEmptyCount(label, status)}") }
                        )
                    }
                }
                status.error?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    private fun Int?.orEmptyCount(label: String, status: ProxyCache.KortStatus): String {
        if (label == "Все") return status.proxyCount.takeIf { it > 0 }?.toString().orEmpty()
        return this?.takeIf { it > 0 }?.toString().orEmpty()
    }

    @Composable
    private fun ProfileSelector(selected: NetworkProfileMode, onSelected: (NetworkProfileMode) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Профиль сети", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val profiles = listOf(
                    NetworkProfileMode.AUTO to "Авто",
                    NetworkProfileMode.WIFI to "Wi-Fi",
                    NetworkProfileMode.MOBILE to "LTE"
                )
                profiles.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = selected == mode,
                        onClick = { onSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, profiles.size),
                        label = { Text(label) }
                    )
                }
            }
            val settings = ProfileSettings.forMode(selected, this@MainActivity)
            Text(
                "До ${settings.maxToCheck} адресов · ${settings.batchSize} потоков · цель ${settings.stopWhenFound}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    private fun SectionTitle(title: String, subtitle: String) {
        Column(Modifier.padding(top = 6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun ActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
        Card(onClick = onClick, shape = RoundedCornerShape(18.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun CompactAction(modifier: Modifier, icon: ImageVector, title: String, count: String, onClick: () -> Unit) {
        Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun ToolButton(modifier: Modifier, icon: ImageVector, label: String, onClick: () -> Unit) {
        FilledTonalButton(onClick = onClick, modifier = modifier.height(52.dp)) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(label)
        }
    }

    @Composable
    private fun ThemeDialog() {
        val current = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_THEME, 0)
        val themes = listOf("Системная", "Светлая", "Тёмная")
        AlertDialog(
            onDismissRequest = { themeDialogVisible = false },
            title = { Text("Тема оформления") },
            text = {
                Column {
                    themes.forEachIndexed { index, title ->
                        TextButton(
                            onClick = {
                                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_THEME, index).apply()
                                AppCompatDelegate.setDefaultNightMode(
                                    when (index) {
                                        1 -> AppCompatDelegate.MODE_NIGHT_NO
                                        2 -> AppCompatDelegate.MODE_NIGHT_YES
                                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                                    }
                                )
                                themeDialogVisible = false
                                recreate()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (index == current) "✓ $title" else title, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    @Composable
    private fun HelpDialog() {
        AlertDialog(
            onDismissRequest = { helpDialogVisible = false },
            icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
            title = { Text("Как работает KupuProxy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("• Собирает списки из GitHub, CDN, зеркал и ваших HTTPS-источников.")
                    Text("• Проверяет настоящий MTProto handshake, а не только открытый TCP-порт.")
                    Text("• Профили Wi-Fi/LTE управляют параллелизмом, таймаутами и расходом батареи.")
                    Text("• Рабочие прокси, избранное, seed и кэш доступны офлайн.")
                    HorizontalDivider()
                    Text("Результат может отличаться от Telegram из-за DPI и блокировок конкретного клиента.")
                }
            },
            confirmButton = { TextButton(onClick = { helpDialogVisible = false }) { Text("Понятно") } },
            dismissButton = {
                TextButton(onClick = {
                    helpDialogVisible = false
                    TelegramIntents.openTelegramChannel(this)
                }) { Text("Канал") }
            }
        )
    }

    @Composable
    private fun UpdateDialog(release: GitHubRelease) {
        val hasApk = release.apkUrl.isNotBlank()
        AlertDialog(
            onDismissRequest = { updateRelease = null },
            title = { Text("Обновление ${release.tagName}") },
            text = { Text(release.changelog.ifBlank { "Доступна новая версия KupuProxy" }.take(1800)) },
            confirmButton = {
                TextButton(onClick = {
                    updateRelease = null
                    if (hasApk) {
                        if (!apkDownloader.canInstallPackages()) {
                            pendingUpdate = release
                            apkDownloader.openInstallPermissionSettings(this)
                        } else startApkDownloadAndInstall(release)
                    } else updateChecker.openReleasePage(release.htmlUrl)
                }) { Text(if (hasApk) "Скачать и установить" else "Открыть GitHub") }
            },
            dismissButton = { TextButton(onClick = { updateRelease = null }) { Text("Позже") } }
        )
    }

    @Composable
    private fun DownloadDialog() {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Проверенное обновление") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (downloadProgress in 0..99) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Скачивание и проверка SHA-256… $downloadProgress%")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.size(12.dp))
                            Text("Открываю установщик…")
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    private fun refreshHomeState() {
        val settings = ProfileSettings.forMode(selectedProfile, this)
        kortStatus = ProxyCache.loadKortStatus(this)
        networkLabel = "${ProfileSettings.currentLabel(this)} · ${settings.label}"
        counts = HomeCounts(
            wifi = ProxyCache.loadWorking(this, NetworkProfileMode.WIFI).size,
            mobile = ProxyCache.loadWorking(this, NetworkProfileMode.MOBILE).size,
            favorites = ProxyCache.getFavorites(this).size,
            seed = ProxyCache.loadSeedFromAssets(this).size,
            cache = ProxyCache.loadRawList(this).size
        )
    }

    private fun applySavedTheme() {
        AppCompatDelegate.setDefaultNightMode(
            when (getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_THEME, 0)) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun savedProfileMode(): NetworkProfileMode = when (
        getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_PROFILE, 0)
    ) {
        1 -> NetworkProfileMode.WIFI
        2 -> NetworkProfileMode.MOBILE
        else -> NetworkProfileMode.AUTO
    }

    private fun NetworkProfileMode.preferenceValue(): Int = when (this) {
        NetworkProfileMode.WIFI -> 1
        NetworkProfileMode.MOBILE -> 2
        NetworkProfileMode.AUTO -> 0
    }

    private fun startScan(mode: String, title: String, sourceId: String = "") {
        statusText = "Запуск: $title"
        startActivity(
            Intent(this, ProxyLoadingActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_SOURCE_NAME, title)
                putExtra(EXTRA_SOURCE_ID, sourceId)
                putExtra(EXTRA_PROFILE, selectedProfile.name)
            }
        )
    }

    private fun openSavedList(profile: NetworkProfileMode, title: String) {
        val list = ProxyCache.loadWorking(this, profile)
        if (list.isEmpty()) {
            Toast.makeText(this, "Список пуст — сначала запустите проверку", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, ProxyListActivity::class.java).apply {
            putExtra(EXTRA_PROXIES, ArrayList(list))
            putExtra(EXTRA_SOURCE_NAME, title)
        })
    }

    private fun openFavorites() {
        val urls = ProxyCache.getFavorites(this).toList()
        if (urls.isEmpty()) {
            Toast.makeText(this, "Избранное пусто", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, ProxyListActivity::class.java).apply {
            putExtra(EXTRA_PROXIES, ArrayList(urls.map { ProxyWithPing(it, 0, "Избранное") }))
            putExtra(EXTRA_SOURCE_NAME, "Избранное")
        })
    }

    private fun startCheckFileActivity(uri: Uri) {
        startActivity(Intent(this, CheckFileActivity::class.java).apply {
            putExtra(EXTRA_FILE_URI, uri)
            putExtra(EXTRA_PROFILE, selectedProfile.name)
        })
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            try {
                val release = updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
                if (release != null) updateRelease = release
            } catch (_: Exception) {
            }
        }
    }

    private fun startApkDownloadAndInstall(release: GitHubRelease) {
        downloadProgress = 0
        lifecycleScope.launch {
            try {
                val file = apkDownloader.download(release, "KupuProxy-${release.tagName}.apk") { pct ->
                    downloadProgress = pct
                }
                downloadProgress = 100
                if (!apkDownloader.canInstallPackages()) {
                    pendingUpdate = release
                    downloadProgress = -1
                    apkDownloader.openInstallPermissionSettings(this@MainActivity)
                    return@launch
                }
                apkDownloader.installApk(this@MainActivity, file)
                downloadProgress = -1
            } catch (error: Exception) {
                downloadProgress = -1
                downloadError = error.message ?: "Ошибка загрузки"
            }
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show() }
    }

    private data class HomeCounts(
        val wifi: Int = 0,
        val mobile: Int = 0,
        val favorites: Int = 0,
        val seed: Int = 0,
        val cache: Int = 0
    )

    private data class HomeSource(
        val id: String,
        val title: String,
        val subtitle: String,
        val icon: ImageVector
    )

    private val homeSources = listOf(
        HomeSource("solispirit", "SoliSpirit Mega", "Большой автообновляемый список", Icons.Default.Public),
        HomeSource("shablin_valid", "Shablin latency", "Живые MTProto, отсортированные по задержке", Icons.Default.Speed),
        HomeSource("dubblebyte", "Dubblebyte MTProto", "Дополнительный регулярно обновляемый список", Icons.Default.Public),
        HomeSource("surfboard", "SurfboardV2ray", "Основной и предварительно проверенный списки", Icons.Default.Speed),
        HomeSource("argh94_scraper", "Argh94 Scraper", "Агрегация публичных каналов", Icons.Default.Search),
        HomeSource("yagami200", "Yagami200 free", "TXT и JSON с регулярным обновлением", Icons.Default.Download)
    )

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
