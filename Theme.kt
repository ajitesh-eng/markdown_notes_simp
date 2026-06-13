package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownRenderer(
    markdownText: String,
    modifier: Modifier = Modifier,
    onContentChanged: ((String) -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current

    if (markdownText.isBlank()) {
        Text(
            text = "No notes content. Click edit to write.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontStyle = FontStyle.Italic,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    // Parse blocks by splitting with double newlines
    val paragraphs = markdownText.split(Regex("(\\r?\\n){2,}"))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        paragraphs.forEachIndexed { blockIndex, block ->
            val trimmedBlock = block.trim()
            when {
                // Code block (```code```)
                trimmedBlock.startsWith("```") && trimmedBlock.endsWith("```") -> {
                    val codeContent = trimmedBlock.removeSurrounding("```").trim()
                    // Extract programming language if present
                    val firstLineBreak = codeContent.indexOf('\n')
                    val displayCode = if (firstLineBreak != -1 && firstLineBreak < 15) {
                        codeContent.substring(firstLineBreak + 1)
                    } else {
                        codeContent
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Code Block",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(displayCode))
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            SelectionContainer {
                                Text(
                                    text = displayCode,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = 20.sp,
                                        fontSize = 13.sp
                                    ),
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Headers (#, ##, ###)
                trimmedBlock.startsWith("# ") -> {
                    Text(
                        text = buildAnnotatedString {
                            appendMarkdownInline(trimmedBlock.substring(2))
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                trimmedBlock.startsWith("## ") -> {
                    Text(
                        text = buildAnnotatedString {
                            appendMarkdownInline(trimmedBlock.substring(3))
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                trimmedBlock.startsWith("### ") -> {
                    Text(
                        text = buildAnnotatedString {
                            appendMarkdownInline(trimmedBlock.substring(4))
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                // Blockquote (> text)
                trimmedBlock.startsWith(">") -> {
                    val rawLines = trimmedBlock.split("\n")
                    val quoteText = rawLines.joinToString("\n") { line ->
                        if (line.trim().startsWith(">")) line.trim().substring(1).trim() else line
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(start = 12.dp)
                    ) {
                        // Vertical divider left hand border
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(4.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        SelectionContainer {
                            Text(
                                text = buildAnnotatedString { appendMarkdownInline(quoteText) },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // Divider line (---)
                trimmedBlock == "---" || trimmedBlock == "***" -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                // List items (bullet lists `- ` or check lists `- [ ]` / `- [x]`)
                trimmedBlock.startsWith("* ") || trimmedBlock.startsWith("- ") || trimmedBlock.split("\n").any { it.trim().startsWith("- ") || it.trim().startsWith("* ") } -> {
                    val lines = trimmedBlock.split("\n")
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        lines.forEachIndexed { lineIndex, line ->
                            val trimmedLine = line.trim()
                            val bulletStartIndex = if (trimmedLine.startsWith("- ")) 2 else if (trimmedLine.startsWith("* ")) 2 else 0
                            val remainder = trimmedLine.substring(bulletStartIndex)

                            when {
                                // Checked / Active task list matching `- [x] ` or `- [ ] `
                                remainder.startsWith("[x]") || remainder.startsWith("[X]") -> {
                                    val taskContent = remainder.substring(3).trim()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = onContentChanged != null) {
                                                // Calculate full text with toggled state back
                                                val toggledContent = toggleMarkdownCheckbox(
                                                    markdownText,
                                                    blockIndex,
                                                    lineIndex,
                                                    false
                                                )
                                                onContentChanged?.invoke(toggledContent)
                                            }
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Checkbox(
                                            checked = true,
                                            onCheckedChange = { checked ->
                                                val toggledContent = toggleMarkdownCheckbox(
                                                    markdownText,
                                                    blockIndex,
                                                    lineIndex,
                                                    checked
                                                )
                                                onContentChanged?.invoke(toggledContent)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = buildAnnotatedString {
                                                appendMarkdownInline(taskContent)
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            textDecoration = TextDecoration.LineThrough
                                        )
                                    }
                                }
                                remainder.startsWith("[ ]") -> {
                                    val taskContent = remainder.substring(3).trim()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = onContentChanged != null) {
                                                val toggledContent = toggleMarkdownCheckbox(
                                                    markdownText,
                                                    blockIndex,
                                                    lineIndex,
                                                    true
                                                )
                                                onContentChanged?.invoke(toggledContent)
                                            }
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Checkbox(
                                            checked = false,
                                            onCheckedChange = { checked ->
                                                val toggledContent = toggleMarkdownCheckbox(
                                                    markdownText,
                                                    blockIndex,
                                                    lineIndex,
                                                    checked
                                                )
                                                onContentChanged?.invoke(toggledContent)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = buildAnnotatedString {
                                                appendMarkdownInline(taskContent)
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                // Standard Bullet List
                                trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                                    val bulletContent = trimmedLine.substring(2).trim()
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "•",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(16.dp)
                                        )
                                        Text(
                                            text = buildAnnotatedString {
                                                appendMarkdownInline(bulletContent)
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                else -> {
                                    // Continuations lines
                                    Text(
                                        text = buildAnnotatedString {
                                            appendMarkdownInline(trimmedLine)
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Standard body paragraph
                else -> {
                    SelectionContainer {
                        Text(
                            text = buildAnnotatedString {
                                appendMarkdownInline(trimmedBlock)
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Parses simple inline markdown indicators (**bold**, *italics*, `monospace`) and appends them
 */
fun AnnotatedString.Builder.appendMarkdownInline(text: String) {
    var i = 0
    while (i < text.length) {
        val remainder = text.substring(i)

        when {
            // Bold element (**...**)
            remainder.startsWith("**") && remainder.length > 2 -> {
                val endIdx = remainder.indexOf("**", 2)
                if (endIdx != -1) {
                    val boldText = remainder.substring(2, endIdx)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                    i += endIdx + 2
                } else {
                    append("**")
                    i += 2
                }
            }
            // Italic element (*...*)
            remainder.startsWith("*") && remainder.length > 1 -> {
                val endIdx = remainder.indexOf("*", 1)
                if (endIdx != -1) {
                    val italicText = remainder.substring(1, endIdx)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicText)
                    pop()
                    i += endIdx + 1
                } else {
                    append("*")
                    i += 1
                }
            }
            // Monospace monospace code element (`...`)
            remainder.startsWith("`") && remainder.length > 1 -> {
                val endIdx = remainder.indexOf("`", 1)
                if (endIdx != -1) {
                    val codeSpan = remainder.substring(1, endIdx)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x22808080),
                            fontSize = 14.sp
                        )
                    )
                    append(codeSpan)
                    pop()
                    i += endIdx + 1
                } else {
                    append("`")
                    i += 1
                }
            }
            // Standard plain progression character
            else -> {
                append(text[i].toString())
                i++
            }
        }
    }
}

/**
 * Interactive utility allowing preview check switching
 */
fun toggleMarkdownCheckbox(
    fullText: String,
    targetBlockIndex: Int,
    targetLineIndex: Int,
    markChecked: Boolean
): String {
    val paragraphs = fullText.split(Regex("(\\r?\\n){2,}")).toMutableList()
    if (targetBlockIndex >= paragraphs.size) return fullText

    val lines = paragraphs[targetBlockIndex].split("\n").toMutableList()
    if (targetLineIndex >= lines.size) return fullText

    val line = lines[targetLineIndex]
    val replacementLine = when {
        markChecked -> {
            line.replaceFirst("- [ ]", "- [x]").replaceFirst("* [ ]", "- [x]")
        }
        else -> {
            line.replaceFirst("- [x]", "- [ ]").replaceFirst("- [X]", "- [ ]")
                .replaceFirst("* [x]", "- [ ]").replaceFirst("* [X]", "- [ ]")
        }
    }

    lines[targetLineIndex] = replacementLine
    paragraphs[targetBlockIndex] = lines.joinToString("\n")

    return paragraphs.joinToString("\n\n")
}
