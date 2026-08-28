package com.scriverse.app.feature.writing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.scriverse.app.AppUiState
import com.scriverse.app.core.designsystem.StatusPill
import com.scriverse.app.core.model.Chapter

@Composable
fun WritingScreen(
    state: AppUiState,
    onSelectChapter: (String) -> Unit,
    onContentChange: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 800.dp) {
            Row(Modifier.fillMaxSize()) {
                ChapterList(
                    chapters = state.chapters,
                    selectedId = state.selectedChapterId,
                    onSelect = onSelectChapter,
                    modifier = Modifier.width(280.dp).fillMaxHeight(),
                )
                HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
                ChapterEditor(state, onContentChange, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                CompactChapterPicker(state.chapters, state.selectedChapterId, onSelectChapter)
                ChapterEditor(state, onContentChange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChapterList(
    chapters: List<Chapter>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("目录", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = {}) { Icon(Icons.Outlined.Add, "新建章节") }
            }
        }
        items(chapters, key = Chapter::id) { chapter ->
            Card(
                onClick = { onSelect(chapter.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedId == chapter.id) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(chapter.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${chapter.content.length} 字 · v${chapter.serverVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactChapterPicker(chapters: List<Chapter>, selectedId: String?, onSelect: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), userScrollEnabled = false) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    val current = chapters.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
                    onSelect(chapters[(current + 1) % chapters.size].id)
                }) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null)
                    Text(chapters.firstOrNull { it.id == selectedId }?.title ?: "选择章节", Modifier.padding(start = 8.dp))
                }
                IconButton(onClick = {}) { Icon(Icons.Outlined.MoreHoriz, "章节操作") }
            }
        }
    }
}

@Composable
private fun ChapterEditor(state: AppUiState, onContentChange: (String) -> Unit, modifier: Modifier) {
    val chapter = state.selectedChapter
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(chapter?.title ?: "未选择章节", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${chapter?.content?.length ?: 0} 字 · 本地修订 ${chapter?.localRevision ?: 0}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                StatusPill(state.saveState, state.sync.pendingCount > 0)
                FilledTonalIconButton(onClick = {}) { Icon(Icons.Outlined.FindReplace, "查找替换") }
                FilledTonalIconButton(onClick = {}) { Icon(Icons.Outlined.Compare, "版本差异") }
            }
        }
        OutlinedTextField(
            value = chapter?.content.orEmpty(),
            onValueChange = onContentChange,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 14.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("从这里开始写作") },
            enabled = chapter != null,
            supportingText = { Text("内容先保存到本机事务，再进入同步队列") },
        )
    }
}
