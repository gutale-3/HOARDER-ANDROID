package com.example.ui.screens

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.BookEntity
import com.example.data.local.GlossaryEntity
import com.example.viewmodel.MainViewModel
import java.io.File
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val books by viewModel.repository.allBooks.collectAsState(emptyList())
    val context = LocalContext.current

    // Glossary state
    var activeGlossaryBook by remember { mutableStateOf<BookEntity?>(null) }
    var showGlossaryDialog by remember { mutableStateOf(false) }

    // Deletion confirmation
    var activeDeleteBook by remember { mutableStateOf<BookEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Export progress feedback
    var exportStatusMessage by remember { mutableStateOf("") }
    var showExportResultDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        if (books.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = "Empty Library",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Your library is empty.",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Text(
                            text = "Download novels from the 'Scrape' tab to read offline.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(books) { book ->
                LibraryBookItem(
                    book = book,
                    onRead = { onOpenBook(book.id) },
                    onListen = { viewModel.startTtsForBook(book) },
                    onGlossary = {
                        activeGlossaryBook = book
                        showGlossaryDialog = true
                    },
                    onExportEpub = {
                        viewModel.compileFormat(book, "EPUB") { ok, path ->
                            exportStatusMessage = if (ok) {
                                "EPUB compiled successfully!\nFile: $path"
                            } else {
                                "Failed to compile EPUB!"
                            }
                            showExportResultDialog = true
                            if (ok) shareFile(context, File(path), "application/epub+zip")
                        }
                    },
                    onExportPdf = {
                        viewModel.compileFormat(book, "PDF") { ok, path ->
                            exportStatusMessage = if (ok) {
                                "PDF compiled successfully!\nFile: $path"
                            } else {
                                "Failed to compile PDF!"
                            }
                            showExportResultDialog = true
                            if (ok) shareFile(context, File(path), "application/pdf")
                        }
                    },
                    onDelete = {
                        activeDeleteBook = book
                        showDeleteConfirmDialog = true
                    }
                )
            }
        }
    }

    // --- Confirmation Delete Dialog ---
    if (showDeleteConfirmDialog && activeDeleteBook != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Novel?") },
            text = { Text("Are you sure you want to delete '${activeDeleteBook!!.title}'? This will delete all downloaded chapters, compiled ebooks, and customized glossaries permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(activeDeleteBook!!.id)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Export Result Dialog ---
    if (showExportResultDialog) {
        AlertDialog(
            onDismissRequest = { showExportResultDialog = false },
            title = { Text("Export Completed") },
            text = { Text(exportStatusMessage) },
            confirmButton = {
                Button(onClick = { showExportResultDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // --- Custom Glossary Dialog ---
    if (showGlossaryDialog && activeGlossaryBook != null) {
        GlossaryManagerDialog(
            book = activeGlossaryBook!!,
            viewModel = viewModel,
            onDismiss = { showGlossaryDialog = false }
        )
    }
}

@Composable
fun LibraryBookItem(
    book: BookEntity,
    onRead: () -> Unit,
    onListen: () -> Unit,
    onGlossary: () -> Unit,
    onExportEpub: () -> Unit,
    onExportPdf: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("library_book_card_${book.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cover image or Placeholder
                val hasLocalCover = !book.coverLocalPath.isNullOrEmpty() && File(book.coverLocalPath!!).exists()
                if (hasLocalCover) {
                    AsyncImage(
                        model = File(book.coverLocalPath!!),
                        contentDescription = "${book.title} Cover",
                        modifier = Modifier
                            .size(width = 64.dp, height = 90.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else if (!book.coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = "${book.title} Cover",
                        modifier = Modifier
                            .size(width = 64.dp, height = 90.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 90.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = book.title.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp
                            )
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Box {
                            IconButton(onClick = { expandedMenu = true }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu")
                            }
                            DropdownMenu(
                                expanded = expandedMenu,
                                onDismissRequest = { expandedMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Manage Glossary") },
                                    onClick = {
                                        expandedMenu = false
                                        onGlossary()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Spellcheck, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export EPUB") },
                                    onClick = {
                                        expandedMenu = false
                                        onExportEpub()
                                    },
                                    leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export PDF") },
                                    onClick = {
                                        expandedMenu = false
                                        onExportPdf()
                                    },
                                    leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("Delete Novel", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        expandedMenu = false
                                        onDelete()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }

                    Text(
                        text = "Author: ${book.author}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = book.synopsis,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${book.totalChapters} Chapters Saved",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onListen,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Listen", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Listen", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onRead,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.MenuBook, contentDescription = "Read Book", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Read", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun GlossaryManagerDialog(
    book: BookEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val glossaries by viewModel.repository.getGlossaryFlow(book.id).collectAsState(emptyList())
    var origText by remember { mutableStateOf("") }
    var replText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Glossary: ${book.title}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add translation rules to replace machine-translated terms dynamically as you read offline.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                // Input fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = origText,
                        onValueChange = { origText = it },
                        label = { Text("Original") },
                        placeholder = { Text("e.g. Master Lin") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp)
                    )

                    OutlinedTextField(
                        value = replText,
                        onValueChange = { replText = it },
                        label = { Text("Replace") },
                        placeholder = { Text("e.g. Lin Feng") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp)
                    )

                    IconButton(
                        onClick = {
                            if (origText.trim().isNotEmpty()) {
                                scope.launch {
                                    viewModel.repository.insertGlossary(
                                        GlossaryEntity(
                                            bookId = book.id,
                                            originalText = origText.trim(),
                                            replacementText = replText.trim()
                                        )
                                    )
                                    origText = ""
                                    replText = ""
                                }
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                            .size(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Divider()

                // List of glossary terms
                Text(
                    text = "Active Replacements (${glossaries.size})",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )

                if (glossaries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No glossary terms defined.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(glossaries) { glossary ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = glossary.originalText,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = glossary.replacementText.ifEmpty { "(deleted)" },
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            viewModel.repository.deleteGlossary(glossary)
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// Share Intent helper for compiled EPUB/PDF
private fun shareFile(context: Context, file: File, mimeType: String) {
    try {
        val authority = "${context.packageName}.provider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Ebook"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
