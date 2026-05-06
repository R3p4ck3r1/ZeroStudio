package com.wuxianggujun.tinaide.core.editorlsp

interface SemanticTokensProvider {
    suspend fun requestSemanticTokens(fileUri: String, visibleRange: IntRange): List<SemanticToken>
}

data class SemanticToken(
    val line: Int,
    val startColumn: Int,
    val length: Int,
    val tokenType: String,
    val tokenModifiers: Set<String> = emptySet()
)

interface HoverService {
    suspend fun hover(fileUri: String, line: Int, column: Int): HoverResult?
}

data class HoverResult(
    val markdown: String,
    val range: Range? = null
)

interface SignatureHelpService {
    suspend fun signatureHelp(fileUri: String, line: Int, column: Int): SignatureHelpResult?
}

data class SignatureHelpResult(
    val signatures: List<String>,
    val activeSignature: Int,
    val activeParameter: Int
)

