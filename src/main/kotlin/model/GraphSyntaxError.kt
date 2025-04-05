package model

class GraphSyntaxError (
    message: String,
    lineContent: String,
    lineNumber: Int
) : Exception("Error on line $lineNumber: $message\n$lineContent")