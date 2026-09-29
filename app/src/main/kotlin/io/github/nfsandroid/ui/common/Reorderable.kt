package io.github.nfsandroid.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex

/**
 * Rows that change places when dragged by their handle (the modifier [row] gets): a row passes its
 * neighbour once dragged past half of it. [onOrder] gets the whole new order at each change.
 */
@Composable
fun <T> Reorderable(items: List<T>, onOrder: (List<T>) -> Unit, row: @Composable (T, Modifier) -> Unit) {
    val order = remember { mutableStateListOf<T>().apply { addAll(items) } }
    LaunchedEffect(items) { if (order.toList() != items) order.apply { clear(); addAll(items) } }
    val heights = remember { mutableStateMapOf<T, Int>() }
    var dragged by remember { mutableStateOf<T?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val changed by rememberUpdatedState(onOrder)
    fun swap(from: Int, to: Int, by: Int) {
        order.add(to, order.removeAt(from))
        offset += by
        changed(order.toList())
    }
    Column {
        order.forEach { item ->
            key(item) {
                val handle = Modifier.pointerInput(item) {
                    detectDragGestures(
                        onDragStart = { dragged = item; offset = 0f },
                        onDragEnd = { dragged = null; offset = 0f },
                        onDragCancel = { dragged = null; offset = 0f },
                    ) { change, amount ->
                        change.consume()
                        offset += amount.y
                        val i = order.indexOf(item)
                        val below = order.getOrNull(i + 1)?.let { heights[it] ?: 0 } ?: 0
                        val above = order.getOrNull(i - 1)?.let { heights[it] ?: 0 } ?: 0
                        if (below > 0 && offset > below / 2f) swap(i, i + 1, -below)
                        else if (above > 0 && offset < -above / 2f) swap(i, i - 1, above)
                    }
                }
                val moving = item == dragged
                Box(
                    Modifier.onSizeChanged { heights[item] = it.height }.zIndex(if (moving) 1f else 0f)
                        .graphicsLayer { translationY = if (moving) offset else 0f },
                ) { row(item, handle) }
            }
        }
    }
}
