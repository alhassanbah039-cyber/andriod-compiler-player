package com.android.sandbox.compiler.module2_validator

import com.android.sandbox.compiler.model.SandboxFile
import com.android.sandbox.compiler.model.SandboxFileType
import java.util.ArrayDeque

/**
 * High-performance lexical validator enforcing balanced curly braces for JVM/Kotlin code
 * and balanced angle brackets for XML documents. Safely ignores tokens within comments
 * and string literals, while strictly rejecting unclosed string literals.
 */
object SyntaxBracketValidator {

    private data class BracketPos(val line: Int, val col: Int)

    /**
     * Validates a sandbox file based on its file extension / type.
     * Throws SyntaxValidationException on failure.
     */
    @Throws(SyntaxValidationException::class)
    fun validateFile(file: SandboxFile) {
        when (file.fileType) {
            SandboxFileType.SOURCE_KOTLIN,
            SandboxFileType.SOURCE_JAVA,
            SandboxFileType.GRADLE_BUILD,
            SandboxFileType.GRADLE_SETTINGS -> {
                validateKotlinOrJavaBraces(file.relativePath, file.content)
            }
            SandboxFileType.MANIFEST_XML,
            SandboxFileType.LAYOUT_OR_RESOURCE_XML -> {
                validateXmlAngleBrackets(file.relativePath, file.content)
            }
            else -> {
                // For generic text, skip or perform basic check if file name indicates kt/java/xml
                if (file.relativePath.endsWith(".kt") || file.relativePath.endsWith(".java")) {
                    validateKotlinOrJavaBraces(file.relativePath, file.content)
                } else if (file.relativePath.endsWith(".xml")) {
                    validateXmlAngleBrackets(file.relativePath, file.content)
                }
            }
        }
    }

    /**
     * Validates balanced '{' and '}' for Kotlin and Java code.
     * Accurately distinguishes single line comments, nested block comments,
     * character literals, single-line strings, and Kotlin raw triple-quoted strings.
     */
    @Throws(SyntaxValidationException::class)
    fun validateKotlinOrJavaBraces(filePath: String, code: String) {
        val braceStack = ArrayDeque<BracketPos>()
        val len = code.length
        var index = 0
        var line = 1
        var col = 1

        var inLineComment = false
        var blockCommentDepth = 0
        var inCharLiteral = false
        var inStringLiteral = false
        var inRawStringLiteral = false

        var literalStartLine = 1
        var literalStartCol = 1

        while (index < len) {
            val c = code[index]
            val nextC = if (index + 1 < len) code[index + 1] else null
            val nextNextC = if (index + 2 < len) code[index + 2] else null

            // 1. Inside Line Comment: // ... \n
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false
                    line++
                    col = 1
                } else {
                    col++
                }
                index++
                continue
            }

            // 2. Inside Block Comment: /* ... */ (supports Kotlin nested comments)
            if (blockCommentDepth > 0) {
                if (c == '/' && nextC == '*') {
                    blockCommentDepth++
                    index += 2
                    col += 2
                    continue
                } else if (c == '*' && nextC == '/') {
                    blockCommentDepth--
                    index += 2
                    col += 2
                    continue
                } else if (c == '\n') {
                    line++
                    col = 1
                    index++
                    continue
                } else {
                    col++
                    index++
                    continue
                }
            }

            // 3. Inside Kotlin Raw String Literal: """ ... """
            if (inRawStringLiteral) {
                if (c == '"' && nextC == '"' && nextNextC == '"') {
                    inRawStringLiteral = false
                    index += 3
                    col += 3
                    continue
                } else if (c == '\n') {
                    line++
                    col = 1
                    index++
                    continue
                } else {
                    col++
                    index++
                    continue
                }
            }

            // 4. Inside Standard String Literal: " ... "
            if (inStringLiteral) {
                if (c == '\\') {
                    // Skip escaped character
                    index += 2
                    col += 2
                    continue
                }
                if (c == '\n') {
                    throw SyntaxValidationException(
                        filePath = filePath,
                        line = literalStartLine,
                        column = literalStartCol,
                        detailMessage = "Unclosed double-quoted string literal before newline."
                    )
                }
                if (c == '"') {
                    inStringLiteral = false
                    col++
                    index++
                    continue
                }
                col++
                index++
                continue
            }

            // 5. Inside Character Literal: ' ... '
            if (inCharLiteral) {
                if (c == '\\') {
                    // Skip escaped character
                    index += 2
                    col += 2
                    continue
                }
                if (c == '\n') {
                    throw SyntaxValidationException(
                        filePath = filePath,
                        line = literalStartLine,
                        column = literalStartCol,
                        detailMessage = "Unclosed character literal before newline."
                    )
                }
                if (c == '\'') {
                    inCharLiteral = false
                    col++
                    index++
                    continue
                }
                col++
                index++
                continue
            }

            // --- Outside comments and literals: Check for starts of tokens ---

            // Check for Raw String: """
            if (c == '"' && nextC == '"' && nextNextC == '"') {
                inRawStringLiteral = true
                literalStartLine = line
                literalStartCol = col
                index += 3
                col += 3
                continue
            }

            // Check for Double-Quoted String: "
            if (c == '"') {
                inStringLiteral = true
                literalStartLine = line
                literalStartCol = col
                index++
                col++
                continue
            }

            // Check for Char Literal: '
            if (c == '\'') {
                inCharLiteral = true
                literalStartLine = line
                literalStartCol = col
                index++
                col++
                continue
            }

            // Check for Line Comment: //
            if (c == '/' && nextC == '/') {
                inLineComment = true
                index += 2
                col += 2
                continue
            }

            // Check for Block Comment: /*
            if (c == '/' && nextC == '*') {
                blockCommentDepth = 1
                index += 2
                col += 2
                continue
            }

            // --- Check Curly Braces ---
            if (c == '{') {
                braceStack.push(BracketPos(line, col))
            } else if (c == '}') {
                if (braceStack.isEmpty()) {
                    throw SyntaxValidationException(
                        filePath = filePath,
                        line = line,
                        column = col,
                        detailMessage = "Unmatched closing curly brace '}' without corresponding opening '{'."
                    )
                }
                braceStack.pop()
            }

            // Advance line/col
            if (c == '\n') {
                line++
                col = 1
            } else {
                col++
            }
            index++
        }

        // --- EOF Checks ---
        if (inRawStringLiteral) {
            throw SyntaxValidationException(
                filePath = filePath,
                line = literalStartLine,
                column = literalStartCol,
                detailMessage = "Unclosed triple-quoted raw string literal '\"\"\"' reaching EOF."
            )
        }
        if (inStringLiteral) {
            throw SyntaxValidationException(
                filePath = filePath,
                line = literalStartLine,
                column = literalStartCol,
                detailMessage = "Unclosed string literal '\"' reaching EOF."
            )
        }
        if (inCharLiteral) {
            throw SyntaxValidationException(
                filePath = filePath,
                line = literalStartLine,
                column = literalStartCol,
                detailMessage = "Unclosed character literal reaching EOF."
            )
        }
        if (blockCommentDepth > 0) {
            throw SyntaxValidationException(
                filePath = filePath,
                line = line,
                column = col,
                detailMessage = "Unclosed block comment '/*' reaching EOF."
            )
        }

        if (braceStack.isNotEmpty()) {
            val unclosed = braceStack.peek()
            throw SyntaxValidationException(
                filePath = filePath,
                line = unclosed.line,
                column = unclosed.col,
                detailMessage = "Unclosed opening curly brace '{' at line ${unclosed.line}, col ${unclosed.col} reaching EOF."
            )
        }
    }

    /**
     * Validates balanced '<' and '>' angle brackets for XML documents.
     * Safely ignores brackets inside XML comments <!-- ... -->,
     * CDATA sections <![CDATA[ ... ]]>, and quoted attribute values.
     */
    @Throws(SyntaxValidationException::class)
    fun validateXmlAngleBrackets(filePath: String, xml: String) {
        val len = xml.length
        var index = 0
        var line = 1
        var col = 1

        var inComment = false
        var inCdata = false
        var inAttributeQuote = false
        var quoteChar = '"'
        var quoteStartLine = 1
        var quoteStartCol = 1

        var insideTag = false
        var tagStartLine = 1
        var tagStartCol = 1

        while (index < len) {
            val c = xml[index]

            // 1. Inside XML Comment: <!-- ... -->
            if (inComment) {
                if (c == '-' && index + 2 < len && xml[index + 1] == '-' && xml[index + 2] == '>') {
                    inComment = false
                    index += 3
                    col += 3
                    continue
                }
                if (c == '\n') { line++; col = 1 } else { col++ }
                index++
                continue
            }

            // 2. Inside CDATA: <![CDATA[ ... ]]>
            if (inCdata) {
                if (c == ']' && index + 2 < len && xml[index + 1] == ']' && xml[index + 2] == '>') {
                    inCdata = false
                    index += 3
                    col += 3
                    continue
                }
                if (c == '\n') { line++; col = 1 } else { col++ }
                index++
                continue
            }

            // 3. Inside Quoted Attribute: "..." or '...'
            if (inAttributeQuote) {
                if (c == quoteChar) {
                    inAttributeQuote = false
                    index++
                    col++
                    continue
                }
                if (c == '\n') {
                    line++
                    col = 1
                } else {
                    col++
                }
                index++
                continue
            }

            // --- Outside comments, CDATA, and attribute quotes ---

            // Check for XML comment start: <!--
            if (c == '<' && index + 3 < len && xml.substring(index, index + 4) == "<!--") {
                inComment = true
                index += 4
                col += 4
                continue
            }

            // Check for CDATA start: <![CDATA[
            if (c == '<' && index + 8 < len && xml.substring(index, index + 9) == "<![CDATA[") {
                inCdata = true
                index += 9
                col += 9
                continue
            }

            // If inside an XML tag, quotes begin attribute values
            if (insideTag && (c == '"' || c == '\'')) {
                inAttributeQuote = true
                quoteChar = c
                quoteStartLine = line
                quoteStartCol = col
                index++
                col++
                continue
            }

            // Check tag start '<'
            if (c == '<') {
                if (insideTag) {
                    throw SyntaxValidationException(
                        filePath = filePath,
                        line = line,
                        column = col,
                        detailMessage = "Unexpected nested '<' inside an already unclosed tag opened at line $tagStartLine, col $tagStartCol."
                    )
                }
                insideTag = true
                tagStartLine = line
                tagStartCol = col
            } else if (c == '>') {
                if (!insideTag) {
                    throw SyntaxValidationException(
                        filePath = filePath,
                        line = line,
                        column = col,
                        detailMessage = "Unmatched closing bracket '>' without corresponding opening '<'."
                    )
                }
                insideTag = false
            }

            if (c == '\n') {
                line++
                col = 1
            } else {
                col++
            }
            index++
        }

        // EOF Checks
        if (inComment) {
            throw SyntaxValidationException(
                filePath = filePath,
                line = line,
                column = col,
                detailMessage = "Unclosed XML comment '<!--' reaching EOF."
            )
        }
        if (inCdata) {
            throw SyntaxValidationException(
                filePath = filePath,
                line = line,
                column = col,
                detailMessage = "Unclosed CDATA section '<![CDATA[' reaching EOF."
            )
        }
        if (inAttributeQuote) {
            throw SyntaxValidationException(
                filePath = filePath,
                line = quoteStartLine,
                column = quoteStartCol,
                detailMessage = "Unclosed XML attribute string literal '$quoteChar' reaching EOF."
            )
        }
        if (insideTag) {
            throw SyntaxValidationException(
                filePath = filePath,
                line = tagStartLine,
                column = tagStartCol,
                detailMessage = "Unclosed XML tag starting at line $tagStartLine, col $tagStartCol reaching EOF."
            )
        }
    }
}
