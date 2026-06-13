package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MarkdownRenderer
import com.example.ui.viewmodel.NotesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    viewModel: NotesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Write, 1 = Preview

    // Retrieve active title and content values
    val titleStream = viewModel.currentEditTitle
    val contentStream = viewModel.currentEditContent

    // Track state locally via TextFieldValue to maintain cursor tracking
    var contentValueState by remember {
        mutableStateOf(TextFieldValue(contentStream))
    }

    // Sync ViewModel's external stream with TextFieldValue
    LaunchedEffect(contentStream) {
        if (contentStream != contentValueState.text) {
            contentValueState = contentValueState.copy(text = contentStream)
        }
    }

    // Surgical insertion into editor helper
    fun insertMarkdownHelper(prefix: String, suffix: String = "") {
        val selectedText = contentValueState.text.substring(
            contentValueState.selection.start,
            contentValueState.selection.end
        )
        val textInsertion = "$prefix$selectedText$suffix"
        val newFullText = contentValueState.text.replaceRange(
            contentValueState.selection.start,
            contentValueState.selection.end,
            textInsertion
        )
        val newSelectionIdx = contentValueState.selection.start + prefix.length + selectedText.length

        contentValueState = TextFieldValue(
            text = newFullText,
            selection = androidx.compose.ui.text.TextRange(newSelectionIdx)
        )
        viewModel.updateEditFields(titleStream, newFullText)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Note",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.saveCurrentNote { onNavigateBack() }
                        },
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Save and go back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveCurrentNote {
                                // Simple save status toast overlay
                            }
                        },
                        modifier = Modifier.testTag("save_note_action")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save changes"
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.selectedNoteId?.let { id ->
                                viewModel.deleteNoteById(id) {
                                    onNavigateBack()
                                }
                            } ?: onNavigateBack()
                        },
                        modifier = Modifier.testTag("delete_note_action")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete this note",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Write vs Preview Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Write", fontWeight = FontWeight.Medium) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_write")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Preview", fontWeight = FontWeight.Medium) },
                    icon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_preview")
                )
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "editor_transition",
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxWidth()
            ) { state ->
                when (state) {
                    0 -> {
                        // Write Screen Content Editor
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            // Document Formatting Toolbar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FormatHelperKey(label = "H1", desc = "Header 1") { insertMarkdownHelper("# ") }
                                FormatHelperKey(label = "H2", desc = "Header 2") { insertMarkdownHelper("## ") }
                                FormatHelperKey(label = "H3", desc = "Header 3") { insertMarkdownHelper("### ") }
                                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                                FormatHelperKey(icon = Icons.Default.FormatBold, desc = "Bold") { insertMarkdownHelper("**", "**") }
                                FormatHelperKey(icon = Icons.Default.FormatItalic, desc = "Italic") { insertMarkdownHelper("*", "*") }
                                FormatHelperKey(icon = Icons.Default.Code, desc = "In-line Code") { insertMarkdownHelper("`", "`") }
                                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                                FormatHelperKey(icon = Icons.Default.FormatListBulleted, desc = "Bullet List") { insertMarkdownHelper("- ") }
                                FormatHelperKey(icon = Icons.Default.Checklist, desc = "Task List") { insertMarkdownHelper("- [ ] ") }
                                FormatHelperKey(icon = Icons.Default.FormatQuote, desc = "Blockquote") { insertMarkdownHelper("> ") }
                                FormatHelperKey(icon = Icons.Default.HorizontalRule, desc = "Separator Rule") { insertMarkdownHelper("\n---\n") }
                                FormatHelperKey(label = "Code Block", desc = "Code Block Wrap") { insertMarkdownHelper("```\n", "\n```") }
                            }

                            // Interactive Title Field
                            TextField(
                                value = titleStream,
                                onValueChange = { viewModel.updateEditFields(it, contentValueState.text) },
                                placeholder = { Text("Title", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.background,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("note_detail_title_input")
                            )

                            // Editor note workspace input body
                            TextField(
                                value = contentValueState,
                                onValueChange = {
                                    contentValueState = it
                                    viewModel.updateEditFields(titleStream, it.text)
                                },
                                placeholder = { Text("Write your standard or Markdown notes here...") },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.background,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    lineHeight = 22.sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                                    .testTag("note_detail_content_input")
                            )
                        }
                    }

                    1 -> {
                        // Render Preview Screen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            // Styled Title Row
                            Text(
                                text = titleStream.ifBlank { "Untitled Note" },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Visual Markdown renderer with live update checkbox callback
                            MarkdownRenderer(
                                markdownText = contentStream,
                                onContentChanged = { updatedMarkdown ->
                                    viewModel.handleExternalContentOverride(updatedMarkdown)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FormatHelperKey(
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    desc: String,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier
            .padding(2.dp)
            .size(width = if (label != null) 72.dp else 40.dp, height = 36.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                modifier = Modifier.size(18.dp)
            )
        } else if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
