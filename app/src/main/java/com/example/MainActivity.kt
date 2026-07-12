package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.ScrapeScreen
import com.example.ui.screens.TtsPlayerBar
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.AiSettingsDialog
import com.example.ui.theme.AppTheme
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
      }
    }

    setContent {
      val viewModel: MainViewModel = viewModel()
      val navController = rememberNavController()

      MyApplicationTheme(appTheme = viewModel.currentTheme) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: "home"

        // Hide navigation elements on Reader screen for deep immersive reading
        val isReaderScreen = currentRoute.startsWith("reader")
        var showAiSettings by remember { mutableStateOf(false) }

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          topBar = {
            if (!isReaderScreen) {
              TopAppBar(
                title = {
                  Text(
                    text = when (currentRoute) {
                      "home" -> "Novel Hoarder"
                      "scrape" -> "Scraper Terminal"
                      "library" -> "My Library"
                      "discover" -> "AI Novel Discoverer"
                      else -> "Novel Hoarder"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                  )
                },
                actions = {
                  // AI Control Center button
                  IconButton(onClick = { showAiSettings = true }) {
                    Icon(
                      imageVector = Icons.Default.AutoAwesome,
                      contentDescription = "AI Control Center",
                      tint = MaterialTheme.colorScheme.primary
                    )
                  }

                  // Theme palette chooser directly in the header!
                  var showThemeMenu by remember { mutableStateOf(false) }
                  Box {
                    IconButton(onClick = { showThemeMenu = true }) {
                      Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Toggle Themes",
                        tint = MaterialTheme.colorScheme.primary
                      )
                    }
                    DropdownMenu(
                      expanded = showThemeMenu,
                      onDismissRequest = { showThemeMenu = false }
                    ) {
                      AppTheme.values().forEach { theme ->
                        DropdownMenuItem(
                          text = { Text(theme.displayName) },
                          onClick = {
                            viewModel.updateTheme(theme)
                            showThemeMenu = false
                          },
                          leadingIcon = {
                            if (viewModel.currentTheme == theme) {
                              Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                              )
                            }
                          }
                        )
                      }
                    }
                  }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                  containerColor = MaterialTheme.colorScheme.surface
                )
              )
            }
          },
          bottomBar = {
            if (!isReaderScreen) {
              NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
              ) {
                // Home tab
                NavigationBarItem(
                  selected = currentRoute == "home",
                  onClick = {
                    if (currentRoute != "home") {
                      navController.navigate("home") {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                      }
                    }
                  },
                  icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                  label = { Text("Home") },
                  modifier = Modifier.testTag("nav_home_tab")
                )

                // Scrape tab
                NavigationBarItem(
                  selected = currentRoute == "scrape",
                  onClick = {
                    if (currentRoute != "scrape") {
                      navController.navigate("scrape") {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                      }
                    }
                  },
                  icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Scrape") },
                  label = { Text("Scrape") },
                  modifier = Modifier.testTag("nav_scrape_tab")
                )

                // Library tab
                NavigationBarItem(
                  selected = currentRoute == "library",
                  onClick = {
                    if (currentRoute != "library") {
                      navController.navigate("library") {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                      }
                    }
                  },
                  icon = { Icon(Icons.Default.LibraryBooks, contentDescription = "Library") },
                  label = { Text("Library") },
                  modifier = Modifier.testTag("nav_library_tab")
                )

                // Discover tab
                NavigationBarItem(
                  selected = currentRoute == "discover",
                  onClick = {
                    if (currentRoute != "discover") {
                      navController.navigate("discover") {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                      }
                    }
                  },
                  icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Discover") },
                  label = { Text("Discover") },
                  modifier = Modifier.testTag("nav_discover_tab")
                )
              }
            }
          }
        ) { innerPadding ->
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
          ) {
            NavHost(
              navController = navController,
              startDestination = "home",
              modifier = Modifier.fillMaxSize()
            ) {
              composable("home") {
                HomeScreen(
                  viewModel = viewModel,
                  onNavigateToScrape = { navController.navigate("scrape") },
                  onNavigateToLibrary = { navController.navigate("library") },
                  onOpenBook = { bookId -> navController.navigate("reader/$bookId") }
                )
              }

              composable("scrape") {
                ScrapeScreen(viewModel = viewModel)
              }

              composable("library") {
                LibraryScreen(
                  viewModel = viewModel,
                  onOpenBook = { bookId -> navController.navigate("reader/$bookId") }
                )
              }

              composable(
                route = "reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
              ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                ReaderScreen(
                  bookId = bookId,
                  viewModel = viewModel,
                  onBack = { navController.popBackStack() }
                )
              }

              composable("discover") {
                DiscoverScreen(
                  viewModel = viewModel,
                  onNavigateToScrape = { navController.navigate("scrape") }
                )
              }
            }

            if (showAiSettings) {
              AiSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showAiSettings = false }
              )
            }

            TtsPlayerBar(
              viewModel = viewModel,
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
            )
          }
        }
      }
    }
  }
}
