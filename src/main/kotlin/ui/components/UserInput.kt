package ui.components

import DiagramViewModel
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.DiagramState

@Composable
fun UserInputField(state: DiagramState, viewModel: DiagramViewModel) {
    // Calculate number of lines in the text
    val text = state.verticesList.joinToString("\n")
    val lineCount = text.count { it == '\n' } + 1

    // Shared scrolling for line numbers and text field
    val scrollState = remember { ScrollState(0) }

    Row(modifier = Modifier.fillMaxSize()) {
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

                for (i in 1..lineCount) {
                    Text(
                        text = i.toString().padStart(3, ' '),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                    )
                }

                // Extra padding at bottom for scrolling
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        BasicTextField(
            value = text,
            onValueChange = { viewModel.updateEdges(it) },
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 18.dp),
            textStyle = MaterialTheme.typography.body2.copy(
                color = MaterialTheme.colors.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                lineHeight = 20.sp
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
                            text = "One connection per line:\nA->B\nB<-C",
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