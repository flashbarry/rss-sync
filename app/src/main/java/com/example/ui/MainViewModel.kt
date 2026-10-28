package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ArticleEntity
import com.example.data.local.FeedEntity
import com.example.data.repository.RssRepository
import com.example.data.repository.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FilterTab {
    ALL, UNREAD, STARRED
}

sealed class AppScreen {
    object ArticleList : AppScreen()
    data class ArticleReader(val articleId: String) : AppScreen()
    object ServerLogin : AppScreen()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = RssRepository(application)
    val preferences = repository.preferences

    // Screen navigation state
    private val _currentScreen = MutableStateFlow<AppScreen>(
        if (preferences.isLoggedIn) AppScreen.ArticleList else AppScreen.ServerLogin
    )
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Navigation and filtering
    private val _selectedFeedId = MutableStateFlow<String?>(null)
    val selectedFeedId: StateFlow<String?> = _selectedFeedId.asStateFlow()

    private val _filterTab = MutableStateFlow(FilterTab.ALL)
    val filterTab: StateFlow<FilterTab> = _filterTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    // Sync state
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    // Reader state
    private val _readerFontSize = MutableStateFlow(preferences.readerFontSizeSp)
    val readerFontSize: StateFlow<Int> = _readerFontSize.asStateFlow()

    // Dialogs
    private val _showAddFeedDialog = MutableStateFlow(false)
    val showAddFeedDialog: StateFlow<Boolean> = _showAddFeedDialog.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Pending offline sync count
    val pendingActionCount: StateFlow<Int> = repository.pendingActionCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Feeds from database
    val feeds: StateFlow<List<FeedEntity>> = repository.allFeeds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Raw articles list
    private val rawArticles = repository.allArticles

    // Filtered articles
    val displayedArticles: StateFlow<List<ArticleEntity>> = combine(
        rawArticles,
        _selectedFeedId,
        _filterTab,
        _searchQuery
    ) { articles, feedId, tab, query ->
        articles.filter { article ->
            val matchFeed = (feedId == null || article.feedId == feedId)
            val matchTab = when (tab) {
                FilterTab.ALL -> true
                FilterTab.UNREAD -> !article.isRead
                FilterTab.STARRED -> article.isStarred
            }
            val matchQuery = if (query.isBlank()) true else {
                article.title.contains(query, ignoreCase = true) ||
                        article.summaryHtml.contains(query, ignoreCase = true) ||
                        article.feedTitle.contains(query, ignoreCase = true)
            }
            matchFeed && matchTab && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // If already logged in, trigger initial sync
        if (preferences.isLoggedIn) {
            triggerSync()
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun navigateBack() {
        val current = _currentScreen.value
        if (current is AppScreen.ArticleReader) {
            _currentScreen.value = AppScreen.ArticleList
        } else if (current is AppScreen.ServerLogin && preferences.isLoggedIn) {
            _currentScreen.value = AppScreen.ArticleList
        }
    }

    fun selectFeed(feedId: String?) {
        _selectedFeedId.value = feedId
    }

    fun setFilterTab(tab: FilterTab) {
        _filterTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _searchQuery.value = ""
        }
    }

    fun setShowAddFeedDialog(show: Boolean) {
        _showAddFeedDialog.value = show
    }

    fun setShowSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun triggerSync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "正在与 FreshRSS 同步..."
            val result = repository.sync(force = true)
            _isSyncing.value = false
            if (result.success) {
                val msg = if (result.pendingSyncedCount > 0) {
                    "同步成功: 更新 ${result.newArticlesCount} 篇文章，提交 ${result.pendingSyncedCount} 个离线操作"
                } else {
                    "同步成功: 已更新文章"
                }
                _syncStatusMessage.value = msg
                _snackbarMessage.value = msg
            } else {
                val err = result.errorMessage ?: "同步失败"
                _syncStatusMessage.value = err
                _snackbarMessage.value = err
            }
        }
    }

    fun toggleArticleRead(article: ArticleEntity) {
        viewModelScope.launch {
            repository.setArticleRead(article.id, !article.isRead)
        }
    }

    fun toggleArticleStarred(article: ArticleEntity) {
        viewModelScope.launch {
            repository.setArticleStarred(article.id, !article.isStarred)
        }
    }

    fun markCurrentFeedAsRead() {
        viewModelScope.launch {
            val feedId = _selectedFeedId.value
            if (feedId != null) {
                repository.markFeedAsRead(feedId)
                _snackbarMessage.value = "已将该源全部标为已读"
            } else {
                repository.markAllAsRead()
                _snackbarMessage.value = "已将全部文章标为已读"
            }
        }
    }

    fun addFeed(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            val res = repository.addSubscription(url.trim())
            if (res.isSuccess) {
                _snackbarMessage.value = res.getOrNull() ?: "添加成功"
                _showAddFeedDialog.value = false
            } else {
                _snackbarMessage.value = "添加订阅失败: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun adjustFontSize(delta: Int) {
        val newSize = (_readerFontSize.value + delta).coerceIn(12, 28)
        _readerFontSize.value = newSize
        preferences.readerFontSizeSp = newSize
    }

    fun clearReadCache() {
        viewModelScope.launch {
            val deleted = repository.clearOldArticles(days = 7)
            _snackbarMessage.value = "已清理 $deleted 篇已读缓存文章"
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _currentScreen.value = AppScreen.ServerLogin
            _snackbarMessage.value = "已退出当前账号"
        }
    }

    fun startDemoMode() {
        viewModelScope.launch {
            repository.startDemoMode()
            _currentScreen.value = AppScreen.ArticleList
            _snackbarMessage.value = "已进入演示模式，离线数据已就绪"
        }
    }

    fun getAdjacentArticle(currentId: String, next: Boolean): ArticleEntity? {
        val list = displayedArticles.value
        val index = list.indexOfFirst { it.id == currentId }
        if (index == -1) return null
        val targetIndex = if (next) index + 1 else index - 1
        return list.getOrNull(targetIndex)
    }
}
