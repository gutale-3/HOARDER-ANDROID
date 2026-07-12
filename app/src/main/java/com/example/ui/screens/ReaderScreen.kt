package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.BookEntity
import com.example.data.local.ChapterEntity
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bookState by viewModel.repository.getBookFlow(bookId).collectAsState(null)
    val chapters by viewModel.repository.getChaptersFlow(bookId).collectAsState(emptyList())

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var currentChapterId by remember { mutableStateOf("") }
    var selectedFontFamily by remember { mutableStateOf(FontFamily.Serif) }

    // Settings panel toggles
    var showSettingsDialog by remember { mutableStateOf(false) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedChapters by remember { mutableStateOf(setOf<String>()) }

    // Reset multi-select when drawer closes
    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen) {
            isMultiSelectMode = false
            selectedChapters = emptySet()
        }
    }

    // Load book's last read chapter
    LaunchedEffect(bookState, chapters) {
        val lastRead = bookState?.lastReadChapterId
        if (currentChapterId.isEmpty() && chapters.isNotEmpty()) {
            if (!lastRead.isNullOrEmpty() && chapters.any { it.id == lastRead }) {
                currentChapterId = lastRead
            } else {
                currentChapterId = chapters.first().id
            }
        }
    }

    // Active Chapter object
    val activeChapter = chapters.find { it.id == currentChapterId } ?: chapters.firstOrNull()

    // Whenever active chapter changes, save reading progress in database
    LaunchedEffect(activeChapter) {
        if (activeChapter != null && bookState != null) {
            viewModel.repository.updateBook(bookState!!.copy(lastReadChapterId = activeChapter.id))
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMultiSelectMode) "Selected (${selectedChapters.size})" else "Chapters",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isMultiSelectMode) {
                            IconButton(onClick = {
                                if (selectedChapters.size == chapters.size) {
                                    selectedChapters = emptySet()
                                } else {
                                    selectedChapters = chapters.map { it.id }.toSet()
                                }
                            }) {
                                Icon(
                                    imageVector = if (selectedChapters.size == chapters.size) Icons.Default.SelectAll else Icons.Default.Checklist,
                                    contentDescription = "Select All"
                                )
                            }
                            IconButton(onClick = {
                                isMultiSelectMode = false
                                selectedChapters = emptySet()
                            }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Multi-select")
                            }
                        } else {
                            IconButton(onClick = {
                                isMultiSelectMode = true
                                selectedChapters = emptySet()
                            }) {
                                Icon(imageVector = Icons.Default.EditCalendar, contentDescription = "Enable Multi-select")
                            }
                        }
                    }
                }
                
                if (isMultiSelectMode && selectedChapters.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.updateChaptersReadStatus(selectedChapters.toList(), true)
                                isMultiSelectMode = false
                                selectedChapters = emptySet()
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mark Read", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        
                        Divider(modifier = Modifier.height(20.dp).width(1.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f))
                        
                        TextButton(
                            onClick = {
                                viewModel.updateChaptersReadStatus(selectedChapters.toList(), false)
                                isMultiSelectMode = false
                                selectedChapters = emptySet()
                            }
                        ) {
                            Icon(imageVector = Icons.Default.RemoveDone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mark Unread", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                
                Divider()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(chapters) { ch ->
                        val isCurrent = ch.id == currentChapterId
                        val isSelected = selectedChapters.contains(ch.id)
                        NavigationDrawerItem(
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isMultiSelectMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedChapters = if (checked) {
                                                    selectedChapters + ch.id
                                                } else {
                                                    selectedChapters - ch.id
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    } else {
                                        // Visual indicators for Read / Unread in standard view
                                        Icon(
                                            imageVector = if (ch.isRead) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = if (ch.isRead) "Read" else "Unread",
                                            tint = if (ch.isRead) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) 
                                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp).clickable {
                                                viewModel.updateChaptersReadStatus(listOf(ch.id), !ch.isRead)
                                            }
                                        )
                                    }
                                    
                                    Text(
                                        text = ch.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary 
                                                else if (ch.isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            },
                            selected = !isMultiSelectMode && isCurrent,
                            onClick = {
                                if (isMultiSelectMode) {
                                    selectedChapters = if (isSelected) {
                                        selectedChapters - ch.id
                                    } else {
                                        selectedChapters + ch.id
                                    }
                                } else {
                                    scope.launch {
                                        currentChapterId = ch.id
                                        drawerState.close()
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = bookState?.title ?: "Offline Reader",
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Audio / TTS Narration
                        IconButton(onClick = {
                            val activeCh = activeChapter
                            val bk = bookState
                            if (activeCh != null && bk != null) {
                                if (viewModel.ttsIsPlaying && viewModel.ttsPlayingChapter?.id == activeCh.id) {
                                    viewModel.pauseTts()
                                } else if (viewModel.ttsIsPaused && viewModel.ttsPlayingChapter?.id == activeCh.id) {
                                    viewModel.resumeTts()
                                } else {
                                    viewModel.speak(activeCh.content, bk, activeCh)
                                }
                            }
                        }) {
                            val icon = if (viewModel.ttsIsPlaying && viewModel.ttsPlayingChapter?.id == activeChapter?.id) {
                                Icons.Default.VolumeOff
                            } else {
                                Icons.Default.RecordVoiceOver
                            }
                            Icon(imageVector = icon, contentDescription = "Listen to Chapter")
                        }
                        // Open TOC drawer
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.FormatListNumbered, contentDescription = "Chapter Index")
                        }
                        // Text style settings
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(imageVector = Icons.Default.TextFormat, contentDescription = "Font settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                // Reader Navigation controls (Previous / Next Chapters)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentIdx = chapters.indexOfFirst { it.id == currentChapterId }
                        val hasPrev = currentIdx > 0
                        val hasNext = currentIdx < chapters.size - 1

                        TextButton(
                            onClick = {
                                if (hasPrev) currentChapterId = chapters[currentIdx - 1].id
                            },
                            enabled = hasPrev,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Prev")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Previous", fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = if (chapters.isNotEmpty() && currentIdx != -1) {
                                "${currentIdx + 1} / ${chapters.size}"
                            } else "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )

                        TextButton(
                            onClick = {
                                if (hasNext) currentChapterId = chapters[currentIdx + 1].id
                            },
                            enabled = hasNext,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Next", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next")
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .testTag("reader_canvas")
            ) {
                if (activeChapter == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Loading chapter contents offline...",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground)
                            )
                        }
                    }
                } else {
                    // Actual reading canvas (scrollable with generous line spacing and custom font size!)
                    val scrollState = rememberScrollState()

                    // Reset scroll state on chapter change
                    LaunchedEffect(currentChapterId) {
                        scrollState.scrollTo(0)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = activeChapter.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = selectedFontFamily
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val paragraphs = activeChapter.content.split("\n").filter { it.trim().isNotEmpty() }
                        paragraphs.forEachIndexed { idx, para ->
                            val isReadingThisPara = viewModel.ttsIsPlaying &&
                                    viewModel.ttsPlayingBook?.id == bookState?.id &&
                                    viewModel.ttsPlayingChapter?.id == activeChapter.id &&
                                    idx == viewModel.ttsActiveParagraphIndex

                            val textAndStyleModifier = if (isReadingThisPara) {
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            }

                            val textColor = if (isReadingThisPara) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            }

                            Text(
                                text = "      " + para.trim(),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = viewModel.readerFontSize.sp,
                                    lineHeight = (viewModel.readerFontSize * 1.6).sp,
                                    fontFamily = selectedFontFamily,
                                    color = textColor,
                                    fontWeight = if (isReadingThisPara) FontWeight.Bold else FontWeight.Normal
                                ),
                                modifier = textAndStyleModifier
                            )
                        }

                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
    }

    // --- Dynamic Text Style Controls Dialog ---
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Reading Style Options", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Font Sizing control
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Font Size: ${viewModel.readerFontSize}sp",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.updateFontSize(viewModel.readerFontSize - 1) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("A-")
                            }

                            OutlinedButton(
                                onClick = { viewModel.updateFontSize(viewModel.readerFontSize + 1) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("A+")
                            }
                        }
                    }

                    // Font Family Selector
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Font Style",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Pair("Serif", FontFamily.Serif),
                                Pair("Sans", FontFamily.SansSerif),
                                Pair("Mono", FontFamily.Monospace)
                            ).forEach { (name, font) ->
                                val selected = selectedFontFamily == font
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedFontFamily = font },
                                    label = { Text(name) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}
