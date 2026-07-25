package com.kupuproxy.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kupuproxy.app.data.local.db.SourceEntity
import com.kupuproxy.app.data.source.UserCustomSourceStore
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import kotlinx.coroutines.launch

class UserSourcesActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = UserCustomSourceStore(this)
        setContent {
            KupuProxyTheme {
                val scope = rememberCoroutineScope()
                var items by remember { mutableStateOf<List<SourceEntity>>(emptyList()) }
                var showAdd by remember { mutableStateOf(false) }
                var name by remember { mutableStateOf("") }
                var url by remember { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    items = store.list()
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Мои источники") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        if (items.isEmpty()) {
                            item {
                                Text(
                                    "Добавьте URL списка прокси (txt/json). Работают без Telegram и участвуют в мега-скане.",
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        items(items, key = { it.id }) { s ->
                            ListItem(
                                headlineContent = { Text(s.name) },
                                supportingContent = { Text(s.url) },
                                trailingContent = {
                                    IconButton(onClick = {
                                        scope.launch {
                                            store.delete(s.id)
                                            items = store.list()
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (showAdd) {
                    AlertDialog(
                        onDismissRequest = { showAdd = false },
                        title = { Text("Новый источник") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Имя") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = url,
                                    onValueChange = { url = it },
                                    label = { Text("URL") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                if (url.isBlank()) {
                                    Toast.makeText(this@UserSourcesActivity, "URL пуст", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    store.add(name, url)
                                    items = store.list()
                                    showAdd = false
                                    name = ""
                                    url = ""
                                }
                            }) { Text("Сохранить") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAdd = false }) { Text("Отмена") }
                        }
                    )
                }
            }
        }
    }
}
