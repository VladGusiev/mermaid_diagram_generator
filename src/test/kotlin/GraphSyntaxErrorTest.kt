import model.GraphSyntaxError
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.*

class GraphSyntaxErrorTest {

    @Test
    fun `error message contains line number and content`() {
        val error = GraphSyntaxError("Invalid arrow format", "A<->B", 3)
        assertTrue(error.message?.contains("line 3") == true)
        assertTrue(error.message?.contains("A<->B") == true)
        assertTrue(error.message?.contains("Invalid arrow format") == true)
    }
}