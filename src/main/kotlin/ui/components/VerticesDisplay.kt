package ui.components

import DiagramViewModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.DiagramState

@Composable
fun VerticesListDisplay(
    state: DiagramState,
    viewModel: DiagramViewModel
) {
    // Calculate vertices only when the list changes
    val uniqueVertices = remember(state.verticesList) {
        DiagramViewModel.getUniqueVertices(state.verticesList)
    }

    var searchQuery by remember { mutableStateOf("") }


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

        // Fixed cell size
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
                    key = { filteredVertices[it] } // Stable keys
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
