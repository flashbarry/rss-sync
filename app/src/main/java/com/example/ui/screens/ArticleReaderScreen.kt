package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.example.ui.MainViewModel
import com.example.ui.theme.StarAmber
import com.example.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderScreen(
    articleId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val articleFlow = viewModel.repository.getArticleById(articleId)
    val article by articleFlow.collectAsState(initial = null)
    val fontSizeSp by viewModel.readerFontSize.collectAsState()

    // Automatically mark as read when reading
    LaunchedEffect(articleId, article) {
        article?.let {
            if (!it.isRead) {
                viewModel.toggleArticleRead(it)
            }
        }
    }

    val prevArticle = viewModel.getAdjacentArticle(articleId, next = false)
    val nextArticle = viewModel.getAdjacentArticle(articleId, next = true)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = article?.feedTitle ?: "文章详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回文章列表"
                        )
                    }
                },
                actions = {
                    // Star button
                    article?.let { art ->
                        IconButton(
                            onClick = { viewModel.toggleArticleStarred(art) },
                            modifier = Modifier.testTag("reader_star_button")
                        ) {
                            Icon(
                                imageVector = if (art.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = if (art.isStarred) "取消星标" else "加星收藏",
                                tint = if (art.isStarred) StarAmber else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Read / Unread toggle
                        IconButton(
                            onClick = { viewModel.toggleArticleRead(art) },
                            modifier = Modifier.testTag("reader_read_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (art.isRead) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                                contentDescription = if (art.isRead) "标为未读" else "标为已读",
                                tint = if (art.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Share
                        IconButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "${art.title}\n${art.link ?: ""}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "分享文章"))
                            },
                            modifier = Modifier.testTag("reader_share_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "分享文章")
                        }

                        // Open in browser
                        if (!art.link.isNullOrEmpty()) {
                            IconButton(
                                onClick = {
                                    try {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(art.link))
                                        context.startActivity(browserIntent)
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.testTag("reader_open_browser_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "在浏览器中打开")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous article
                    TextButton(
                        onClick = {
                            prevArticle?.let { viewModel.navigateTo(com.example.ui.AppScreen.ArticleReader(it.id)) }
                        },
                        enabled = prevArticle != null,
                        modifier = Modifier.testTag("prev_article_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("上一篇", maxLines = 1)
                    }

                    // Font Size controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.adjustFontSize(-1) },
                            modifier = Modifier.size(36.dp).testTag("reader_decrease_font")
                        ) {
                            Text("A-", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${fontSizeSp}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { viewModel.adjustFontSize(1) },
                            modifier = Modifier.size(36.dp).testTag("reader_increase_font")
                        ) {
                            Text("A+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Next article
                    TextButton(
                        onClick = {
                            nextArticle?.let { viewModel.navigateTo(com.example.ui.AppScreen.ArticleReader(it.id)) }
                        },
                        enabled = nextArticle != null,
                        modifier = Modifier.testTag("next_article_button")
                    ) {
                        Text("下一篇", maxLines = 1)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        val currentArticle = article
        if (currentArticle == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val formattedDate = if (currentArticle.publishedAt > 0) {
                DateUtils.getRelativeDateTimeString(
                    context,
                    currentArticle.publishedAt,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.WEEK_IN_MILLIS,
                    0
                ).toString()
            } else ""

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header Feed & Offline status chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = currentArticle.feedTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "离线已缓存",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Article Title
                Text(
                    text = currentArticle.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 32.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Metadata: author & date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!currentArticle.author.isNullOrEmpty()) {
                        Text(
                            text = currentArticle.author,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Article Image
                if (!currentArticle.imageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = currentArticle.imageUrl,
                        contentDescription = "文章插图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Article HTML Body with dynamic typography and links
                val rawHtml = currentArticle.contentHtml.ifEmpty { currentArticle.summaryHtml }
                val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
                val linkColor = MaterialTheme.colorScheme.primary.toArgb()

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("article_html_content"),
                    factory = { ctx ->
                        TextView(ctx).apply {
                            movementMethod = LinkMovementMethod.getInstance()
                            setTextColor(textColor)
                            setLinkTextColor(linkColor)
                            setLineSpacing(8f, 1.25f)
                        }
                    },
                    update = { view ->
                        view.textSize = fontSizeSp.toFloat()
                        view.setTextColor(textColor)
                        view.setLinkTextColor(linkColor)
                        view.text = HtmlCompat.fromHtml(
                            rawHtml,
                            HtmlCompat.FROM_HTML_MODE_COMPACT
                        )
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Link to original
                if (!currentArticle.link.isNullOrEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp)),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "阅读原文链接",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentArticle.link,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = {
                                try {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentArticle.link))
                                    context.startActivity(browserIntent)
                                } catch (_: Exception) {}
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "查看原文"
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
