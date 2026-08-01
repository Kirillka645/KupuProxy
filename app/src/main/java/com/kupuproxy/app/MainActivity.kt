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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.kupuproxy.app.core.locale.AppLocaleManager
import com.kupuproxy.app.core.util.TelegramIntents
import com.kupuproxy.app.data.local.prefs.PromoPreferences
import com.kupuproxy.app.ui.AboutActivity
import com.kupuproxy.app.ui.AppearanceActivity
import com.kupuproxy.app.ui.SettingsActivity
import com.kupuproxy.app.ui.components.channel.ChannelPromoHost
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import com.kupuproxy.app.ui.theme.kupuSafeScreen
import com.kupuproxy.app.updater.ApkDownloader
import com.kupuproxy.app.updater.GitHubRelease
import com.kupuproxy.app.updater.UpdateCheckResult
import com.kupuproxy.app.updater.UpdateChecker
import com.kupuproxy.app.work.ProxyRescanWorker
import com.kupuproxy.app.work.UpdateCheckWorker
import java.io.File
import kotlinx.coroutines.launch
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
    private var statusText by mutableStateOf("")
    private var promoDismissed by mutableStateOf<Boolean?>(null)
    private var helpDialogVisible by mutableStateOf(false)
    private var updateRelease by mutableStateOf<GitHubRelease?>(null)
    private var updateCheckInProgress by mutableStateOf(false)
    private var updateCheckError by mutableStateOf<String?>(null)
    private var pendingUpdateFile: File? = null
    private var downloadProgress by mutableIntStateOf(-1)
    private var downloadError by mutableStateOf<String?>(null)

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(::startCheckFileActivity)
        }

    private fun getPrefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        AppLocaleManager.apply(AppLocaleManager.currentTag())
        super.onCreate(savedInstanceState)
        statusText = getString(R.string.home_ready)
        updateChecker = UpdateChecker(this, client)
        apkDownloader = ApkDownloader(this)
        promoPreferences = PromoPreferences(this)
        selectedProfile = savedProfileMode()

        setContent { KupuProxyTheme { HomeScreen() } }

        lifecycleScope.launch { promoDismissed = promoPreferences.isPromoDismissed() }
        if (!handleManualUpdateCheck(intent)) checkForUpdates()
        ProxyRescanWorker.schedule(this, com.kupuproxy.app.work.ProxyRefreshPreferences.load(this))
        UpdateCheckWorker.schedule(this)
        lifecycleScope.launch { ProxyCache.migrateCleanup(this@MainActivity) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleManualUpdateCheck(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshHomeState()
        pendingUpdateFile?.let { file ->
            if (apkDownloader.canInstallPackages() && apkDownloader.isVerifiedUpdateFile(file)) {
                pendingUpdateFile = null
                runCatching { apkDownloader.installApk(this, file) }
                    .onFailure {
                        downloadError =
                            it.message ?: getString(R.string.update_installer_launch_error)
                    }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HomeScreen() {
        val profileSettings = ProfileSettings.forMode(selectedProfile, this)
        val localizedHomeSources = homeSources()
        Scaffold(
            modifier = Modifier.kupuSafeScreen(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("KupuProxy", fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(
                                    R.string.home_version_network,
                                    BuildConfig.VERSION_NAME,
                                    networkLabel,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { helpDialogVisible = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = stringResource(R.string.help),
                            )
                        }
                        IconButton(
                            onClick = {
                                startActivity(
                                    Intent(this@MainActivity, AppearanceActivity::class.java)
                                )
                            }
                        ) {
                            Icon(
                                Icons.Default.DarkMode,
                                contentDescription = stringResource(R.string.theme),
                            )
                        }
                        IconButton(
                            onClick = {
                                startActivity(
                                    Intent(this@MainActivity, SettingsActivity::class.java)
                                )
                            }
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    HeroCard(
                        status = statusText,
                        profile = profileSettings.label,
                        onScan = { startScan(MODE_MEGA, getString(R.string.home_mega_scan)) },
                    )
                }
                item {
                    ProfileSelector(selectedProfile) { mode ->
                        selectedProfile = mode
                        getPrefs().edit().putInt(KEY_PROFILE, mode.preferenceValue()).apply()
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
                            },
                        )
                    }
                }
                item { KortCollectorCard(kortStatus) }
                item {
                    Card(
                        onClick = { openUrl("https://github.com/dubblebyte/free-mtproto-proxies") },
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Dubblebyte free MTProto", fontWeight = FontWeight.Bold)
                                Text(
                                    "github.com/dubblebyte/free-mtproto-proxies",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    SectionTitle(
                        stringResource(R.string.home_quick_start),
                        stringResource(R.string.home_quick_start_subtitle),
                    )
                }
                items(localizedHomeSources, key = HomeSource::id) { source ->
                    ActionCard(
                        icon = source.icon,
                        title = source.title,
                        subtitle = source.subtitle,
                        onClick = { startScan(MODE_SOURCE, source.title, source.id) },
                    )
                }
                item {
                    SectionTitle(
                        stringResource(R.string.home_offline_saved),
                        stringResource(R.string.home_offline_saved_subtitle),
                    )
                }
                item {
                    ActionCard(
                        Icons.Default.Download,
                        stringResource(R.string.home_seed_title),
                        stringResource(R.string.home_seed_summary, counts.seed, counts.cache),
                        onClick = {
                            startScan(MODE_SEED, getString(R.string.home_seed_scan_title))
                        },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompactAction(
                            Modifier.weight(1f),
                            Icons.Default.Wifi,
                            "Wi-Fi",
                            counts.wifi.toString(),
                        ) {
                            openSavedList(
                                NetworkProfileMode.WIFI,
                                getString(R.string.home_saved_wifi),
                            )
                        }
                        CompactAction(
                            Modifier.weight(1f),
                            Icons.Default.Smartphone,
                            "LTE",
                            counts.mobile.toString(),
                        ) {
                            openSavedList(
                                NetworkProfileMode.MOBILE,
                                getString(R.string.home_saved_mobile),
                            )
                        }
                        CompactAction(
                            Modifier.weight(1f),
                            Icons.Default.Star,
                            stringResource(R.string.home_favorites),
                            counts.favorites.toString(),
                        ) {
                            openFavorites()
                        }
                    }
                }
                item {
                    SectionTitle(
                        stringResource(R.string.home_tools),
                        stringResource(R.string.home_tools_subtitle),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToolButton(
                            Modifier.weight(1f),
                            Icons.AutoMirrored.Filled.MergeType,
                            stringResource(R.string.home_build_file),
                        ) {
                            startActivity(
                                Intent(this@MainActivity, MergeProxiesActivity::class.java)
                            )
                        }
                        ToolButton(
                            Modifier.weight(1f),
                            Icons.Default.FileOpen,
                            stringResource(R.string.home_check_file),
                        ) {
                            filePickerLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { openUrl("https://github.com/${BuildConfig.GITHUB_REPO}") },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.github))
                        }
                        OutlinedButton(
                            onClick = {
                                startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.about))
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        if (helpDialogVisible) HelpDialog()
        updateRelease?.let { UpdateDialog(it) }
        if (updateCheckInProgress) UpdateCheckDialog()
        updateCheckError?.let { error ->
            AlertDialog(
                onDismissRequest = { updateCheckError = null },
                title = { Text(stringResource(R.string.update_check_failed_title)) },
                text = { Text(error) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            updateCheckError = null
                            checkForUpdates(showResult = true)
                        }
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { updateCheckError = null }) {
                        Text(stringResource(R.string.close))
                    }
                },
            )
        }
        if (downloadProgress >= 0) DownloadDialog()
        downloadError?.let { error ->
            AlertDialog(
                onDismissRequest = { downloadError = null },
                title = { Text(stringResource(R.string.update_download_failed_title)) },
                text = { Text(error) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            downloadError = null
                            updateRelease?.let(::startApkDownloadAndInstall)
                        }
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { downloadError = null }) {
                        Text(stringResource(R.string.close))
                    }
                },
            )
        }
    }

    @Composable
    private fun HeroCard(status: String, profile: String, onScan: () -> Unit) {
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.home_proxy_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            status,
                            color =
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(profile) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    AssistChip(onClick = {}, label = { Text("MTProto handshake") })
                }
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.home_start_scan),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun KortCollectorCard(status: ProxyCache.KortStatus) {
        val age =
            if (status.refreshedAtMs <= 0) stringResource(R.string.kort_never_updated)
            else {
                val minutes =
                    ((System.currentTimeMillis() - status.refreshedAtMs).coerceAtLeast(0) / 60_000)
                when {
                    minutes < 60 -> stringResource(R.string.kort_minutes_ago, minutes)
                    minutes < 1_440 -> stringResource(R.string.kort_hours_ago, minutes / 60)
                    else -> stringResource(R.string.kort_days_ago, minutes / 1_440)
                }
            }
        Card(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Kort Verified Collector", fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(
                                R.string.kort_status,
                                if (status.isStale()) stringResource(R.string.kort_stale)
                                else stringResource(R.string.kort_updated, age),
                                status.proxyCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (status.isStale()) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                            Triple(stringResource(R.string.kort_all), "kort_verified", "all"),
                            Triple("RU", "kort_ru", "ru"),
                            Triple("EU", "kort_eu", "eu"),
                            Triple("US", "kort_us", "us"),
                            Triple(stringResource(R.string.kort_asia), "kort_asia", "asia"),
                        )
                        .forEach { (label, id, regionKey) ->
                            AssistChip(
                                onClick = { startScan(MODE_SOURCE, "Kort $label", id) },
                                label = {
                                    Text(
                                        "$label ${status.regionalCounts[regionKey].orEmptyCount(id == "kort_verified", status)}"
                                    )
                                },
                            )
                        }
                }
                status.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    private fun Int?.orEmptyCount(isAll: Boolean, status: ProxyCache.KortStatus): String {
        if (isAll) return status.proxyCount.takeIf { it > 0 }?.toString().orEmpty()
        return this?.takeIf { it > 0 }?.toString().orEmpty()
    }

    @Composable
    private fun ProfileSelector(
        selected: NetworkProfileMode,
        onSelected: (NetworkProfileMode) -> Unit,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.home_profile_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val profiles =
                    listOf(
                        NetworkProfileMode.AUTO to stringResource(R.string.profile_auto),
                        NetworkProfileMode.WIFI to stringResource(R.string.profile_wifi),
                        NetworkProfileMode.MOBILE to stringResource(R.string.profile_mobile),
                    )
                profiles.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = selected == mode,
                        onClick = { onSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, profiles.size),
                        label = { Text(label) },
                    )
                }
            }
            val settings = ProfileSettings.forMode(selected, this@MainActivity)
            Text(
                stringResource(
                    R.string.profile_limits_all,
                    settings.maxToCheck,
                    settings.batchSize,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun SectionTitle(title: String, subtitle: String) {
        Column(Modifier.padding(top = 6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun ActionCard(
        icon: ImageVector,
        title: String,
        subtitle: String,
        onClick: () -> Unit,
    ) {
        Card(onClick = onClick, shape = MaterialTheme.shapes.medium) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "›",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    private fun CompactAction(
        modifier: Modifier,
        icon: ImageVector,
        title: String,
        count: String,
        onClick: () -> Unit,
    ) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(
                    count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    private fun ToolButton(
        modifier: Modifier,
        icon: ImageVector,
        label: String,
        onClick: () -> Unit,
    ) {
        FilledTonalButton(onClick = onClick, modifier = modifier.height(52.dp)) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(label)
        }
    }

    @Composable
    private fun HelpDialog() {
        AlertDialog(
            onDismissRequest = { helpDialogVisible = false },
            icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
            title = { Text(stringResource(R.string.help_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.help_step_sources))
                    Text(stringResource(R.string.help_step_handshake))
                    Text(stringResource(R.string.help_step_profiles))
                    Text(stringResource(R.string.help_step_offline))
                    HorizontalDivider()
                    Text(stringResource(R.string.help_client_difference))
                }
            },
            confirmButton = {
                TextButton(onClick = { helpDialogVisible = false }) {
                    Text(stringResource(R.string.understood))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        helpDialogVisible = false
                        TelegramIntents.openTelegramChannel(this)
                    }
                ) {
                    Text(stringResource(R.string.channel))
                }
            },
        )
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure {
                Toast.makeText(this, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun startApkDownloadAndInstall(release: GitHubRelease) {
        downloadProgress = 0
        lifecycleScope.launch {
            try {
                val file =
                    apkDownloader.download(release, "KupuProxy-${release.tagName}.apk") { pct ->
                        downloadProgress = pct
                    }
                downloadProgress = 100
                if (!apkDownloader.canInstallPackages()) {
                    pendingUpdateFile = file
                    downloadProgress = -1
                    apkDownloader.openInstallPermissionSettings(this@MainActivity)
                    return@launch
                }
                apkDownloader.installApk(this@MainActivity, file)
                downloadProgress = -1
            } catch (_: Exception) {
                downloadProgress = -1
                downloadError = getString(R.string.update_download_error)
            }
        }
    }

    @Composable
    private fun UpdateDialog(release: GitHubRelease) {
        val hasApk = release.apkUrl.isNotBlank()
        AlertDialog(
            onDismissRequest = { updateRelease = null },
            title = { Text(stringResource(R.string.update_dialog_title, release.tagName)) },
            text = {
                Text(
                    release.changelog
                        .ifBlank { stringResource(R.string.update_available_default) }
                        .take(1800)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (hasApk) startApkDownloadAndInstall(release)
                        else {
                            updateRelease = null
                            updateChecker.openReleasePage(release.htmlUrl)
                        }
                    }
                ) {
                    Text(
                        stringResource(
                            if (hasApk) R.string.update_download_install
                            else R.string.update_open_github
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { updateRelease = null }) {
                    Text(stringResource(R.string.later))
                }
            },
        )
    }

    @Composable
    private fun UpdateCheckDialog() {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_check_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.update_check_message))
                }
            },
            confirmButton = {},
        )
    }

    @Composable
    private fun DownloadDialog() {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_verified_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (downloadProgress in 0..99) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.update_download_progress, downloadProgress))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.size(12.dp))
                            Text(stringResource(R.string.update_opening_installer))
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    private fun refreshHomeState() {
        val settings = ProfileSettings.forMode(selectedProfile, this)
        kortStatus = ProxyCache.loadKortStatus(this)
        networkLabel = "${ProfileSettings.currentLabel(this)} · ${settings.label}"
        counts =
            HomeCounts(
                wifi = ProxyCache.loadWorking(this, NetworkProfileMode.WIFI).size,
                mobile = ProxyCache.loadWorking(this, NetworkProfileMode.MOBILE).size,
                favorites = ProxyCache.getFavorites(this).size,
                seed = ProxyCache.loadSeedFromAssets(this).size,
                cache = ProxyCache.loadRawList(this).size,
            )
    }

    private fun applySavedTheme() {
        AppCompatDelegate.setDefaultNightMode(
            when (getPrefs().getInt(KEY_THEME, 0)) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun savedProfileMode(): NetworkProfileMode =
        when (getPrefs().getInt(KEY_PROFILE, 0)) {
            1 -> NetworkProfileMode.WIFI
            2 -> NetworkProfileMode.MOBILE
            else -> NetworkProfileMode.AUTO
        }

    private fun NetworkProfileMode.preferenceValue(): Int =
        when (this) {
            NetworkProfileMode.WIFI -> 1
            NetworkProfileMode.MOBILE -> 2
            NetworkProfileMode.AUTO -> 0
        }

    private fun startScan(mode: String, title: String, sourceId: String = "") {
        statusText = getString(R.string.home_starting, title)
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
            Toast.makeText(this, R.string.home_empty_saved, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, R.string.home_empty_favorites, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, ProxyListActivity::class.java).apply {
                putExtra(
                    EXTRA_PROXIES,
                    ArrayList(
                        urls.map { ProxyWithPing(it, 0, getString(R.string.home_favorites)) }
                    ),
                )
                putExtra(EXTRA_SOURCE_NAME, getString(R.string.home_favorites))
            }
        )
    }

    private fun startCheckFileActivity(uri: Uri) {
        startActivity(
            Intent(this, CheckFileActivity::class.java).apply {
                putExtra(EXTRA_FILE_URI, uri)
                putExtra(EXTRA_PROFILE, selectedProfile.name)
            }
        )
    }

    private fun handleManualUpdateCheck(intent: Intent): Boolean {
        if (!intent.getBooleanExtra(EXTRA_CHECK_UPDATES, false)) return false
        intent.removeExtra(EXTRA_CHECK_UPDATES)
        checkForUpdates(showResult = true)
        return true
    }

    private fun checkForUpdates(showResult: Boolean = false) {
        lifecycleScope.launch {
            if (showResult) updateCheckInProgress = true
            try {
                when (val result = updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        updateCheckError = null
                        updateRelease = result.release
                    }
                    UpdateCheckResult.UpToDate -> {
                        updateCheckError = null
                        if (showResult)
                            Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.update_latest_installed),
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                    }
                    is UpdateCheckResult.Failure -> {
                        if (showResult)
                            updateCheckError = getString(R.string.update_check_failed_title)
                    }
                }
            } finally {
                updateCheckInProgress = false
            }
        }
    }

    private data class HomeCounts(
        val wifi: Int = 0,
        val mobile: Int = 0,
        val favorites: Int = 0,
        val seed: Int = 0,
        val cache: Int = 0,
    )

    private data class HomeSource(
        val id: String,
        val title: String,
        val subtitle: String,
        val icon: ImageVector,
    )

    @Composable
    private fun homeSources(): List<HomeSource> =
        listOf(
            HomeSource(
                "solispirit",
                "SoliSpirit Mega",
                stringResource(R.string.home_source_solispirit_summary),
                Icons.Default.Public,
            ),
            HomeSource(
                "shablin_valid",
                "Shablin latency",
                stringResource(R.string.home_source_shablin_summary),
                Icons.Default.Speed,
            ),
            HomeSource(
                "dubblebyte",
                "Dubblebyte free MTProto",
                stringResource(R.string.home_source_dubblebyte_summary),
                Icons.Default.Public,
            ),
            HomeSource(
                "surfboard",
                "SurfboardV2ray",
                stringResource(R.string.home_source_surfboard_summary),
                Icons.Default.Speed,
            ),
            HomeSource(
                "argh94_scraper",
                "Argh94 Scraper",
                stringResource(R.string.home_source_argh94_summary),
                Icons.Default.Search,
            ),
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
        const val EXTRA_CHECK_UPDATES = "check_updates"
        const val MODE_MEGA = "mega"
        const val MODE_SOURCE = "source"
        const val MODE_SEED = "seed"
        const val MODE_CACHE = "cache"
    }
}
