package com.scriverse.app.feature.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.scriverse.app.AppUiState
import com.scriverse.app.core.designsystem.SectionCard
import com.scriverse.app.core.designsystem.StatusPill
import com.scriverse.app.core.model.WorkSummary

@Composable
fun ShelfScreen(state: AppUiState, onSelectWork: (String) -> Unit, onContinueWriting: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("书架", style = MaterialTheme.typography.displaySmall)
                    Text("继续今天的故事", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = {}) { Icon(Icons.Outlined.FileUpload, "导入作品") }
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Add, "创建作品") }
                }
            }
        }
        state.selectedWork?.let { work ->
            item {
                SectionCard("继续写作", action = { StatusPill(state.saveState, state.sync.pendingCount > 0) }) {
                    Text(work.title, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        state.selectedChapter?.title ?: "选择章节",
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onContinueWriting, modifier = Modifier.padding(top = 16.dp)) {
                        Text("打开编辑器")
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item { Text("全部作品", style = MaterialTheme.typography.titleLarge) }
        items(state.works, key = WorkSummary::id) { work ->
            WorkCard(work, selected = state.selectedWorkId == work.id, onClick = { onSelectWork(work.id) })
        }
        item { Box(Modifier.padding(bottom = 28.dp)) }
    }
}

@Composable
private fun WorkCard(work: WorkSummary, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.width(78.dp).aspectRatio(0.68f).clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(work.title.take(2), style = MaterialTheme.typography.headlineMedium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(work.title, style = MaterialTheme.typography.titleLarge)
                    Icon(Icons.Outlined.MoreVert, contentDescription = "作品菜单")
                }
                Text("${work.chapterCount} 章 · ${work.wordCount} 字 · ${work.role}")
                work.targetWordCount?.let { target ->
                    LinearProgressIndicator(
                        progress = { (work.wordCount.toFloat() / target).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = onClick) { Text("进入作品") }
            }
        }
    }
}
