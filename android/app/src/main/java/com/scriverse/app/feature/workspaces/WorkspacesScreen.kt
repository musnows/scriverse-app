package com.scriverse.app.feature.workspaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scriverse.app.AppUiState
import com.scriverse.app.core.designsystem.SectionCard
import com.scriverse.app.core.designsystem.StatusPill
import com.scriverse.app.core.model.WorkspaceCompatibility
import com.scriverse.app.core.model.WorkspaceKind
import com.scriverse.app.core.model.WorkspaceProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspacesScreen(
    state: AppUiState,
    onSelect: (String) -> Unit,
    onCreateLocal: () -> Unit,
    onAddRemote: (String, String) -> Unit,
    onLogin: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showRemoteDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工作区") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("让每个故事有自己的边界", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "本机工作区在设备内运行 Scriverse；云端工作区使用 HTTPS Server。切换时不会混用登录态、缓存或离线副本。",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onCreateLocal) {
                        Icon(Icons.Outlined.Computer, null)
                        Text("新建本机", Modifier.padding(start = 8.dp))
                    }
                    FilledTonalButton(onClick = { showRemoteDialog = true }) {
                        Icon(Icons.Outlined.Add, null)
                        Text("连接云端", Modifier.padding(start = 8.dp))
                    }
                }
            }
            state.serverCheckMessage?.let { message ->
                item {
                    SectionCard("连接检查") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.isCheckingServer) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                            }
                            Text(message)
                        }
                    }
                }
            }
            item { Text("我的工作区", style = MaterialTheme.typography.titleLarge) }
            items(state.profiles, key = WorkspaceProfile::id) { profile ->
                WorkspaceRow(
                    profile = profile,
                    selected = state.selectedProfileId == profile.id,
                    onSelect = { onSelect(profile.id) },
                    onLogin = { onLogin(profile.id) },
                )
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }

    if (showRemoteDialog) {
        RemoteWorkspaceDialog(
            onDismiss = { showRemoteDialog = false },
            onConfirm = { name, origin ->
                showRemoteDialog = false
                onAddRemote(name, origin)
            },
        )
    }
}

@Composable
private fun WorkspaceRow(profile: WorkspaceProfile, selected: Boolean, onSelect: () -> Unit, onLogin: () -> Unit) {
    SectionCard(
        title = profile.name,
        action = {
            StatusPill(
                text = when (profile.compatibility) {
                    WorkspaceCompatibility.CHECKING -> "准备中"
                    WorkspaceCompatibility.COMPATIBLE -> "兼容"
                    WorkspaceCompatibility.ONLINE_ONLY -> "仅在线"
                    WorkspaceCompatibility.UPGRADE_REQUIRED -> "需升级"
                    WorkspaceCompatibility.INCOMPATIBLE -> "不兼容"
                    WorkspaceCompatibility.OFFLINE -> "离线"
                },
                emphasized = selected,
            )
        },
    ) {
        ListItem(
            headlineContent = { Text(if (profile.kind == WorkspaceKind.LOCAL) "本机 Scriverse runtime" else profile.origin.orEmpty()) },
            supportingContent = {
                Text(if (profile.kind == WorkspaceKind.LOCAL) "数据仅保存在 App 私有目录" else "凭据与离线副本按用户隔离")
            },
            leadingContent = {
                Icon(if (profile.kind == WorkspaceKind.LOCAL) Icons.Outlined.Computer else Icons.Outlined.Cloud, null)
            },
        )
        HorizontalDivider()
        OutlinedButton(onClick = onSelect, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(if (selected) "当前工作区" else "切换到此工作区")
        }
        if (profile.kind == WorkspaceKind.REMOTE && profile.userId == null) {
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("登录")
            }
        }
    }
}

@Composable
private fun RemoteWorkspaceDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("连接 Scriverse Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("保存前会检查 health、版本和原生协议。正式远端必须使用 HTTPS。")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("工作区名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = origin,
                    onValueChange = { origin = it },
                    label = { Text("https://server.example") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, origin) }, enabled = name.isNotBlank() && origin.isNotBlank()) {
                Text("检查并保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
