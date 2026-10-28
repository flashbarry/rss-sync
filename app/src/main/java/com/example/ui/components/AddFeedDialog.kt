package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFeedDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val presetFeeds = listOf(
        "少数派" to "https://sspai.com/feed",
        "36氪" to "https://36kr.com/feed",
        "Hacker News" to "https://news.ycombinator.com/rss",
        "GitHub 趋势" to "https://rsshub.app/github/trending/daily/any",
        "V2EX" to "https://www.v2ex.com/index.xml"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "添加 RSS 订阅源",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "输入 RSS / Atom 订阅链接，系统将自动同步至您的 FreshRSS VPS：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        if (isError) isError = false
                    },
                    label = { Text("订阅源 URL") },
                    placeholder = { Text("https://example.com/feed.xml") },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("请输入有效的订阅链接") }
                    } else null,
                    singleLine = true,
                    trailingIcon = {
                        if (urlText.isNotEmpty()) {
                            IconButton(onClick = { urlText = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "清空输入")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_feed_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "快速添加热门源：",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetFeeds.forEach { (name, url) ->
                        SuggestionChip(
                            onClick = { urlText = url },
                            label = { Text(name, fontSize = 12.sp) },
                            modifier = Modifier.testTag("preset_$name")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlText.isNotBlank()) {
                        onConfirm(urlText.trim())
                    } else {
                        isError = true
                    }
                },
                modifier = Modifier.testTag("confirm_add_feed_button")
            ) {
                Icon(
                    Icons.Filled.AddLink,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("订阅")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_add_feed_button")
            ) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
