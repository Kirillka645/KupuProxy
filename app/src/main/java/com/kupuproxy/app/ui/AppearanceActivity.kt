package com.kupuproxy.app.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kupuproxy.app.R
import com.kupuproxy.app.ui.theme.AppearancePreferences
import com.kupuproxy.app.ui.theme.AppearanceSettings
import com.kupuproxy.app.ui.theme.ColorPalette
import com.kupuproxy.app.ui.theme.CornerStyle
import com.kupuproxy.app.ui.theme.FontScale
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import com.kupuproxy.app.ui.theme.ThemeMode
import com.kupuproxy.app.ui.theme.kupuImeAware
import com.kupuproxy.app.ui.theme.kupuSafeScreen
import com.kupuproxy.app.ui.theme.normalizeHexColor

class AppearanceActivity : AppCompatActivity() {
    private var settings by mutableStateOf(AppearanceSettings())
    private var customDialogVisible by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppearancePreferences.load(this)
        setContent {
            KupuProxyTheme(settingsOverride = settings) {
                AppearanceScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppearanceScreen() {
        Scaffold(
            modifier = Modifier.kupuSafeScreen(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.appearance_title)) },
                    navigationIcon = {
                        IconButton(onClick = ::finish) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                AppearancePreferences.reset(this@AppearanceActivity)
                                settings = AppearancePreferences.load(this@AppearanceActivity)
                            }
                        ) {
                            Text(stringResource(R.string.appearance_reset))
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item { PreviewCard() }
                item { SectionLabel(stringResource(R.string.appearance_mode)) }
                item {
                    ChoiceRow(
                        values = ThemeMode.entries,
                        selected = settings.themeMode,
                        label = { modeLabel(it) },
                        onSelected = { update(settings.copy(themeMode = it)) },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.appearance_dynamic)) },
                        supportingContent = {
                            Text(stringResource(R.string.appearance_dynamic_summary))
                        },
                        trailingContent = {
                            Switch(
                                checked = settings.dynamicColor,
                                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                onCheckedChange = { update(settings.copy(dynamicColor = it)) },
                            )
                        },
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
                item { SectionLabel(stringResource(R.string.appearance_palette)) }
                item {
                    ChoiceRow(
                        values = ColorPalette.entries,
                        selected = settings.palette,
                        label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                        onSelected = {
                            update(settings.copy(palette = it))
                            if (it == ColorPalette.CUSTOM) customDialogVisible = true
                        },
                    )
                }
                if (settings.palette == ColorPalette.CUSTOM) {
                    item {
                        Button(
                            onClick = { customDialogVisible = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        ) {
                            Text(stringResource(R.string.appearance_edit_colors))
                        }
                    }
                }
                item { SectionLabel(stringResource(R.string.appearance_corners)) }
                item {
                    ChoiceRow(
                        values = CornerStyle.entries,
                        selected = settings.cornerStyle,
                        label = { cornerLabel(it) },
                        onSelected = { update(settings.copy(cornerStyle = it)) },
                    )
                }
                item { SectionLabel(stringResource(R.string.appearance_text_size)) }
                item {
                    ChoiceRow(
                        values = FontScale.entries,
                        selected = settings.fontScale,
                        label = { fontScaleLabel(it) },
                        onSelected = { update(settings.copy(fontScale = it)) },
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.appearance_saved_automatically),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        if (customDialogVisible) CustomColorDialog()
    }

    @Composable
    private fun PreviewCard() {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.appearance_preview_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.appearance_preview_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = {}) { Text(stringResource(R.string.appearance_preview_button)) }
            }
        }
    }

    @Composable
    private fun SectionLabel(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        )
    }

    @Composable
    private fun <T> ChoiceRow(
        values: List<T>,
        selected: T,
        label: @Composable (T) -> String,
        onSelected: (T) -> Unit,
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(values) { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    label = { Text(label(value)) },
                )
            }
        }
    }

    @Composable
    private fun CustomColorDialog() {
        var primary by remember(settings.customPrimary) { mutableStateOf(settings.customPrimary) }
        var accent by remember(settings.customAccent) { mutableStateOf(settings.customAccent) }
        val normalizedPrimary = normalizeHexColor(primary)
        val normalizedAccent = normalizeHexColor(accent)
        val primaryValid = normalizedPrimary != null
        val accentValid = normalizedAccent != null
        AlertDialog(
            modifier = Modifier.kupuImeAware(),
            onDismissRequest = { customDialogVisible = false },
            title = { Text(stringResource(R.string.appearance_custom_colors)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.appearance_hex_hint))
                    OutlinedTextField(
                        value = primary,
                        onValueChange = { primary = it.take(7) },
                        label = { Text(stringResource(R.string.appearance_primary_color)) },
                        isError = !primaryValid,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = accent,
                        onValueChange = { accent = it.take(7) },
                        label = { Text(stringResource(R.string.appearance_accent_color)) },
                        isError = !accentValid,
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = primaryValid && accentValid,
                    onClick = {
                        update(
                            settings.copy(
                                palette = ColorPalette.CUSTOM,
                                customPrimary = normalizedPrimary ?: settings.customPrimary,
                                customAccent = normalizedAccent ?: settings.customAccent,
                            )
                        )
                        customDialogVisible = false
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { customDialogVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    private fun update(newSettings: AppearanceSettings) {
        settings = newSettings
        AppearancePreferences.save(this, newSettings)
    }

    @Composable
    private fun modeLabel(mode: ThemeMode): String =
        stringResource(
            when (mode) {
                ThemeMode.SYSTEM -> R.string.theme_system
                ThemeMode.LIGHT -> R.string.theme_light
                ThemeMode.DARK -> R.string.theme_dark
            }
        )

    @Composable
    private fun cornerLabel(style: CornerStyle): String =
        stringResource(
            when (style) {
                CornerStyle.COMPACT -> R.string.appearance_corner_compact
                CornerStyle.BALANCED -> R.string.appearance_corner_balanced
                CornerStyle.ROUND -> R.string.appearance_corner_round
            }
        )

    @Composable
    private fun fontScaleLabel(scale: FontScale): String =
        stringResource(
            when (scale) {
                FontScale.SMALL -> R.string.appearance_text_small
                FontScale.NORMAL -> R.string.appearance_text_normal
                FontScale.LARGE -> R.string.appearance_text_large
                FontScale.EXTRA_LARGE -> R.string.appearance_text_extra_large
            }
        )
}
