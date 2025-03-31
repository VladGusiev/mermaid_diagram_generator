import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.jetbrains.skia.Image as SkiaImage

data class DiagramState(
    val code: String = """
flowchart TD
A --> D
    """.trimIndent(),
    val imageBytes: ByteArray? = null,
    var isLoading: Boolean = false,
    val error: String? = null
) {
    fun copyWithLoading() = copy(isLoading = true, error = null)
    fun copyWithError(e: Throwable) = copy(isLoading = false, error = e.message)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiagramState) return false

        if (code != other.code) return false
        if (imageBytes != null) {
            if (other.imageBytes == null) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false
        } else if (other.imageBytes != null) return false
        if (isLoading != other.isLoading) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}

@Preview
@Composable
fun App() {
    var state by remember { mutableStateOf(DiagramState()) }

    LaunchedEffect(state.code) {
        if (state.code.isBlank()) return@LaunchedEffect

        delay(500)

        state = state.copyWithLoading()

        try {
            val diagramPath = withContext(Dispatchers.IO) {
                generateDiagram(state.code)
            }
            val bytes = withContext(Dispatchers.IO) { Files.readAllBytes(diagramPath)}
            state = state.copy(imageBytes = bytes, isLoading = false)
        } catch (e: Exception) {
            state = state.copyWithError(e)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextField(
            value = state.code,
            onValueChange = { state = state.copy(code = it) },
            label = { Text("Mermaid Code") },
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )

        if (state.isLoading) {
            Text("Generating diagram...")
        } else if (state.error != null) {
            Text("Error: ${state.error}")
        }

        state.imageBytes?.let { bytes ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val imageBitmap = remember(bytes) {
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
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
        Files.createTempDirectory("mermaid")
    }
    val inputFile = tempDir.resolve("input.mmd")
    val outputFile = tempDir.resolve("output.png")



    try {
        // Write to temp file
        withContext(Dispatchers.IO) {
            Files.writeString(inputFile, code)
        }

        // Execute with proper timeout and capture output
        val processBuilder = ProcessBuilder("mmdc", "-i", inputFile.toString(), "-o", outputFile.toString())

        val process = withContext(Dispatchers.IO) {
            processBuilder.redirectErrorStream(true).start()
        }
        val output = process.inputStream.bufferedReader().readText()

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

