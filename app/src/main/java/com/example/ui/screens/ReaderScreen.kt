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
import androidx.compose.ui.platform.LocalContext
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var currentChapterId by remember { mutableStateOf("") }
    val selectedFontFamily = when (viewModel.readerFontFamily) {
        "sans" -> FontFamily.SansSerif
        "mono" -> FontFamily.Monospace
        else -> FontFamily.Serif
    }

    // Settings panel toggles
    var showSettingsDialog by remember { mutableStateOf(false) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedChapters by remember { mutableStateOf(setOf<String>()) }

    // AI states
    val polishedChaptersLoading by viewModel.polishedChaptersLoading.collectAsState()
    val polishedChapter by viewModel.getPolishedChapterFlow(currentChapterId).collectAsState(null)
    var readPolishedTranslation by remember { mutableStateOf(true) }
    var showRecapDialog by remember { mutableStateOf(false) }
    var showAskAiDialog by remember { mutableStateOf(false) }
    var showFindReplaceDialog by remember { mutableStateOf(false) }

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

                                    if (!isMultiSelectMode) {
                                        if (viewModel.rescrapingChapterId == ch.id) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    viewModel.rescrapeSingleChapter(ch) { success, msg ->
                                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Rescrape Chapter",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
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
                        // Rescrape Current Chapter
                        IconButton(onClick = {
                            val activeCh = activeChapter
                            if (activeCh != null) {
                                viewModel.rescrapeSingleChapter(activeCh) { success, msg ->
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            if (viewModel.rescrapingChapterId == activeChapter?.id) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Rescrape Chapter")
                            }
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
                    val lazyListState = rememberLazyListState()

                    // Reset scroll state on chapter change
                    LaunchedEffect(currentChapterId) {
                        lazyListState.scrollToItem(0)
                    }

                    // Auto-follow scroll: Keep the active TTS paragraph centered and visible
                    val activePara = viewModel.ttsActiveParagraphIndex ?: -1
                    val isTtsPlaying = viewModel.ttsIsPlaying
                    val playingChapterId = viewModel.ttsPlayingChapter?.id
                    val autoScrollEnabled = viewModel.ttsAutoScrollEnabled
                    LaunchedEffect(activePara, isTtsPlaying, playingChapterId, autoScrollEnabled) {
                        if (autoScrollEnabled && isTtsPlaying && playingChapterId == activeChapter.id && activePara >= -1) {
                            val targetItemIndex = if (activePara < 0) 0 else activePara + 3
                            
                            // Check if the target item is already comfortably visible
                            val isVisible = lazyListState.layoutInfo.visibleItemsInfo.any {
                                it.index == targetItemIndex && it.offset >= 0 && (it.offset + it.size) <= (lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset)
                            }
                            
                            if (!isVisible) {
                                val viewportHeight = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
                                val offset = if (viewportHeight > 0) -viewportHeight / 3 else -200
                                lazyListState.animateScrollToItem(targetItemIndex, offset)
                            }
                        }
                    }

                    val chapterTextToRender = if (polishedChapter != null && readPolishedTranslation) {
                        polishedChapter!!.content
                    } else {
                        activeChapter.content
                    }
                    val paragraphs = remember(chapterTextToRender) {
                        chapterTextToRender.split("\n").filter { it.trim().isNotEmpty() }
                    }

                    // Expose total paragraphs to viewmodel for TTS player bar seek slider
                    LaunchedEffect(paragraphs.size) {
                        viewModel.ttsTotalParagraphs = paragraphs.size
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 20.dp, bottom = 80.dp)
                    ) {
                        // Title
                        item {
                            Text(
                                text = activeChapter.title,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = selectedFontFamily
                                ),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // AI Reading Toolbar
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isPolishing = polishedChaptersLoading[activeChapter.id] == true
                                
                                if (polishedChapter != null) {
                                    FilterChip(
                                        selected = readPolishedTranslation,
                                        onClick = { readPolishedTranslation = !readPolishedTranslation },
                                        label = { Text("✨ AI Polished") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = "Polished",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("ai_toggle_polished")
                                    )
                                } else {
                                    Button(
                                        onClick = { viewModel.polishChapter(activeChapter) },
                                        enabled = !isPolishing,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp).testTag("ai_polish_button")
                                    ) {
                                        if (isPolishing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        } else {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("AI Polish", fontSize = 11.sp)
                                        }
                                    }
                                }

                                // Summary / Recap button
                                Button(
                                    onClick = { showRecapDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp).testTag("ai_recap_button")
                                ) {
                                    Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Recap", fontSize = 11.sp)
                                }

                                // Ask AI button
                                Button(
                                    onClick = { showAskAiDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp).testTag("ai_ask_button")
                                ) {
                                    Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ask AI", fontSize = 11.sp)
                                }

                                // Find & Replace
                                IconButton(
                                    onClick = { showFindReplaceDialog = true },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                        .testTag("bulk_replace_icon_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Find & Replace",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Divider
                        item {
                            Divider()
                        }

                        // Paragraphs
                        items(paragraphs.size) { idx ->
                            val para = paragraphs[idx]
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

                            val paragraphAlpha = if (viewModel.focusModeEnabled && viewModel.ttsIsPlaying &&
                                    viewModel.ttsPlayingBook?.id == bookState?.id &&
                                    viewModel.ttsPlayingChapter?.id == activeChapter.id) {
                                if (isReadingThisPara) 1.0f else 0.35f
                            } else {
                                1.0f
                            }

                            Text(
                                text = "      " + para.trim(),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = viewModel.readerFontSize.sp,
                                    lineHeight = (viewModel.readerFontSize * 1.6).sp,
                                    fontFamily = selectedFontFamily,
                                    color = textColor.copy(alpha = paragraphAlpha),
                                    fontWeight = if (isReadingThisPara) FontWeight.Bold else FontWeight.Normal
                                ),
                                modifier = textAndStyleModifier
                            )
                        }
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
                                Pair("Serif", "serif"),
                                Pair("Sans", "sans"),
                                Pair("Mono", "mono")
                            ).forEach { (name, fontKey) ->
                                val selected = viewModel.readerFontFamily == fontKey
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateFontFamily(fontKey) },
                                    label = { Text(name) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // Focus Mode Toggle
                    Divider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Focus Mode",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Dim non-active paragraphs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = viewModel.focusModeEnabled,
                            onCheckedChange = { viewModel.toggleFocusMode() }
                        )
                    }

                    // Auto-Scroll Toggle
                    Divider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Auto-Follow Spoken Text",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Automatically scroll page to active sentence",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = viewModel.ttsAutoScrollEnabled,
                            onCheckedChange = { viewModel.toggleTtsAutoScroll() }
                        )
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

    if (showRecapDialog && activeChapter != null) {
        ChapterRecapDialog(
            chapter = activeChapter,
            viewModel = viewModel,
            onDismiss = { showRecapDialog = false }
        )
    }

    if (showAskAiDialog && activeChapter != null) {
        AskAiDialog(
            chapter = activeChapter,
            viewModel = viewModel,
            onDismiss = { showAskAiDialog = false }
        )
    }

    if (showFindReplaceDialog) {
        FindReplaceDialog(
            bookId = bookId,
            viewModel = viewModel,
            onDismiss = { showFindReplaceDialog = false }
        )
    }
}

@Composable
fun ChapterRecapDialog(
    chapter: ChapterEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    var summaryText by remember { mutableStateOf("") }

    LaunchedEffect(chapter.id) {
        isGenerating = true
        val cached = viewModel.repository.getChapterRecap(chapter.id)
        if (cached != null) {
            summaryText = cached.summary
        }
        isGenerating = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Summarize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chapter Recap")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "A concise AI summary of the events in: ${chapter.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isGenerating) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("AI is writing recap...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (summaryText.isNotEmpty()) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            lineHeight = 20.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                isGenerating = true
                                coroutineScope.launch {
                                    val result = viewModel.getChapterRecap(chapter)
                                    if (result != null) {
                                        summaryText = result
                                    }
                                    isGenerating = false
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Recap with AI")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
        dismissButton = {
            if (summaryText.isNotEmpty() && !isGenerating) {
                TextButton(onClick = {
                    isGenerating = true
                    coroutineScope.launch {
                        val result = viewModel.getChapterRecap(chapter, force = true)
                        if (result != null) {
                            summaryText = result
                        }
                        isGenerating = false
                    }
                }) {
                    Text("Regenerate")
                }
            }
        }
    )
}

@Composable
fun AskAiDialog(
    chapter: ChapterEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ask AI Helper")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Ask any question about characters, translation nuances, or plot context in: ${chapter.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = { Text("e.g. Who is Master Lin Feng?") },
                    label = { Text("Your Question") },
                    modifier = Modifier.fillMaxWidth().testTag("ask_ai_input_field"),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                if (isThinking) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("AI is answering...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (answer.isNotEmpty()) {
                    Text(
                        text = "Answer:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = answer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (question.isNotBlank()) {
                        isThinking = true
                        coroutineScope.launch {
                            val resp = viewModel.askAboutSelection(
                                selection = chapter.content.take(1000),
                                question = question
                            )
                            answer = resp
                            isThinking = false
                        }
                    }
                },
                enabled = question.isNotBlank() && !isThinking,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Ask")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun FindReplaceDialog(
    bookId: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var scopeAllBooks by remember { mutableStateOf(false) }
    var isReplacing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bulk Find & Replace")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Perform bulk string substitutions across downloaded chapters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = findText,
                    onValueChange = { findText = it },
                    label = { Text("Find Text") },
                    placeholder = { Text("e.g. Master Lin") },
                    modifier = Modifier.fillMaxWidth().testTag("bulk_replace_find_input"),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text("Replacement Text") },
                    placeholder = { Text("e.g. Lin Feng") },
                    modifier = Modifier.fillMaxWidth().testTag("bulk_replace_replace_input"),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Apply across ALL books",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = scopeAllBooks,
                        onCheckedChange = { scopeAllBooks = it }
                    )
                }

                if (isReplacing) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (resultMessage.isNotEmpty()) {
                    Text(
                        text = resultMessage,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (findText.isNotEmpty()) {
                        isReplacing = true
                        resultMessage = ""
                        coroutineScope.launch {
                            val results = viewModel.bulkFindAndReplace(
                                bookId = bookId,
                                findText = findText,
                                replaceText = replaceText,
                                scopeAllBooks = scopeAllBooks
                            )
                            resultMessage = "Replaced in ${results.first} chapters (${results.second} matches)"
                            isReplacing = false
                        }
                    }
                },
                enabled = findText.isNotEmpty() && !isReplacing,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Execute")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
