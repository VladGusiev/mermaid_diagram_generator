package ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image

@Composable
fun GraphDisplay(bytes: ByteArray, imageCache: LinkedHashMap<String, ImageBitmap>) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(Color.LightGray.copy(alpha = 0.2f))
                .clip(RectangleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offset += dragAmount
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val imageBitmap = remember(bytes.contentHashCode()) {
                imageCache.getOrPut(bytes.contentHashCode().toString()) {
                    Image.makeFromEncoded(bytes).toComposeImageBitmap()
                }
            }

            Image(
                bitmap = imageBitmap,
                contentDescription = "Diagram",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                        clip = true
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ZoomControls(
                scale = scale,
                onScaleChange = { newScale ->
                    scale = newScale
                },
                onReset = {
                    scale = 1f
                    offset = Offset.Zero
                }
            )
        }
    }
}

@Composable
private fun ZoomControls(scale: Float, onScaleChange: (Float) -> Unit, onReset: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Zoom: ${String.format("%.1f", scale)}x", modifier = Modifier.width(80.dp))

        Box(modifier = Modifier.weight(1f)) {
            Slider(
                value = scale,
                onValueChange = onScaleChange,
                valueRange = 0.5f..10f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(onClick = onReset) { Text("Reset View") }
    }
}