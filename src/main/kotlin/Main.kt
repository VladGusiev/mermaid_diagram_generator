//import androidx.compose.material3.*

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.skia.Image as SkiaImage


@Preview
@Composable
fun App() {
    var mermaidCode by remember { mutableStateOf(
        """
            flowchart TD
            A --> B
            A --> C
            B --> D
            C --> D
        """.trimIndent()
    ) }
    var diagramPath by remember { mutableStateOf<Path?>(null) }
    var regenerateCounter by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // Button to trigger diagram generation
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextField(
            value = mermaidCode,
            onValueChange = { mermaidCode = it },
            label = { Text("Mermaid Code") },
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )

        Button(onClick = {
            // First, save the mermaid code to the file
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    Files.createDirectories(Path.of("./src/mermaid"))
                    Files.writeString(Path.of("./src/mermaid/graph.mmd"), mermaidCode)

                    // Then generate the diagram
                    diagramPath = generateDiagram()
                    regenerateCounter++
                }
            }
        }) {
            Text("Generate Diagram")
        }

        diagramPath?.let { path ->
            if (Files.exists(path)) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Convert file to ImageBitmap
                    val imageBitmap = remember(path, regenerateCounter) {
                        val bytes = Files.readAllBytes(path)
                        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                    }

                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Diagram",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Text("Error: Image file not found")
            }
        } ?: Text("Click 'Generate Diagram' to create the diagram")
    }
}

private suspend fun generateDiagram(
    inputPath: String = "./src/mermaid/graph.mmd",
    outputPath: String = "./src/mermaid/diagram.png"
): Path {
    val output = Path.of(outputPath)

    // Create parent directories if they don't exist
    withContext(Dispatchers.IO) {
        Files.createDirectories(output.parent)
    }

    // Run the mermaid CLI command
    withContext(Dispatchers.IO) {
        ProcessBuilder("mmdc", "-i", inputPath, "-o", outputPath)
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }

    return output
}


fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Graph Generator") {
        App()
    }
}

