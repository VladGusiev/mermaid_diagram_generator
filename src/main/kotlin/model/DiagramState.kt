package model

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