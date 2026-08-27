package com.scriverse.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scriverse.app.AppUiState
import com.scriverse.app.core.designsystem.SectionCard
import com.scriverse.app.core.designsystem.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncCenterScreen(state: AppUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("同步中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard("同步状态", action = { StatusPill(state.sync.state.name, state.sync.pendingCount > 0) }) {
                if (state.sync.state.name == "SYNCING") LinearProgressIndicator(Modifier.fillMaxWidth())
                ListItem(
                    headlineContent = { Text("待同步 ${state.sync.pendingCount}") },
                    supportingContent = { Text("先拉取再幂等推送，遵循 Retry-After 与指数退避") },
                    leadingContent = { Icon(Icons.Outlined.Sync, null) },
                )
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("立即同步") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionCard("离线副本", Modifier.weight(1f)) {
                    Icon(Icons.Outlined.FileDownload, null)
                    Text("快照完成后单事务替换，失败保留旧副本。")
                }
                SectionCard("冲突", Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Merge, null)
                    Text("${state.sync.conflictCount} 项等待三方合并。")
                }
            }
            SectionCard("安全保护") {
                ListItem(
                    headlineContent = { Text("权限撤销后只读") },
                    supportingContent = { Text("本机修改保留为 rejected，可先导出 JSON 救援包再删除工作区。") },
                    leadingContent = { Icon(Icons.Outlined.ErrorOutline, null) },
                )
                ListItem(
                    headlineContent = { Text("本机事务完整") },
                    supportingContent = { Text("正文与 outbox 在同一事务写入。") },
                    leadingContent = { Icon(Icons.Outlined.CloudDone, null) },
                )
            }
        }
    }
}
