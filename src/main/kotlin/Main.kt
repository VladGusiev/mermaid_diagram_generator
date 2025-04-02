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

private const val DEBOUNCE_DELAY = 500L
private const val PROCESS_TIMEOUT = 5L
private const val CACHE_SIZE = 20

data class DiagramState(
    val edgesList: List<String> = mutableListOf(),
    val imageBytes: ByteArray? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val code: String
        get() = DiagramViewModel.buildMermaidCode(edgesList)
    fun copyWithLoading() = copy(isLoading = true, error = null)
    fun copyWithError(e: Throwable) = copy(isLoading = false, error = e.message)
}

class DiagramViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var diagramJob: Job? = null
    private val diagramGenerator = DiagramGenerator()

    var state by mutableStateOf(DiagramState())
        private set

    companion object {
        fun buildMermaidCode(edges: List<String>): String {
            return buildString {
                appendLine("flowchart TD")

                for (edge in edges) {
                    if (edge.isBlank()) continue

                    val parts = edge.split("->")
                    if (parts.size != 2) continue

                    val source = parts[0].trim()
                    val target = parts[1].trim()

                    if (source.isEmpty() || target.isEmpty()) continue
                    appendLine("$source --> $target")
                }
            }
        }

        fun getUniqueVertices(edges: List<String>): List<String> {
            val nodes = mutableSetOf<String>()

            for (edge in edges) {
                if (edge.isBlank()) continue

                val parts = edge.split("->")
                if (parts.size != 2) continue

                val source = parts[0].trim()
                val target = parts[1].trim()

                if (source.isNotEmpty()) nodes.add(source)
                if (target.isNotEmpty()) nodes.add(target)
            }
            return nodes.toList().sorted()
        }

    }

    fun updateEdges(userEdges: String) {
        state = state.copy(
            edgesList = userEdges.split("\n")
        )
        generateDiagramWithDebounce(state.code)
    }

    private fun generateDiagramWithDebounce(code: String) {
        // Cancel previous job if it's still running
        diagramJob?.cancel()

        diagramJob = viewModelScope.launch {
            delay(DEBOUNCE_DELAY) // Debounce
            state = state.copyWithLoading()

            try {
                val diagramPath = withContext(Dispatchers.IO) {
                    diagramGenerator.generate(code)
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
                return size > CACHE_SIZE // Limit cache size
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

        UserInputField(state, viewModel)
        VerticesListDisplay(state)

        when  {
            state.isLoading -> DiagramLoading()
            state.error != null -> ErrorDisplay(state)
            state.imageBytes != null -> GraphDisplay(state.imageBytes, imageCache)
            else -> Text(
                text = "Type: A->B",
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun GraphDisplay(bytes: ByteArray, imageCache: LinkedHashMap<String, ImageBitmap>) {
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
}

@Composable
private fun UserInputField(state: DiagramState, viewModel: DiagramViewModel) {
    TextField(
        value = state.edgesList.joinToString("\n"),
        onValueChange = { viewModel.updateEdges(it) },
        label = { Text("Directed Graph") },
        placeholder = { Text("One edge per line:\nA->B\nB->C") },
        modifier = Modifier.fillMaxWidth().height(200.dp)
    )
}

@Composable
private fun VerticesListDisplay(state: DiagramState) {
    val uniqueVertices = remember(state.edgesList) {
        DiagramViewModel.getUniqueVertices(state.edgesList)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Unique Vertices",
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.primary
        )
    }
    Row (
        modifier = Modifier.fillMaxWidth().padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        uniqueVertices.forEach { vertex ->
            Text(
                text = vertex,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.primary
            )
        }
    }
}

@Composable
private fun DiagramLoading() {
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

@Composable
private fun ErrorDisplay(state: DiagramState) {
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

class DiagramGenerator {
    suspend fun generate(code: String): Path {
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

            val processBuilder = ProcessBuilder(
                "mmdc",
                "-i", inputFile.toString(),
                "-o", outputFile.toString(),
                "--scale", "2",
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
                    process.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS)
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
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Graph Generator") {
        App()
    }
}

