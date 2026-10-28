package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FilterTab
import com.example.ui.MainViewModel
import com.example.ui.components.AddFeedDialog
import com.example.ui.components.ArticleCard
import com.example.ui.components.FeedDrawer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val feeds by viewModel.feeds.collectAsState()
    val selectedFeedId by viewModel.selectedFeedId.collectAsState()
    val filterTab by viewModel.filterTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val articles by viewModel.displayedArticles.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val pendingActionCount by viewModel.pendingActionCount.collectAsState()
    val snackbarMsg by viewModel.snackbarMessage.collectAsState()

    val showAddFeedDialog by viewModel.showAddFeedDialog.collectAsState()
    val showSettingsDialog by viewModel.showSettingsDialog.collectAsState()

    var showMenu by remember { mutableStateOf(false) }

    // Display snackbar updates
    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbarMessage()
        }
    }

    // Determine current feed title
    val currentTitle = if (selectedFeedId == null) {
        "全部文章"
    } else {
        feeds.find { it.id == selectedFeedId }?.title ?: "订阅源"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FeedDrawer(
                feeds = feeds,
                selectedFeedId = selectedFeedId,
                username = viewModel.preferences.username,
                serverUrl = viewModel.preferences.serverUrl,
                isDemoMode = viewModel.preferences.isDemoMode,
                pendingActionCount = pendingActionCount,
                onSelectFeed = { feedId ->
                    viewModel.selectFeed(feedId)
                    coroutineScope.launch { drawerState.close() }
                },
                onOpenAddFeed = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.setShowAddFeedDialog(true)
                },
                onOpenSettings = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.setShowSettingsDialog(true)
                },
                onLogout = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.logout()
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (viewModel.preferences.isDemoMode) {
                                Text(
                                    text = "本地演示模式 (支持离线阅读)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = "打开订阅源抽屉")
                        }
                    },
                    actions = {
                        // Search Toggle
                        IconButton(
                            onClick = { viewModel.setSearchActive(!isSearchActive) },
                            modifier = Modifier.testTag("toggle_search_button")
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索文章")
                        }

                        // Sync button with rotation animation
                        val infiniteTransition = rememberInfiniteTransition()
                        val angle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing)
                            )
                        )

                        IconButton(
                            onClick = { viewModel.triggerSync() },
                            enabled = !isSyncing,
                            modifier = Modifier.testTag("sync_toolbar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = "同步",
                                modifier = if (isSyncing) Modifier.rotate(angle) else Modifier
                            )
                        }

                        // Overflow menu
                        IconButton(
                            onClick = { showMenu = !showMenu },
                            modifier = Modifier.testTag("more_menu_button")
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("标为已读") },
                                leadingIcon = { Icon(Icons.Filled.DoneAll, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.markCurrentFeedAsRead()
                                },
                                modifier = Modifier.testTag("menu_mark_all_read")
                            )
                            DropdownMenuItem(
                                text = { Text("添加新订阅源") },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setShowAddFeedDialog(true)
                                },
                                modifier = Modifier.testTag("menu_add_feed")
                            )
                            DropdownMenuItem(
                                text = { Text("VPS 与设置") },
                                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setShowSettingsDialog(true)
                                },
                                modifier = Modifier.testTag("menu_settings")
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { viewModel.triggerSync() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_sync_button")
                ) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val angle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing)
                        )
                    )
                    Icon(
                        imageVector = Icons.Filled.Sync,
                        contentDescription = "同步文章",
                        modifier = if (isSyncing) Modifier.rotate(angle) else Modifier
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Search Input (Collapsible)
                AnimatedVisibility(visible = isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("搜索文章标题、内容或源...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Filled.SearchOff, contentDescription = "清除搜索")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("article_search_input")
                    )
                }

                // Filter Tabs: All, Unread, Starred
                TabRow(
                    selectedTabIndex = filterTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = filterTab == FilterTab.ALL,
                        onClick = { viewModel.setFilterTab(FilterTab.ALL) },
                        text = { Text("全部", fontWeight = if (filterTab == FilterTab.ALL) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("tab_all")
                    )
                    Tab(
                        selected = filterTab == FilterTab.UNREAD,
                        onClick = { viewModel.setFilterTab(FilterTab.UNREAD) },
                        text = { Text("未读", fontWeight = if (filterTab == FilterTab.UNREAD) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("tab_unread")
                    )
                    Tab(
                        selected = filterTab == FilterTab.STARRED,
                        onClick = { viewModel.setFilterTab(FilterTab.STARRED) },
                        text = { Text("星标收藏", fontWeight = if (filterTab == FilterTab.STARRED) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("tab_starred")
                    )
                }

                // Offline & Pending Actions Banner
                if (pendingActionCount > 0) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "离线保护：有 $pendingActionCount 个阅读/星标操作已保存在本地，将在恢复连接时自动推送到 VPS",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Articles List
                if (articles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.RssFeed,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "未找到与「$searchQuery」相关的文章" else "当前筛选无文章",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "点击右下角刷新与 FreshRSS 同步，或在侧边栏切换订阅源",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("article_lazy_column"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(articles, key = { it.id }) { article ->
                            ArticleCard(
                                article = article,
                                onClick = {
                                    viewModel.navigateTo(com.example.ui.AppScreen.ArticleReader(article.id))
                                },
                                onToggleRead = {
                                    viewModel.toggleArticleRead(article)
                                },
                                onToggleStar = {
                                    viewModel.toggleArticleStarred(article)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Feed Dialog
    if (showAddFeedDialog) {
        AddFeedDialog(
            onDismiss = { viewModel.setShowAddFeedDialog(false) },
            onConfirm = { url ->
                viewModel.addFeed(url)
            }
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            serverUrl = viewModel.preferences.serverUrl,
            username = viewModel.preferences.username,
            isDemoMode = viewModel.preferences.isDemoMode,
            lastSyncTimestamp = viewModel.preferences.lastSyncTimestamp,
            pendingActionCount = pendingActionCount,
            fontSizeSp = viewModel.readerFontSize.collectAsState().value,
            onAdjustFontSize = { delta -> viewModel.adjustFontSize(delta) },
            onSyncNow = { viewModel.triggerSync() },
            onClearCache = { viewModel.clearReadCache() },
            onLogout = { viewModel.logout() },
            onDismiss = { viewModel.setShowSettingsDialog(false) }
        )
    }
}
