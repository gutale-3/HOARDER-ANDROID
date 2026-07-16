package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.BookEntity
import com.example.viewmodel.MainViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToScrape: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val books by viewModel.repository.allBooks.collectAsState(emptyList())
    val totalBooksCount by viewModel.totalBooks.collectAsState(0)
    val totalChaptersCount by viewModel.totalChapters.collectAsState(0)

    val unreadCounts = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(books) {
        books.forEach { book ->
            val count = viewModel.repository.getUnreadChapterCount(book.id)
            unreadCounts[book.id] = count
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        // 1. Offline Mode Indicator
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF34D399), RoundedCornerShape(50))
                )
                Text(
                    text = "OFFLINE MODE ACTIVE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                )
            }
        }

        // 2. Hero Section / Immersive Storage Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1B1D2F),
                                Color(0xFF0F101A)
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            1.5.dp,
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFFE885).copy(alpha = 0.6f),
                                    Color(0xFFD4AF37).copy(alpha = 0.2f),
                                    Color(0xFFFFE885).copy(alpha = 0.5f)
                                )
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "NOVEL HOARDER",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    color = Color(0xFFFFD700)
                                ),
                                modifier = Modifier.testTag("app_hero_title")
                            )
                            Text(
                                text = "OFFLINE VAULT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                ),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Icon(
                            painter = painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                            contentDescription = "App logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Offline Database",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF909194),
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "100",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Light,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "%",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Synced Local Cache",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF909194)
                                ),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Progress bar mimicking the mockup
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF2D2F31))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth() // 100% cache active
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }

        // 3. Statistics Cards (Horizontal)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Book Count Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1C1E21)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF2D2F31))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Saved Novels",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF909194),
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = "$totalBooksCount",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }

                // Chapter Count Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1C1E21)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF2D2F31))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total Chapters",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF909194),
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = "$totalChaptersCount",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onNavigateToScrape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("home_to_scrape_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "New Scrape"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Scrape", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onNavigateToLibrary,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("home_to_library_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryBooks,
                        contentDescription = "Library"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Library", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Continue Listening Card
        if (viewModel.hasResumableSession) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.resumeLastSession() }
                        .testTag("home_continue_listening_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Play Icon Button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CONTINUE LISTENING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = viewModel.resumeBookName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = viewModel.resumeChapterTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Close button to clear progress
                        IconButton(
                            onClick = {
                                viewModel.clearTtsProgress()
                                viewModel.loadResumableTtsSession()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear session",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Recent Novels Header
        item {
            Text(
                text = "Recent Novels",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // List of Recent Books
        if (books.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Empty download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No saved novels found.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = "Paste a TomatoMTL book URL in the 'Scrape' tab to download a novel for offline reading.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(books.take(5)) { book ->
                RecentBookRow(
                    book = book,
                    unreadCount = unreadCounts[book.id] ?: 0,
                    onClick = { onOpenBook(book.id) }
                )
            }
        }
    }
}

@Composable
fun RecentBookRow(
    book: BookEntity,
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val readChapters = (book.totalChapters - unreadCount).coerceAtLeast(0)
    val progressPercent = if (book.totalChapters > 0) {
        (readChapters.toFloat() / book.totalChapters * 100).toInt().coerceIn(0, 100)
    } else {
        0
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("recent_book_${book.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elegant Cover Image with text fallback
            val hasLocalCover = !book.coverLocalPath.isNullOrEmpty() && File(book.coverLocalPath).exists()
            if (hasLocalCover) {
                AsyncImage(
                    model = File(book.coverLocalPath!!),
                    contentDescription = "${book.title} Cover",
                    modifier = Modifier
                        .size(width = 52.dp, height = 72.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else if (!book.coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = "${book.title} Cover",
                    modifier = Modifier
                        .size(width = 52.dp, height = 72.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 72.dp)
                        .clip(RoundedCornerShape(10.dp))
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
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Author: ${book.author}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$progressPercent% Read",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "$readChapters/${book.totalChapters} Chs",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                }
                
                LinearProgressIndicator(
                    progress = if (book.totalChapters > 0) readChapters.toFloat() / book.totalChapters else 0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
            }

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Read",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
