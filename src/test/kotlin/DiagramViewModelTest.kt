import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class DiagramViewModelTest {

    @Test
    fun `getUniqueVertices extracts vertices correctly from valid edges`() {
        val edges = listOf(
            "A->B",
            "B->C",
            "C->D",
            "D->A"
        )

        val result = DiagramViewModel.getUniqueVertices(edges)

        assertEquals(listOf("A", "B", "C", "D"), result)
    }

    @Test
    fun `getUniqueVertices handles empty lines`() {
        val edges = listOf(
            "A->B",
            "",
            "B->C",
            "",
            "C->D"
        )

        val result = DiagramViewModel.getUniqueVertices(edges)

        assertEquals(listOf("A", "B", "C", "D"), result)
    }

    @Test
    fun `getUniqueVertices handles left arrow notation`() {
        val edges = listOf(
            "A->B",
            "C<-D",
            "E->F"
        )

        val result = DiagramViewModel.getUniqueVertices(edges)

        assertEquals(listOf("A", "B", "C", "D", "E", "F"), result)
    }

    @Test
    fun `getUniqueVertices ignores invalid edge formats`() {
        val edges = listOf(
            "A->B",
            "Invalid line",
            "C<->D",
            "E->F<-G",
            "E->F->G",
            "R  <- G  <-J",
            "H->I"
        )

        val result = DiagramViewModel.getUniqueVertices(edges)

        assertEquals(listOf("A", "B", "H", "I"), result)
    }

    @Test
    fun `getUniqueVertices ignores vertices with spaces`() {
        val edges = listOf(
            "A->B",
            "C->D E",
            "F G->H"
        )

        val result = DiagramViewModel.getUniqueVertices(edges)

        assertEquals(listOf("A", "B", "C", "H"), result)
    }

    @Test
    fun `buildMermaidCode generates correct code for active vertices`() {
        val edges = listOf(
            "A->B",
            "B->C"
        )
        val verticesStates = mapOf(
            "A" to true,
            "B" to true,
            "C" to true
        )

        val result = DiagramViewModel.buildMermaidCode(edges, verticesStates)

        assertTrue(result.contains("flowchart TD"))
        assertTrue(result.contains("A --> B"))
        assertTrue(result.contains("B --> C"))
    }

    @Test
    fun `buildMermaidCode handles inactive source vertex`() {
        val edges = listOf(
            "A->B",
            "B->C"
        )
        val verticesStates = mapOf(
            "A" to false,
            "B" to true,
            "C" to true
        )

        val result = DiagramViewModel.buildMermaidCode(edges, verticesStates)
//        println(result)

        assertTrue(result.contains("flowchart TD"))
        assertFalse(result.contains("A --> B"))
        assertTrue(result.contains("B --> C"))
    }

    @Test
    fun `buildMermaidCode handles inactive target vertex`() {
        val edges = listOf(
            "A->B",
            "B->C"
        )
        val verticesStates = mapOf(
            "A" to true,
            "B" to true,
            "C" to false
        )

        val result = DiagramViewModel.buildMermaidCode(edges, verticesStates)

        assertTrue(result.contains("flowchart TD"))
        assertTrue(result.contains("A --> B"))
        assertTrue(result.contains("B"))
        assertFalse(result.contains("B --> C"))
    }

    @Test
    fun `buildMermaidCode throws exception for bidirectional edges`() {
        val edges = listOf("A<->B")
        val verticesStates = mapOf("A" to true, "B" to true)

        val exception = assertThrows<GraphSyntaxError> {
            DiagramViewModel.buildMermaidCode(edges, verticesStates)
        }

        assertTrue(exception.message?.contains("Bidirectional connections are not supported") == true)
    }

    @Test
    fun `buildMermaidCode throws exception for multiple transitions in line`() {
        val edges = listOf("A->B<-C")
        val verticesStates = mapOf("A" to true, "B" to true, "C" to true)

        val exception = assertThrows<GraphSyntaxError> {
            DiagramViewModel.buildMermaidCode(edges, verticesStates)
        }

        assertTrue(exception.message?.contains("Only one transition per line allowed") == true)
    }

    @Test
    fun `buildMermaidCode throws exception for vertices with spaces`() {
        val edges = listOf("A B->C")
        val verticesStates = mapOf("A B" to true, "C" to true)

        val exception = assertThrows<GraphSyntaxError> {
            DiagramViewModel.buildMermaidCode(edges, verticesStates)
        }

        assertTrue(exception.message?.contains("Node names cannot contain spaces") == true)
    }

    @Test
    fun `buildMermaidCode throws exception for empty vertices`() {
        val edges = listOf("->B")
        val verticesStates = mapOf("B" to true)

        val exception = assertThrows<GraphSyntaxError> {
            DiagramViewModel.buildMermaidCode(edges, verticesStates)
        }

        assertTrue(exception.message?.contains("Source or target vertex is empty") == true)
    }

    @Test
    fun `updateEdges preserves toggle states for existing vertices`() {
        val viewModel = DiagramViewModel()


        viewModel.updateEdges("A->B\nB->C")


        viewModel.toggleVertex("B", false)

        viewModel.updateEdges("A->B\nB->C\nC->D")

        // Verify state is preserved
        val state = viewModel.state
        assertTrue(state.verticesStates["A"] == true)
        assertTrue(state.verticesStates["B"] == false)
        assertTrue(state.verticesStates["C"] == true)
        assertTrue(state.verticesStates["D"] == true)
    }
}