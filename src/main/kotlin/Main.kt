import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextField
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
import kotlinx.coroutines.*


data class DiagramState(
    val code: String = """flowchart TD""".trimIndent(),
    val imageBytes: ByteArray? = null,
    var isLoading: Boolean = false,
    val error: String? = null
) {
    fun copyWithLoading() = copy(isLoading = true, error = null)
    fun copyWithError(e: Throwable) = copy(isLoading = false, error = e.message)

//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (other !is DiagramState) return false
//
//        if (code != other.code) return false
//        if (imageBytes != null) {
//            if (other.imageBytes == null) return false
//            if (!imageBytes.contentEquals(other.imageBytes)) return false
//        } else if (other.imageBytes != null) return false
//        if (isLoading != other.isLoading) return false
//        if (error != other.error) return false
//
//        return true
//    }
//
//    override fun hashCode(): Int {
//        var result = code.hashCode()
//        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
//        result = 31 * result + isLoading.hashCode()
//        result = 31 * result + (error?.hashCode() ?: 0)
//        return result
//    }
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
    val imageCache = remember { mutableMapOf<String, ImageBitmap>() }

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

        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
        } else if (state.error != null) {
            Text("Error: ${state.error}")
        }

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

        if (!withContext(Dispatchers.IO) {
                process.waitFor(5, TimeUnit.SECONDS)
            }) {
            process.destroy()
            throw TimeoutException("Diagram generation timed out")
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

