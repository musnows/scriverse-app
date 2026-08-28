package com.scriverse.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.scriverse.app.AppUiState
import com.scriverse.app.core.designsystem.SectionCard
import com.scriverse.app.core.designsystem.StatusPill

private data class SettingEntry(val title: String, val subtitle: String, val icon: ImageVector)

@Composable
fun AccountScreen(state: AppUiState, onOpenWorkspaces: () -> Unit) {
    val entries = listOf(
        SettingEntry("账号与安全", "头像、改密、API Key 与其他设备会话", Icons.Outlined.Person),
        SettingEntry("协作与审计", "成员、模块权限、在线状态与操作记录", Icons.Outlined.Group),
        SettingEntry("写作目标", "日目标、作品目标与进度提醒", Icons.Outlined.TrackChanges),
        SettingEntry("本地 AI", "设备供应商、模型、API Key 与连接测试", Icons.Outlined.Memory),
        SettingEntry("备份", "S3 加密备份、运行记录与恢复", Icons.Outlined.Backup),
        SettingEntry("显示设置", "主题、字号、编辑器和无障碍", Icons.Outlined.Palette),
        SettingEntry("诊断", "脱敏日志、数据库完整性与诊断包", Icons.Outlined.BugReport),
        SettingEntry("平台管理", "用户、模型、额度、定价与全局任务", Icons.Outlined.AdminPanelSettings),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("我的", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 18.dp))
            Text("账号、工作区与系统能力", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionCard("当前工作区", action = { StatusPill(state.selectedProfile?.kind?.name ?: "未选择") }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(state.selectedProfile?.name ?: "未选择", style = MaterialTheme.typography.titleLarge)
                        Text(state.selectedProfile?.origin ?: "本机私有目录")
                    }
                    TextButton(onClick = onOpenWorkspaces) {
                        Icon(Icons.Outlined.Workspaces, null)
                        Text("切换", Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        item {
            SectionCard("同步") {
                ListItem(
                    headlineContent = { Text("${state.sync.pendingCount} 项待同步") },
                    supportingContent = { Text("冲突 ${state.sync.conflictCount} · 被拒绝 ${state.sync.rejectedCount}") },
                    leadingContent = { Icon(Icons.Outlined.CloudSync, null) },
                )
            }
        }
        entries.forEach { entry ->
            item {
                SectionCard(entry.title) {
                    ListItem(
                        headlineContent = { Text(entry.title) },
                        supportingContent = { Text(entry.subtitle) },
                        leadingContent = { Icon(entry.icon, null) },
                    )
                }
            }
        }
    }
}
