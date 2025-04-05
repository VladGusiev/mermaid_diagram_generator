import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import model.GraphSyntaxError
import java.nio.file.Files
import model.DiagramState

class DiagramViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var diagramJob: Job? = null
    private val diagramGenerator = DiagramGenerator()

    private val diagramCache = object : LinkedHashMap<String, ByteArray>(10, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ByteArray>): Boolean {
            return size > CACHE_SIZE
        }
    }

    var state by mutableStateOf(DiagramState())
        private set

    companion object {
        fun buildMermaidCode(connections: List<String>, verticesStates: Map<String, Boolean>): String {
            return buildString {
                appendLine("flowchart TD")

                for ((index, connection) in connections.withIndex()) {
                    if (connection.isBlank()) continue

                    if (connection.contains("<->")) {
                        throw GraphSyntaxError("Bidirectional connections are not supported", connection, index + 1)
                    } else if (connection.contains("->") && connection.contains("<-")) {
                        throw GraphSyntaxError("Only one transition per line allowed", connection, index + 1)
                    }

                    try {
                        val rightArrowParts = extractVertices(connection, "->")
                        val leftArrowParts = extractVertices(connection, "<-")

                        val (source, target) = when {
                            rightArrowParts != null -> {
                                if (rightArrowParts[0].contains(" ") || rightArrowParts[1].contains(" ")) {
                                    throw GraphSyntaxError("Node names cannot contain spaces", connection, index + 1)
                                }
                                Pair(rightArrowParts[0], rightArrowParts[1])
                            }
                            leftArrowParts != null -> {
                                if (leftArrowParts[0].contains(" ") || leftArrowParts[1].contains(" ")) {
                                    throw GraphSyntaxError("Node names cannot contain spaces", connection, index + 1)
                                }
                                Pair(leftArrowParts[1], leftArrowParts[0])
                            }
                            else -> throw GraphSyntaxError("Expected '->' or '<-' were not found", connection, index + 1)
                        }

                        if (source.isEmpty() || target.isEmpty())
                            throw GraphSyntaxError("Source or target vertex is empty", connection, index + 1)

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
                        throw GraphSyntaxError("Invalid graph notation", connection, index + 1)
                    }
                }
            }
        }

        private fun extractVertices(connection: String, divider: String): List<String>? {
            val parts = connection.split(divider).map { it.trim() }
            return if (parts.size == 2) {
                parts
            } else null
        }

        fun getUniqueVertices(connections: List<String>): List<String> {
            val uniqueVertices = mutableSetOf<String>()

            for (connection in connections) {
                if (connection.isBlank()) continue

                try {

                    if (connection.contains("<->")) continue
                    else if (connection.contains("->") && connection.contains("<-")) continue

                    val rightArrowParts = extractVertices(connection, "->")
                    val leftArrowParts = extractVertices(connection, "<-")

                    when {
                        rightArrowParts != null -> addVertices(rightArrowParts, uniqueVertices)
                        leftArrowParts != null -> addVertices(leftArrowParts, uniqueVertices)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            return uniqueVertices.toList().sorted()
        }

        private fun addVertices(
            providedVertices: List<String>,
            verticesSet: MutableSet<String>
        ) {
            val source = providedVertices[0]
            val target = providedVertices[1]

            if (source.isNotEmpty() && !source.contains(" ")) verticesSet.add(source)
            if (target.isNotEmpty() && !target.contains(" ")) verticesSet.add(target)
        }
    }

    private fun updateDiagramWithStates(verticesStates: Map<String, Boolean>) {
        state = state.copy(verticesStates = verticesStates)

        try {
            val code = buildMermaidCode(state.verticesList, verticesStates)
            generateDiagramWithDebounce(code)
        } catch (e: Exception) {
            state = state.copy(error = e.message, isLoading = false)
        }
    }

    fun toggleVertex(vertex: String, isActive: Boolean) {
        val currentVertices = state.verticesStates.toMutableMap()
        currentVertices[vertex] = isActive

        updateDiagramWithStates(currentVertices)
    }

    fun updateEdges(userEdges: String) {
        val lines = userEdges.split("\n")

        state = state.copy(
            verticesList = lines,
            error = null
        )

        try {
            val uniqueVertices = getUniqueVertices(lines)

            // New state map that preserves existing toggle states
            // Defaults to true for new vertices
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
        diagramJob?.cancel()

        if (diagramCache.containsKey(code)) {
            state = state.copy(
                imageBytes = diagramCache[code],
                isLoading = false,
                error = null
            )
            return
        }


        diagramJob = viewModelScope.launch {
            delay(DEBOUNCE_DELAY)
            state = state.copyWithLoading()

            try {
                val diagramPath = withContext(Dispatchers.IO) {
                    diagramGenerator.generate(code)
                }
                val bytes = withContext(Dispatchers.IO) {
                    Files.readAllBytes(diagramPath)
                }

                diagramCache[code] = bytes

                state = state.copy(imageBytes = bytes, isLoading = false, error = null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                state = state.copyWithError(e)
            }
        }
    }

    fun bulkToggleVertices(vertices: List<String>, isActive: Boolean) {
        val currentVertices = state.verticesStates.toMutableMap()

        vertices.forEach { vertex ->
            currentVertices[vertex] = isActive
        }

        updateDiagramWithStates(currentVertices)
    }

    fun dispose() {
        diagramJob?.cancel()
        viewModelScope.cancel()
        diagramCache.clear()
    }

}