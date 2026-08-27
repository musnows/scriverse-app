package com.scriverse.app.feature.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scriverse.app.AppUiState
import com.scriverse.app.core.designsystem.SectionCard
import com.scriverse.app.core.model.KnowledgeEntry
import com.scriverse.app.core.model.KnowledgeType

@Composable
fun KnowledgeScreen(state: AppUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("设定库", style = MaterialTheme.typography.displaySmall)
                    Text("角色、世界与故事结构", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Search, "搜索设定") }
                    IconButton(onClick = {}) { Icon(Icons.Outlined.FilterList, "筛选设定") }
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Add, "新建设定") }
                }
            }
        }
        item {
            SectionCard("可视化与结构") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssistChip(onClick = {}, label = { Text("人物关系") }, leadingIcon = { Icon(Icons.Outlined.AccountTree, null) })
                    AssistChip(onClick = {}, label = { Text("时间轨道") })
                    AssistChip(onClick = {}, label = { Text("大纲看板") })
                }
                Text(
                    "关系图与银河图使用原生 Canvas / OpenGL 渲染，并提供无障碍列表降级。",
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RelationshipGraph(state.knowledge, Modifier.padding(top = 14.dp))
            }
        }
        items(state.knowledge, key = KnowledgeEntry::id) { entry -> KnowledgeCard(entry) }
    }
}

@Composable
private fun KnowledgeCard(entry: KnowledgeEntry) {
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.type.displayName(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(entry.title, style = MaterialTheme.typography.titleLarge)
            Text(entry.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(entry.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun KnowledgeType.displayName(): String = when (this) {
    KnowledgeType.IDEA -> "想法"
    KnowledgeType.SETTING -> "设定"
    KnowledgeType.CHARACTER -> "角色"
    KnowledgeType.RACE -> "种族"
    KnowledgeType.ORGANIZATION -> "组织"
    KnowledgeType.TIMELINE -> "时间线"
    KnowledgeType.OUTLINE -> "大纲"
    KnowledgeType.FORESHADOWING -> "伏笔"
    KnowledgeType.RELATIONSHIP -> "关系"
    KnowledgeType.ATTACHMENT -> "附件"
}
