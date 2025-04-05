import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.nio.file.Files

class DiagramGeneratorTest {

    private lateinit var diagramGenerator: DiagramGenerator

    @BeforeEach
    fun setup() {
        diagramGenerator = DiagramGenerator()
    }

    @Test
    fun `generate creates valid diagram for simple input`() = runTest {
        val code = """
            flowchart TD
            A --> B
            B --> C
        """.trimIndent()

        val result = diagramGenerator.generate(code)

        assertTrue(Files.exists(result))
        assertTrue(Files.size(result) > 0)
    }

    @Test
    fun `generate handles empty diagram`() = runTest {
        val code = "flowchart TD"

        val result = diagramGenerator.generate(code)
        assertTrue(Files.exists(result))
    }

    @Test
    // When the number of lines around 1000, graph will not be generated. (no File)
    fun `generate throws exception for very large diagram`() = runTest {
        val codeBuilder = StringBuilder("flowchart TD\n")
        for (i in 1..1000) {
            codeBuilder.append("Node$i --> Node${i+1}\n")
        }

        assertThrows<IOException> {
            diagramGenerator.generate(codeBuilder.toString())
        }
    }
}