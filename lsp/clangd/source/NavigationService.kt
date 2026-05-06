package com.wuxianggujun.tinaide.core.editorlsp

import com.wuxianggujun.tinaide.core.textengine.Position

interface NavigationService {
    suspend fun gotoDefinition(fileUri: String, position: Position): List<Location>
    suspend fun findReferences(fileUri: String, position: Position): List<Location>
    suspend fun gotoTypeDefinition(fileUri: String, position: Position): List<Location>
    suspend fun gotoImplementation(fileUri: String, position: Position): List<Location>
    suspend fun documentSymbols(fileUri: String): List<DocumentSymbol>
    suspend fun workspaceSymbol(query: String): List<WorkspaceSymbol>
}

data class Location(
    val fileUri: String,
    val range: Range
)

data class Range(
    val start: Position,
    val end: Position
)

data class DocumentSymbol(
    val name: String,
    val kind: SymbolKind,
    val range: Range,
    val selectionRange: Range,
    val children: List<DocumentSymbol> = emptyList()
)

data class WorkspaceSymbol(
    val name: String,
    val kind: SymbolKind,
    val location: Location
)

enum class SymbolKind {
    FILE,
    MODULE,
    NAMESPACE,
    PACKAGE,
    CLASS,
    METHOD,
    PROPERTY,
    FIELD,
    CONSTRUCTOR,
    ENUM,
    INTERFACE,
    FUNCTION,
    VARIABLE,
    CONSTANT,
    STRING,
    NUMBER,
    BOOLEAN,
    ARRAY,
    OBJECT,
    KEY,
    NULL,
    ENUM_MEMBER,
    STRUCT,
    EVENT,
    OPERATOR,
    TYPE_PARAMETER
}

