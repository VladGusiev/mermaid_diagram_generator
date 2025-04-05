package ui

import CACHE_SIZE
import DiagramViewModel
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import ui.components.*

@Preview
@Composable
fun App() {

    val viewModel = remember { DiagramViewModel() }
    val state = viewModel.state
    val imageCache = remember {
        object : LinkedHashMap<String, ImageBitmap>(10, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, ImageBitmap>): Boolean {
                return size > CACHE_SIZE // Limit cache size
            }
        }
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.dispose()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Text(
                    text = "Input Graph",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                UserInputField(state, viewModel)
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                VerticesListDisplay(state, viewModel)
            }
        }

        when {
            state.isLoading -> DiagramLoading()
            state.error != null -> ErrorDisplay(state)
            state.imageBytes != null -> GraphDisplay(state.imageBytes, imageCache)
            else -> Text(
                text = "Waiting for user input...",
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}
