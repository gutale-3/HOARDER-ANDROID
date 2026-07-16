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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.BookEntity
import com.example.data.local.GlossaryEntity
import com.example.viewmodel.MainViewModel
import java.io.File
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onOpenBook: (String) -> Unit,
    onNavigateToScrape: () -> Unit,
    modifier: Modifier = Modifier
) {
    val books by viewModel.repository.allBooks.collectAsState(emptyList())
    val context = LocalContext.current

    // Search, Filter, Sort, Batch state
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Title A-Z") } // "Title A-Z", "Title Z-A", "Total Chapters", "Unread Chapters", "Last Updated"
    var isBatchMode by remember { mutableStateOf(false) }
    var selectedBookIds by remember { mutableStateOf(emptySet<String>()) }

    var showImportProgressDialog by remember { mutableStateOf(false) }
    var importProgressMessage by remember { mutableStateOf("") }
    var showImportStatusDialog by remember { mutableStateOf(false) }
    var importStatusMessage by remember { mutableStateOf("") }

    // Keep Map of bookId -> unreadCount in memory to sort
    val unreadCounts = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(books) {
        books.forEach { book ->
            val count = viewModel.repository.getUnreadChapterCount(book.id)
            unreadCounts[book.id] = count
        }
    }

    // Configure Import state
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportIsEpub by remember { mutableStateOf(false) }
    var showConfigureImportDialog by remember { mutableStateOf(false) }
    var importCustomUrl by remember { mutableStateOf("") }

    // Edit Book state
    var showEditDetailsDialog by remember { mutableStateOf(false) }
    var activeEditBook by remember { mutableStateOf<BookEntity?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editAuthor by remember { mutableStateOf("") }
    var editUrl by remember { mutableStateOf("") }
    var editCoverUrl by remember { mutableStateOf("") }
    var editCoverLocalPath by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val isEpub = uri.toString().endsWith(".epub", ignoreCase = true) || 
                         (context.contentResolver.getType(uri)?.contains("epub") == true)
            pendingImportUri = uri
            pendingImportIsEpub = isEpub
            importCustomUrl = ""
            showConfigureImportDialog = true
        }
    }

    val coverImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val coversDir = File(context.filesDir, "covers")
                        if (!coversDir.exists()) coversDir.mkdirs()
                        val localFile = File(coversDir, "custom_${System.currentTimeMillis()}.jpg")
                        localFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        inputStream.close()
                        editCoverLocalPath = localFile.absolutePath
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            importProgressMessage = "Restoring library backup... Please wait..."
            showImportProgressDialog = true
            viewModel.library.restoreLibrary(context, uri) { success, msg ->
                showImportProgressDialog = false
                importStatusMessage = msg
                showImportStatusDialog = true
            }
        }
    }

    val filteredAndSortedBooks = remember(books, searchQuery, sortBy, unreadCounts.toMap()) {
        var list = books.filter {
            it.title.contains(searchQuery, ignoreCase = true) || 
            it.author.contains(searchQuery, ignoreCase = true)
        }
        
        list = when (sortBy) {
            "Title A-Z" -> list.sortedBy { it.title.lowercase() }
            "Title Z-A" -> list.sortedByDescending { it.title.lowercase() }
            "Total Chapters" -> list.sortedByDescending { it.totalChapters }
            "Unread Chapters" -> list.sortedByDescending { unreadCounts[it.id] ?: 0 }
            "Last Updated" -> list.sortedByDescending { it.updatedAt }
            else -> list.sortedBy { it.title.lowercase() }
        }
        list
    }

    // Glossary state
    var activeGlossaryBook by remember { mutableStateOf<BookEntity?>(null) }
    var showGlossaryDialog by remember { mutableStateOf(false) }

    // Deletion confirmation
    var activeDeleteBook by remember { mutableStateOf<BookEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Export progress feedback
    var exportStatusMessage by remember { mutableStateOf("") }
    var showExportResultDialog by remember { mutableStateOf(false) }

    // Rescrape feedback
    var rescrapeResultMessage by remember { mutableStateOf("") }
    var showRescrapeResultDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Control Card (Search, Sort, Import, Batch Panel)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search title, author...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("library_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { showSortMenu = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sort: $sortBy", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            listOf("Title A-Z", "Title Z-A", "Total Chapters", "Unread Chapters", "Last Updated").forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        sortBy = option
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { 
                                isBatchMode = !isBatchMode 
                                selectedBookIds = emptySet()
                            }
                        ) {
                            Icon(
                                imageVector = if (isBatchMode) Icons.Default.Close else Icons.Default.Checklist,
                                contentDescription = "Batch Actions",
                                tint = if (isBatchMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }

                        var showImportBackupMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showImportBackupMenu = true }) {
                                Icon(Icons.Default.SystemUpdateAlt, contentDescription = "Import / Sync", tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(
                                expanded = showImportBackupMenu,
                                onDismissRequest = { showImportBackupMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Import Local EPUB/TXT") },
                                    onClick = {
                                        showImportBackupMenu = false
                                        importLauncher.launch("*/*")
                                    },
                                    leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Backup Library") },
                                    onClick = {
                                        showImportBackupMenu = false
                                        viewModel.library.backupLibrary(context) { success, pathOrError ->
                                            if (success) {
                                                shareFile(context, File(pathOrError), "application/json")
                                            } else {
                                                android.widget.Toast.makeText(context, "Backup failed: $pathOrError", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Restore Library Backup") },
                                    onClick = {
                                        showImportBackupMenu = false
                                        restoreLauncher.launch("application/json")
                                    },
                                    leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) }
                                )
                            }
                        }
                    }
                }

                if (isBatchMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected: ${selectedBookIds.size}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(start = 8.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (selectedBookIds.isNotEmpty()) {
                                        viewModel.library.bulkReScrapeBooks(selectedBookIds) { msg ->
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                            isBatchMode = false
                                            selectedBookIds = emptySet()
                                        }
                                    }
                                },
                                enabled = selectedBookIds.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Re-scrape", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    if (selectedBookIds.isNotEmpty()) {
                                        viewModel.library.bulkDeleteBooks(selectedBookIds)
                                        isBatchMode = false
                                        selectedBookIds = emptySet()
                                    }
                                },
                                enabled = selectedBookIds.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
        ) {
            if (filteredAndSortedBooks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp, bottom = 64.dp),
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
                                text = "No matching novels.",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                            Text(
                                text = "Try modifying your search or download new books from 'Scrape' tab.",
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
                items(filteredAndSortedBooks) { book ->
                    LibraryBookItem(
                        book = book,
                        onRead = { onOpenBook(book.id) },
                        onListen = { viewModel.progress.startTtsForBook(book) },
                        onGlossary = {
                            activeGlossaryBook = book
                            showGlossaryDialog = true
                        },
                        onExportEpub = {
                            viewModel.library.compileFormat(book, "EPUB") { ok, path ->
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
                            viewModel.library.compileFormat(book, "PDF") { ok, path ->
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
                        },
                        onRescrapeCorrupted = {
                            viewModel.scraping.rescrapeCorruptedChapters(book.id) { success, message ->
                                rescrapeResultMessage = message
                                showRescrapeResultDialog = true
                            }
                        },
                        onCheckNewChapters = { viewModel.scraping.checkForNewChapters(book) },
                        isCheckingNewChapters = viewModel.isCheckingNewChapters && viewModel.checkingNewChaptersBookId == book.id,
                        isBatchMode = isBatchMode,
                        isSelected = selectedBookIds.contains(book.id),
                        onToggleSelect = {
                            selectedBookIds = if (selectedBookIds.contains(book.id)) {
                                selectedBookIds - book.id
                            } else {
                                selectedBookIds + book.id
                            }
                        },
                        unreadCount = unreadCounts[book.id] ?: 0,
                        onEditDetails = {
                            activeEditBook = book
                            editTitle = book.title
                            editAuthor = book.author
                            editUrl = if (book.url.startsWith("local://")) "" else book.url
                            editCoverUrl = book.coverUrl ?: ""
                            editCoverLocalPath = book.coverLocalPath ?: ""
                            showEditDetailsDialog = true
                        }
                    )
                }
            }
        }
    }

    // --- Import / Restore Progress Dialog ---
    if (showImportProgressDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Processing File...") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(importProgressMessage, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {}
        )
    }

    // --- Import Status Dialog ---
    if (showImportStatusDialog) {
        AlertDialog(
            onDismissRequest = { showImportStatusDialog = false },
            title = { Text("Import Status") },
            text = { Text(importStatusMessage) },
            confirmButton = {
                Button(onClick = { showImportStatusDialog = false }) {
                    Text("OK")
                }
            }
        )
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
                        viewModel.library.deleteBook(activeDeleteBook!!.id)
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

    // --- Rescrape Progress Dialog ---
    if (viewModel.isRescrapingBookId != null) {
        val progressPercent = (viewModel.rescrapeBookProgress * 100).toInt()
        AlertDialog(
            onDismissRequest = { /* non-dismissable */ },
            title = { Text("Rescraping Novel...") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Re-scraping corrupted/invalid chapters for this novel. This identifies chapters capturing login pages or error messages and restores them. Please hold on...")
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = viewModel.rescrapeBookProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Progress: $progressPercent%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {}
        )
    }

    // --- Rescrape Result Dialog ---
    if (showRescrapeResultDialog) {
        AlertDialog(
            onDismissRequest = { showRescrapeResultDialog = false },
            title = { Text("Rescrape Completed") },
            text = { Text(rescrapeResultMessage) },
            confirmButton = {
                Button(onClick = { showRescrapeResultDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // --- Configure Import Dialog ---
    if (showConfigureImportDialog && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { showConfigureImportDialog = false },
            title = { Text("Configure Local Import") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "You are importing a local book file. To enable future chapter updates, re-scraping, or automatic chapter syncing in the future, you can optionally provide its online source URL below.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = importCustomUrl,
                        onValueChange = { importCustomUrl = it },
                        label = { Text("Novel Source URL (Optional)") },
                        placeholder = { Text("https://tomatomtl.com/books/...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Text(
                        text = "If left empty, the book will be imported purely as a local offline file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingImportUri!!
                        val isEpub = pendingImportIsEpub
                        val customUrl = importCustomUrl.trim().ifEmpty { null }
                        showConfigureImportDialog = false
                        importProgressMessage = "Importing local file... Please wait..."
                        showImportProgressDialog = true
                        viewModel.library.importLocalFile(context, uri, isEpub = isEpub, customUrl = customUrl) { success, msg ->
                            showImportProgressDialog = false
                            importStatusMessage = msg
                            showImportStatusDialog = true
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigureImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Edit Novel Details Dialog ---
    if (showEditDetailsDialog && activeEditBook != null) {
        AlertDialog(
            onDismissRequest = { showEditDetailsDialog = false },
            title = { Text("Edit Novel Details") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editAuthor,
                        onValueChange = { editAuthor = it },
                        label = { Text("Author") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text("Source Scraping URL") },
                        placeholder = { Text("https://tomatomtl.com/books/...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Divider()

                    Text(
                        text = "Novel Cover Image",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    // Display current image or temporary edited image
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val coverModel = if (editCoverLocalPath.isNotEmpty()) {
                            File(editCoverLocalPath)
                        } else if (editCoverUrl.isNotEmpty()) {
                            editCoverUrl
                        } else {
                            null
                        }

                        if (coverModel != null) {
                            AsyncImage(
                                model = coverModel,
                                contentDescription = "Cover preview",
                                modifier = Modifier
                                    .size(width = 60.dp, height = 84.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(width = 60.dp, height = 84.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { coverImageLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pick Gallery Image", fontSize = 12.sp)
                            }

                            if (editCoverLocalPath.isNotEmpty()) {
                                TextButton(
                                    onClick = { editCoverLocalPath = "" },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Reset Local Photo", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editCoverUrl,
                        onValueChange = { editCoverUrl = it },
                        label = { Text("Cover Image URL (Fallback)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bookId = activeEditBook!!.id
                        viewModel.library.updateBookDetails(
                            bookId = bookId,
                            newTitle = editTitle,
                            newAuthor = editAuthor,
                            newUrl = editUrl,
                            newCoverUrl = editCoverUrl.ifBlank { null },
                            newCoverLocalPath = editCoverLocalPath.ifBlank { null }
                        ) { success, msg ->
                            showEditDetailsDialog = false
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDetailsDialog = false }) {
                    Text("Cancel")
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

    // --- New Chapters Found Dialog ---
    if (viewModel.showNewChaptersDialog) {
        val count = viewModel.newChaptersFoundCount
        val bookName = viewModel.checkedBookEntity?.title ?: "Novel"
        AlertDialog(
            onDismissRequest = { viewModel.showNewChaptersDialog = false },
            title = { Text("New Chapters Found") },
            text = {
                if (count > 0) {
                    Text("Found $count new chapters for \"$bookName\". Do you want to download/get them now?")
                } else {
                    Text("No new chapters found for \"$bookName\". Everything is up to date!")
                }
            },
            confirmButton = {
                if (count > 0) {
                    Button(
                        onClick = {
                            viewModel.scraping.startScrapingNewChapters()
                            onNavigateToScrape()
                        }
                    ) {
                        Text("Yes, Get It")
                    }
                } else {
                    Button(onClick = { viewModel.showNewChaptersDialog = false }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (count > 0) {
                    TextButton(onClick = { viewModel.showNewChaptersDialog = false }) {
                        Text("No")
                    }
                }
            }
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
    onRescrapeCorrupted: () -> Unit,
    onCheckNewChapters: () -> Unit,
    isCheckingNewChapters: Boolean,
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    unreadCount: Int = 0,
    onEditDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isBatchMode) { onRead() }
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
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = if (isBatchMode) Modifier.clickable { onToggleSelect() } else Modifier
            ) {
                if (isBatchMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
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
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Check for New Chapters")
                                            if (isCheckingNewChapters) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        expandedMenu = false
                                        onCheckNewChapters()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    enabled = !isCheckingNewChapters
                                )
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
                                DropdownMenuItem(
                                    text = { Text("Rescrape Corrupted Chapters") },
                                    onClick = {
                                        expandedMenu = false
                                        onRescrapeCorrupted()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit Novel Details") },
                                    onClick = {
                                        expandedMenu = false
                                        onEditDetails()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
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

            // Info row (Chapters Saved and unread count)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${book.totalChapters} Chapters Saved",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (unreadCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "$unreadCount unread",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (isCheckingNewChapters) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Reading progress percentage & bar
            val readChapters = (book.totalChapters - unreadCount).coerceAtLeast(0)
            val progressPercent = if (book.totalChapters > 0) {
                (readChapters.toFloat() / book.totalChapters * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reading Progress: $progressPercent%",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = "$readChapters/${book.totalChapters} Read",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                }
                LinearProgressIndicator(
                    progress = if (book.totalChapters > 0) readChapters.toFloat() / book.totalChapters else 0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
            }

            // Symmetric Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Listen", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Listen", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = onRead,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.MenuBook, contentDescription = "Read Book", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Read", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
    var editingGlossary by remember { mutableStateOf<GlossaryEntity?>(null) }
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
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add translation rules to replace machine-translated terms dynamically as you read offline.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                // AI Glossary Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AI Glossary Assistant",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                    Button(
                        onClick = { viewModel.aiFeatures.generateGlossaryWithAi(book) },
                        enabled = !viewModel.isGeneratingGlossary,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("ai_glossary_generate_btn")
                    ) {
                        if (viewModel.isGeneratingGlossary) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Generate with AI", fontSize = 11.sp)
                        }
                    }
                }
                
                if (viewModel.glossaryStatusMessage.isNotEmpty()) {
                    Text(
                        text = viewModel.glossaryStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (viewModel.glossaryStatusMessage.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Divider()

                // Input fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = origText,
                        onValueChange = { origText = it },
                        label = { Text(if (editingGlossary != null) "Edit Original" else "Original") },
                        placeholder = { Text("e.g. Master Lin") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp)
                    )

                    OutlinedTextField(
                        value = replText,
                        onValueChange = { replText = it },
                        label = { Text(if (editingGlossary != null) "Edit Replace" else "Replace") },
                        placeholder = { Text("e.g. Lin Feng") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp)
                    )

                    if (editingGlossary != null) {
                        IconButton(
                            onClick = {
                                editingGlossary = null
                                origText = ""
                                replText = ""
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(6.dp))
                                .size(48.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Edit", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }

                    IconButton(
                        onClick = {
                            if (origText.trim().isNotEmpty()) {
                                scope.launch {
                                    val toSave = editingGlossary?.copy(
                                        originalText = origText.trim(),
                                        replacementText = replText.trim()
                                    ) ?: GlossaryEntity(
                                        bookId = book.id,
                                        originalText = origText.trim(),
                                        replacementText = replText.trim()
                                    )
                                    viewModel.repository.insertGlossary(toSave)
                                    editingGlossary = null
                                    origText = ""
                                    replText = ""
                                }
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (editingGlossary != null) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = if (editingGlossary != null) "Save Edit" else "Add",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
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
                                    .background(
                                        color = if (editingGlossary?.id == glossary.id) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
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

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            editingGlossary = glossary
                                            origText = glossary.originalText
                                            replText = glossary.replacementText
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                if (editingGlossary?.id == glossary.id) {
                                                    editingGlossary = null
                                                    origText = ""
                                                    replText = ""
                                                }
                                                viewModel.repository.deleteGlossary(glossary)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
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
