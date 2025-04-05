package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.DiagramState

@Composable
fun DiagramLoading() {
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
fun ErrorDisplay(state: DiagramState) {
    val errorMessage = state.error ?: "Unknown error"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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

@Composable
fun StartupLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Checking Mermaid CLI installation...", modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
fun MermaidNotInstalledError(
    mermaidCliStatus: Pair<Boolean, String>?,
    scope: CoroutineScope
) {
    var mermaidCliStatus1 = mermaidCliStatus
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Warning",
                tint = Color.Red,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Mermaid CLI (mmdc) is not installed or not working properly.",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )
            Text(
                "Please install it using: npm install -g @mermaid-js/mermaid-cli",
                modifier = Modifier.padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )
            Text(
                "Error: ${mermaidCliStatus1!!.second}",
                color = Color.Red,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = {
                    scope.launch {
                        mermaidCliStatus1 = DiagramGenerator.checkMermaidCliInstalled()
                    }
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Retry")
            }
        }
    }
}
