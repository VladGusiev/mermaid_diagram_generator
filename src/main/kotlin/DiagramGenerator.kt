import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DiagramGenerator {

    companion object {

        suspend fun checkMermaidCliInstalled(): Pair<Boolean, String> {
            return try {
                val processBuilder = ProcessBuilder("mmdc", "--version")

                val process = withContext(Dispatchers.IO) {
                    processBuilder.redirectErrorStream(true).start()
                }

                val output = withContext(Dispatchers.IO) {
                    process.inputStream.bufferedReader().use { it.readText() }
                }

                val completed = withContext(Dispatchers.IO) {
                    process.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS)
                }

                if (completed && process.exitValue() == 0) {
                    Pair(true, output.trim())
                } else {
                    Pair(false, "Mermaid CLI check timed out")
                }
            } catch (e: Exception) {
                Pair(false, e.message ?: "Error checking Mermaid CLI")
            }
        }
    }

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
                "--scale", "20",
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
            e.printStackTrace()
            throw e
        }
    }
}