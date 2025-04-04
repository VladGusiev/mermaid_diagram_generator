import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.*

private const val DEBOUNCE_DELAY = 100L
private const val PROCESS_TIMEOUT = 5L
private const val CACHE_SIZE = 20

data class DiagramState(
    val verticesList: List<String> = mutableListOf(),
    val verticesStates: Map<String, Boolean> = mapOf(),
    val imageBytes: ByteArray? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val code: String
        get() = DiagramViewModel.buildMermaidCode(verticesList, verticesStates)
    fun copyWithLoading() = copy(isLoading = true, error = null)
    fun copyWithError(e: Throwable) = copy(isLoading = false, error = e.message)
}

class GraphSyntaxError (
    message: String,
    lineContent: String,
    lineNumber: Int
) : Exception("Error on line $lineNumber: $message\n$lineContent")

class DiagramViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var diagramJob: Job? = null
    private val diagramGenerator = DiagramGenerator()

    var state by mutableStateOf(DiagramState())
        private set

    companion object {
        fun buildMermaidCode(edges: List<String>, verticesStates: Map<String, Boolean>): String {
            return buildString {
                appendLine("flowchart TD")

                for ((index, edge) in edges.withIndex()) {
                    if (edge.isBlank()) continue

                    if (edge.contains("<->")) {
                        throw GraphSyntaxError("Bidirectional edges are not supported", edge, index + 1)
                    } else if (edge.contains("->") && edge.contains("<-")) {
                        throw GraphSyntaxError("Only one transition per line allowed", edge, index + 1)
                    }

                    try {
                        val rightArrowParts = extractVertices(edge, "->")
                        val leftArrowParts = extractVertices(edge, "<-")

                        val (source, target) = when {
                            rightArrowParts != null -> {
                                if (rightArrowParts[0].contains(" ") || rightArrowParts[1].contains(" ")) {
                                    throw GraphSyntaxError("Node names cannot contain spaces", edge, index + 1)
                                }
                                Pair(rightArrowParts[0], rightArrowParts[1])
                            }
                            leftArrowParts != null -> {
                                if (leftArrowParts[0].contains(" ") || leftArrowParts[1].contains(" ")) {
                                    throw GraphSyntaxError("Node names cannot contain spaces", edge, index + 1)
                                }
                                Pair(leftArrowParts[1], leftArrowParts[0])
                            }
                            else -> throw GraphSyntaxError("Expected '->' or '<-' were not found", edge, index + 1)
                        }

                        if (source.isEmpty() || target.isEmpty())
                            throw GraphSyntaxError("Source or target vertex is empty", edge, index + 1)

                        val sourceActive = verticesStates[source] ?: true
                        val targetActive = verticesStates[target] ?: true

                        if (sourceActive && targetActive) {
                            appendLine("$source --> $target")
                        }
                        else if (sourceActive && !targetActive) {
                            appendLine(source)
                        } else if (!sourceActive && targetActive) {
                            appendLine(target)
                        }
                    } catch (e: Exception) {
                        if (e is GraphSyntaxError) throw e
                        throw GraphSyntaxError("Invalid graph notation", edge, index + 1)
                    }
                }
            }
        }

        private fun extractVertices(edge: String, divider: String): List<String>? {
            val parts = edge.split(divider).map { it.trim() }
            return if (parts.size == 2) {
                parts
            } else null
        }

        fun getUniqueVertices(edges: List<String>): List<String> {
            val nodes = mutableSetOf<String>()

            for (edge in edges) {
                if (edge.isBlank()) continue

                try {

                    if (edge.contains("<->")) continue
                    else if (edge.contains("->") && edge.contains("<-")) continue

                    // Handle both -> and <- formats
                    val rightArrowParts = extractVertices(edge, "->")
                    val leftArrowParts = extractVertices(edge, "<-")

                    when {
                        rightArrowParts != null -> {
                            val source = rightArrowParts[0]
                            val target = rightArrowParts[1]

                            if (source.isNotEmpty() && !source.contains(" ")) nodes.add(source)
                            if (target.isNotEmpty() && !target.contains(" ")) nodes.add(target)
                        }
                        leftArrowParts != null -> {
                            val source = leftArrowParts[0]
                            val target = leftArrowParts[1]

                            if (source.isNotEmpty() && !source.contains(" ")) nodes.add(source)
                            if (target.isNotEmpty() && !target.contains(" ")) nodes.add(target)
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            return nodes.toList().sorted()
        }
    }

    fun toggleVertex(vertex: String, isActive: Boolean) {
        val currentVertices = state.verticesStates.toMutableMap()
        currentVertices[vertex] = isActive

        state = state.copy(verticesStates = currentVertices)

        try {
            val code = buildMermaidCode(state.verticesList, currentVertices)
            generateDiagramWithDebounce(code)
        } catch (e: Exception) {
            state = state.copy(
                verticesStates = currentVertices,
                error = e.message,
                isLoading = false
            )
        }
    }

    fun updateEdges(userEdges: String) {
        val lines = userEdges.split("\n")

        state = state.copy(
            verticesList = lines,
            error = null
        )
        
        try {
            // Get all unique valid vertices using the companion method
            val uniqueVertices = getUniqueVertices(lines)

            // Create a new state map that preserves existing toggle states
            // or defaults to true for new vertices
            val vertexStateMap = uniqueVertices.associateWith { vertex ->
                state.verticesStates[vertex] ?: true
            }

            state = state.copy(
                verticesStates = vertexStateMap
            )

            generateDiagramWithDebounce(state.code)
        } catch (e: GraphSyntaxError) {
            state = state.copy(error = e.message)
        } catch (e: Exception) {
            state = state.copy(error = "Error: ${e.message}")
        }
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
                state = state.copy(imageBytes = bytes, isLoading = false, error = null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                state = state.copyWithError(e)
            }
        }
    }

    // Add to DiagramViewModel
    fun bulkToggleVertices(vertices: List<String>, isActive: Boolean) {
        val currentVertices = state.verticesStates.toMutableMap()

        // Update all vertices at once
        vertices.forEach { vertex ->
            currentVertices[vertex] = isActive
        }

        state = state.copy(verticesStates = currentVertices)

        try {
            val code = buildMermaidCode(state.verticesList, currentVertices)
            generateDiagramWithDebounce(code)
        } catch (e: Exception) {
            state = state.copy(
                verticesStates = currentVertices,
                error = e.message,
                isLoading = false
            )
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
    // Calculate number of lines in the text
    val text = state.verticesList.joinToString("\n")
    val lineCount = text.count { it == '\n' } + 1

    // Create shared scroll state
    val scrollState = remember { ScrollState(0) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Line numbers column with fixed width to accommodate 3 digits
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(48.dp)
                .background(MaterialTheme.colors.surface.copy(alpha = 0.2f))
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Match the text field's padding
                Spacer(modifier = Modifier.height(18.dp))

                // Generate line numbers with consistent spacing
                for (i in 1..lineCount) {
                    Text(
                        text = i.toString().padStart(3, ' '),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, // Use monospace font
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                    )
                }

                // Extra padding at bottom for scrolling
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Text field with matching line height
        BasicTextField(
            value = text,
            onValueChange = { viewModel.updateEdges(it) },
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 18.dp),
            textStyle = MaterialTheme.typography.body2.copy(
                color = MaterialTheme.colors.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, // Match with line numbers
                lineHeight = 20.sp // Consistent line height
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.surface)
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "One edge per line:\nA->B\nB->C",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            lineHeight = 20.sp
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}


@Composable
private fun VerticesListDisplay(
    state: DiagramState,
    viewModel: DiagramViewModel
) {
    // Calculate vertices only when the list changes
    val uniqueVertices = remember(state.verticesList) {
        DiagramViewModel.getUniqueVertices(state.verticesList)
    }

    var searchQuery by remember { mutableStateOf("") }

    // Use virtualization for large lists
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Unique Vertices (${uniqueVertices.size})",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.weight(1f)
            )

            // Add buttons for bulk operations
            Button(
                onClick = {
                    viewModel.bulkToggleVertices(uniqueVertices, true)
                },
                modifier = Modifier.height(30.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Enable All", fontSize = 10.sp)
            }

            Button(
                onClick = {
                    viewModel.bulkToggleVertices(uniqueVertices, false)
                },
                modifier = Modifier.height(30.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Disable All", fontSize = 10.sp)
            }

            // Search field
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .width(120.dp)
                    .height(30.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                textStyle = MaterialTheme.typography.body2.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface
                ),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search",
                                fontSize = 11.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        // Use LazyVerticalGrid with fixed cell size for performance
        Surface(
            modifier = Modifier.fillMaxSize(),
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
        ) {
            val filteredVertices = if (searchQuery.isBlank()) {
                uniqueVertices
            } else {
                uniqueVertices.filter { it.contains(searchQuery, ignoreCase = true) }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5), // Fixed number prevents recomposition
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    count = filteredVertices.size,
                    key = { filteredVertices[it] } // Stable keys for better performance
                ) { index ->
                    val vertex = filteredVertices[index]
                    val isActive = state.verticesStates[vertex] == true
                    VertexToggle(
                        vertex = vertex,
                        isActive = isActive,
                        onToggle = viewModel::toggleVertex
                    )
                }
            }
        }
    }
}
@Composable
private fun VertexToggle (
    vertex: String,
    isActive: Boolean,
    onToggle: (String, Boolean) -> Unit
)  {
    Surface(
        modifier = Modifier.padding(4.dp),
        shape = MaterialTheme.shapes.small,
        color = if (isActive) MaterialTheme.colors.primary else Color.Gray,
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable { onToggle(vertex, !isActive) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = vertex,
                style = MaterialTheme.typography.body2
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
    val errorMessage = state.error ?: "Unknown error"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Error header
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
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
                text = errorMessage,
                color = Color(0xFFF87171)
            )
        }
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
                throw IOException("Output graph file was not created. Consider lowering number of vertices.")
            }

            return outputFile
        } catch (e: Exception) {
//            println("Error during diagram generation: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Graph Generator",
        state = rememberWindowState(
            width = 1100.dp,
            height = 800.dp
        )
    ) {
        App()
    }
}

