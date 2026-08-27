package com.scriverse.app.feature.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scriverse.app.AppUiState
import com.scriverse.app.core.designsystem.StatusPill
import com.scriverse.app.core.model.AiMessage
import com.scriverse.app.core.model.AiMessageRole

@Composable
fun AiScreen(state: AppUiState) {
    val conversation = state.conversations.firstOrNull()
    var prompt by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(conversation?.title ?: "AI 工作台", style = MaterialTheme.typography.headlineMedium)
                Text("原生流式消息、上下文与工具调用", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(conversation?.model ?: "选择模型", emphasized = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("模型") }, leadingIcon = { Icon(Icons.Outlined.ModelTraining, null) })
            AssistChip(onClick = {}, label = { Text("上下文") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) })
            AssistChip(onClick = {}, label = { Text("分析任务") })
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(conversation?.messages.orEmpty(), key = AiMessage::id) { message -> MessageCard(message) }
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            placeholder = { Text("询问、续写或启动分析任务") },
            leadingIcon = { IconButton(onClick = {}) { Icon(Icons.Outlined.AddPhotoAlternate, "添加图片") } },
            trailingIcon = { IconButton(onClick = { prompt = "" }, enabled = prompt.isNotBlank()) { Icon(Icons.AutoMirrored.Outlined.Send, "发送") } },
            minLines = 2,
            maxLines = 5,
        )
    }
}

@Composable
private fun MessageCard(message: AiMessage) {
    val isUser = message.role == AiMessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.96f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isUser) "你" else "叙界 AI", style = MaterialTheme.typography.labelLarge)
                Text(message.content)
                if (message.citations.isNotEmpty()) {
                    Text("引用：${message.citations.joinToString()}", style = MaterialTheme.typography.bodySmall)
                }
                message.toolName?.let { Text("工具：$it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
