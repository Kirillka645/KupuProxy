package com.kupuproxy.app.ui

import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kupuproxy.app.ProxyManager
import com.kupuproxy.app.R
import com.kupuproxy.app.core.util.QrEncoder
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import com.kupuproxy.app.ui.theme.kupuImeAware
import com.kupuproxy.app.ui.theme.kupuSafeScreen

class QrToolsActivity : AppCompatActivity() {
    private var value by mutableStateOf("")
    private var error by mutableStateOf<String?>(null)
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
            else @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }.getOrNull()
        val decoded = bitmap?.let(QrEncoder::decode)
        if (decoded != null && ProxyManager.parseProxyUrl(decoded) != null) {
            value = decoded
            error = null
        } else error = getString(R.string.qr_invalid)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KupuProxyTheme {
                Scaffold(
                    modifier = Modifier.kupuSafeScreen(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.qr_title)) },
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back),
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).kupuImeAware()
                            .verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it; error = null },
                            label = { Text(stringResource(R.string.qr_proxy_link)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                        )
                        error?.let { Text(it) }
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.qr_import_image)) }
                        if (ProxyManager.parseProxyUrl(value) != null) {
                            Image(
                                bitmap = QrEncoder.encode(value).asImageBitmap(),
                                contentDescription = stringResource(R.string.qr_title),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value))) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.connect)) }
                            OutlinedButton(
                                onClick = {
                                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, value)
                                    }, getString(R.string.share)))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.share)) }
                        }
                    }
                }
            }
        }
    }
}
