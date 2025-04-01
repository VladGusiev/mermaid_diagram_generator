import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.*


data class DiagramState(
    val code: String = """flowchart TD""".trimIndent(),
    val imageBytes: ByteArray? = null,
    var isLoading: Boolean = false,
    val error: String? = null
) {
    fun copyWithLoading() = copy(isLoading = true, error = null)
    fun copyWithError(e: Throwable) = copy(isLoading = false, error = e.message)
}
class DiagramViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var diagramJob: Job? = null

    var state by mutableStateOf(DiagramState())
        private set

    fun updateCode(newCode: String) {
        state = state.copy(code = newCode)
        generateDiagramWithDebounce(newCode)
    }

    private fun generateDiagramWithDebounce(code: String) {
        // Cancel previous job if it's still running
        diagramJob?.cancel()

        diagramJob = viewModelScope.launch {
            delay(500) // Debounce
            state = state.copyWithLoading()

            try {
                val diagramPath = withContext(Dispatchers.IO) {
                    generateDiagram(code)
                }
                val bytes = withContext(Dispatchers.IO) {
                    Files.readAllBytes(diagramPath)
                }
                state = state.copy(imageBytes = bytes, isLoading = false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                state = state.copyWithError(e)
            }
        }
    }

    fun dispose() {
        diagramJob?.cancel()
        viewModelScope.cancel()
    }
}

@Preview
@Composable
fun App() {
    val viewModel = remember { DiagramViewModel() }
    val state = viewModel.state
    val imageCache = remember {
        object : LinkedHashMap<String, ImageBitmap>(10, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, ImageBitmap>): Boolean {
                return size > 5 // Limit cache size
            }
        }
    }

    // Clean up resources when the composable leaves the composition
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.dispose()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextField(
            value = state.code,
            onValueChange = { viewModel.updateCode(it) },
            label = { Text("Mermaid Code") },
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )

        diagramLoading(state)

        state.imageBytes?.let { bytes ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val imageBitmap = remember(bytes.contentHashCode()) {
                    imageCache.getOrPut(bytes.contentHashCode().toString()) {
                        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                    }
                }

                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Diagram",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } ?: run {
            if (!state.isLoading && state.error == null) {
                Text("Type mermaid code to generate a diagram")
            }
        }
    }
}

private fun diagramLoading(state: DiagramState) {
    if (state.isLoading) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp,
                color = Color(0xFF3B82F6)
            )

            Text(
                text = "Generating diagram...",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.primary
            )

            LinearProgressIndicator(
                modifier = Modifier
                    .width(180.dp)
                    .padding(top = 8.dp),
                color = Color(0xFF3B82F6)
            )
        }
    }
    if (state.error != null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = Color(0xFFF87171),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Error: ${state.error}",
                color = Color(0xFFF87171)
            )
        }
    }
}

private suspend fun generateDiagram(code: String): Path {
    val tempDir = withContext(Dispatchers.IO) {
        Files.createTempDirectory("mermaid").also {
            it.toFile().deleteOnExit()  // Ensure cleanup on JVM exit
        }
    }
    val inputFile = tempDir.resolve("input.mmd")
    val outputFile = tempDir.resolve("output.png")

    try {
        // Write to temp file
        withContext(Dispatchers.IO) {
            Files.writeString(inputFile, code)
        }

        // Execute with proper timeout and capture output
        val processBuilder = ProcessBuilder(
            "mmdc",
            "-i", inputFile.toString(),
            "-o", outputFile.toString(),
            "--scale", "2",  // 2x resolution for retina displays
            "--backgroundColor", "transparent"
        )

        val process = withContext(Dispatchers.IO) {
            processBuilder.redirectErrorStream(true).start()
        }

        // Capture error output for better diagnostics
        val output = withContext(Dispatchers.IO) {
            process.inputStream.bufferedReader().use { it.readText() }
        }

        if (!withContext(Dispatchers.IO) {
                process.waitFor(5, TimeUnit.SECONDS)
            }) {
            process.destroy()
            throw TimeoutException("Diagram generation timed out. Output: $output")
        }

        if (!Files.exists(outputFile)) {
            throw IOException("Output file was not created at ${outputFile.toAbsolutePath()}")
        }

        return outputFile
    } catch (e: Exception) {
        println("Error during diagram generation: ${e.message}")
        e.printStackTrace()
        throw e
    }
}


fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Graph Generator") {
        App()
    }
}

