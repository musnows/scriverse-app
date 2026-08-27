package com.scriverse.app.feature.knowledge

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.scriverse.app.core.model.KnowledgeEntry
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RelationshipGraph(entries: List<KnowledgeEntry>, modifier: Modifier = Modifier) {
    val nodes = entries.take(8)
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(surface, RoundedCornerShape(18.dp))
            .pointerInput(nodes) {
                detectTransformGestures { _, panChange, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.7f, 2.4f)
                    pan += panChange
                }
            },
    ) {
        if (nodes.isEmpty()) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f) + pan
        val radius = minOf(size.width, size.height) * 0.34f * scale
        val positions = nodes.mapIndexed { index, _ ->
            val angle = (Math.PI * 2 * index / nodes.size) - Math.PI / 2
            center + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
        }
        positions.drop(1).forEach { point ->
            drawLine(primary.copy(alpha = 0.42f), center, point, strokeWidth = 3f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurface.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 13.dp.toPx()
        }
        positions.forEachIndexed { index, point ->
            drawCircle(primary, radius = 22.dp.toPx() * scale.coerceAtMost(1.35f), center = point)
            drawContext.canvas.nativeCanvas.drawText(
                nodes[index].title.take(7),
                point.x,
                point.y + 40.dp.toPx(),
                paint,
            )
        }
    }
}
