# Graph Generator GUI Application using Mermaid

This project is a graphical user interface **(GUI)** application built in **Kotlin** using **Compose for Desktop** which is designed to generate and visualize graphs based on user text input.
Application supports only directed graph type.

## Table of Contents
- [Requirements](#requirements)
- [Getting Started](#getting-started)
- [How to use](#how-to-use)
  - [Graph Notation](#graph-notation)
  - [Other rules](#other-rules)
  - [Example](#example)
- [Features](#features)
  - [Error Handling](#error-handling)
  - [Performance Optimizations](#performance-optimizations)
- [Code Structure](#code-structure)
- [Common Issues](#common-issues)
- [Limitations](#limitations)

## Requirements
- This application is developed and tested using JDK 21.
- In order to run the application, user need mermaid CLI installed on their system.
You can install it using npm:

    ```bash
    npm install -g @mermaid-js/mermaid-cli
    ```
    more details can be found [here](https://github.com/mermaid-js/mermaid-cli)

# Getting Started
1. Ensure you have JDK 21 installed.
   - In case you have multiple JDK versions installed, you can specify the JDK version in the `gradle.properties` file:
    ```properties
    org.gradle.java.home=/path/to/desired/jdk
    ```
2. Install Mermaid CLI: `npm install -g @mermaid-js/mermaid-cli`
3. ```bash
    # Clone the repository
    git clone https://github.com/VladGusiev/mermaid_diagram_generator.git
    cd mermaid_diagram_generator/
    
    # Build the application
    ./gradlew build
    
    # Run the application
    ./gradlew run
    ```

# How to use
In this section will be described how to use the application.
## Graph Notation
Application uses a simple graph notation to define the graph structure. The notation is as follows:

`A -> B`
will create a simple directed graph with A as the source node and B as the target node.
```mermaid
flowchart TD
    A --> B

```
Also reversed arrow is supported:

`A <- B`
will create a simple directed graph with A as the target node and B as the source node.
```mermaid
flowchart TD
    B --> A
```

## Other rules
- Only one connection per input line is allowed.
- No other connection symbols besides `->` or `<-` are allowed.
- No whitespaces, special symbols or emojis are allowed in the vertices names

## Example
Input:
```
A -> B
A -> C
C <- B
B -> b
```
Output:

<p align="center">
    <img src="src/resources/example_screenshot.png" alt="example graph" width="700"/>
</p>

# Features
- User-friendly GUI for easy interaction.
- Automatic graph refresh based on user input.
- Responsive application even with large graphs.
- Shows all unique vertices in the graph.
    - Allows user to disable/enable vertices.
    - Search of vertices by name.
    - While toggling vertices, the graph will be updated automatically.
- Caching of most used graphs data for faster access.
- Error detection for invalid input and more.
- Zooming and panning of the graph.

## Error Handling
The application provides error messages when:
- Invalid connection syntax is used
- Vertices are improperly formatted
- The Mermaid CLI encounters issues processing the graph

## Performance Optimizations
- Diagram caching to prevent regenerating identical graphs
- Input debouncing to reduce processing during rapid typing

# Code Structure
- `src/main/kotlin/model` - Contains data classes and error handling models for the application.
- `src/main/kotlin/ui` - Contains the UI components and Compose functions for the GUI.
- `src/main/kotlin/DiagramGenerator.kt` 
  - This class handles the actual diagram generation process:
  - Creates temporary files for Mermaid input/output
  - Executes the Mermaid CLI with appropriate parameters
  - Handles CLI timeouts and errors gracefully
  - Returns the path to the generated PNG image
  - Includes utility to verify Mermaid CLI installation
- `src/main/kotlin/DiagramViewModel.kt` - Core business logic that manages application state, processes user input, and coordinates between the UI and diagram generation. It implements:
  - Parsing graph connections
  - Managing vertex states (active/inactive)
  - Caching generated diagrams for performance
  - Debouncing input to prevent excessive diagram generation
- `src/main/kotlin/Main.kt` - Application entry point that initializes the Compose UI and launches the application window.
- `src/main/test/kotlin` - Contains unit tests for the application logic.
- `src/main/test_graph.txt` - example of large graph.

# Common Issues

- **Cannot build the application** - Ensure you have the correct JDK version and mermaid CLI installed and configured correctly in PATH
- **Error: Mermaid CLI not found** - Ensure Mermaid CLI is properly installed and available in your PATH
- **Output graph file was not generated** - Check if the input format is correct. If correct, consider lowering the number of lines in the input or vertices displayed.

# Limitations
- Maximum number of lines processed for graph generation is **~500**.