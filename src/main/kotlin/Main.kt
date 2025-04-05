import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.rememberWindowState
import ui.App
import ui.components.MermaidNotInstalledError
import ui.components.StartupLoading

const val DEBOUNCE_DELAY = 100L // Waiting duration before diagram generation
const val PROCESS_TIMEOUT = 10L // Timeout for process execution (diagram generation or mermaid check)
const val CACHE_SIZE = 20 // Maximum number of cached images

fun main() = application {
    var mermaidCliStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        mermaidCliStatus = DiagramGenerator.checkMermaidCliInstalled()
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Graph Generator",
        state = rememberWindowState(
            width = 1100.dp,
            height = 800.dp
        )
    ) {
        if (mermaidCliStatus == null) {
            StartupLoading()
        } else if (!mermaidCliStatus!!.first) {
            MermaidNotInstalledError(mermaidCliStatus, scope)
        } else {
            App()
        }
    }
}
