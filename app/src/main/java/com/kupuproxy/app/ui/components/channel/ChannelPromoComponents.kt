package com.kupuproxy.app.ui.components.channel

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kupuproxy.app.R
import com.kupuproxy.app.core.Constants
import com.kupuproxy.app.core.util.QrEncoder
import com.kupuproxy.app.core.util.TelegramIntents
import com.kupuproxy.app.ui.theme.KupuProxyTheme

@Composable
fun ChannelPromoCard(
    onSubscribe: () -> Unit,
    onDismiss: () -> Unit,
    cardModifier: Modifier = Modifier
) {
    Card(
        modifier = cardModifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.channel_card_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.channel_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.channel_dismiss),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onSubscribe,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(stringResource(R.string.channel_subscribe))
            }
        }
    }
}

@Composable
fun EmptyStateWithChannel(
    onOpenChannel: () -> Unit,
    cardModifier: Modifier = Modifier
) {
    Column(
        modifier = cardModifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.channel_empty_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.channel_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        FilledTonalButton(onClick = onOpenChannel) {
            Text(stringResource(R.string.channel_open))
        }
    }
}

@Composable
fun ChannelSettingsListItem(
    onClick: () -> Unit,
    cardModifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.channel_settings_title)) },
        supportingContent = { Text(stringResource(R.string.channel_settings_sub)) },
        trailingContent = {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
        },
        modifier = cardModifier.fillMaxWidth().clickable(onClick = onClick)
    )
}

@Composable
fun AboutChannelSection(
    cardModifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    val qr: Bitmap = remember {
        QrEncoder.encode(Constants.TELEGRAM_CHANNEL_URL, 480)
    }
    Column(
        modifier = cardModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.channel_about_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = Constants.TELEGRAM_CHANNEL_URL,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onOpen)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Image(
            bitmap = qr.asImageBitmap(),
            contentDescription = stringResource(R.string.channel_qr_cd),
            modifier = Modifier.size(180.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onOpen) {
            Text(stringResource(R.string.channel_open))
        }
    }
}

@Composable
fun ChannelInviteDialog(
    onSubscribe: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Send, contentDescription = null) },
        title = { Text(stringResource(R.string.channel_invite_title)) },
        text = { Text(stringResource(R.string.channel_invite_body)) },
        confirmButton = {
            TextButton(onClick = onSubscribe) {
                Text(stringResource(R.string.channel_subscribe))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.later))
            }
        }
    )
}

@Composable
fun ChangelogInChannelRow(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(stringResource(R.string.channel_changelog))
    }
}

@Preview(showBackground = true)
@Composable
fun ChannelPromoCardPreview() {
    KupuProxyTheme {
        ChannelPromoCard(onSubscribe = {}, onDismiss = {})
    }
}

@Composable
fun ChannelPromoHost(
    dismissed: Boolean,
    onDismissForever: () -> Unit
) {
    val context = LocalContext.current
    if (dismissed) return
    ChannelPromoCard(
        onSubscribe = { TelegramIntents.openTelegramChannel(context) },
        onDismiss = onDismissForever,
        cardModifier = Modifier.padding(bottom = 4.dp)
    )
}
