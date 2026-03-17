package com.aspect.braveyoutube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aspect.braveyoutube.data.model.VideoItem
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * YouTube-style full-width video card for the home feed.
 */
@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 16.dp)
    ) {
        // Thumbnail with duration badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Duration badge
            if (video.duration.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = video.duration,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Video info row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // Channel avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { onChannelClick(video.channelId) }
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
                if (video.channelAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = normalizeAvatarUrl(video.channelAvatarUrl),
                        contentDescription = video.channelName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        text = video.channelName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Title and metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(video.channelName)
                        if (video.viewCount.isNotBlank()) {
                            append(" · ")
                            append(video.viewCount)
                            append(" views")
                        }
                        if (video.publishedAt.isNotBlank()) {
                            append(" · ")
                            append(toRelativeTime(video.publishedAt))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Three-dot menu
            IconButton(
                onClick = { /* TODO: video options menu */ },
                modifier = Modifier
                    .size(24.dp)
                    .offset(x = 8.dp) // push it slightly right to align with edge
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * YouTube-style horizontal video card for related videos and search results.
 */
@Composable
fun SmallVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .width(168.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Duration badge
            if (video.duration.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = video.duration,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Video info
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp)
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = video.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onChannelClick(video.channelId) }
            )
            if (video.viewCount.isNotBlank()) {
                Text(
                    text = "${video.viewCount} views · ${toRelativeTime(video.publishedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // More menu
        IconButton(
            onClick = { /* TODO */ },
            modifier = Modifier
                .size(28.dp)
                .align(Alignment.Top)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun normalizeAvatarUrl(url: String): String {
    if (url.isBlank()) return url
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> url.replaceFirst("http://", "https://")
        else -> url
    }
}

private fun toRelativeTime(raw: String): String {
    if (raw.isBlank()) return ""
    val lower = raw.lowercase()
    if (lower.contains("ago") || lower.contains("streamed")) return raw

    val publishedInstant = try {
        Instant.parse(raw)
    } catch (_: DateTimeParseException) {
        try {
            Instant.parse(raw.replace(" ", "T") + "Z")
        } catch (_: Exception) {
            return raw.take(10)
        }
    }

    val now = Instant.now().atOffset(ZoneOffset.UTC)
    val published = publishedInstant.atOffset(ZoneOffset.UTC)
    val duration = Duration.between(published, now)
    val minutes = duration.toMinutes().coerceAtLeast(0)
    val hours = duration.toHours().coerceAtLeast(0)
    val days = duration.toDays().coerceAtLeast(0)

    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
        hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
        days < 7 -> if (days == 1L) "1 day ago" else "$days days ago"
        days < 30 -> {
            val weeks = (days / 7).coerceAtLeast(1)
            if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
        }
        days < 365 -> {
            val months = (days / 30).coerceAtLeast(1)
            if (months == 1L) "1 month ago" else "$months months ago"
        }
        else -> {
            val years = (days / 365).coerceAtLeast(1)
            if (years == 1L) "1 year ago" else "$years years ago"
        }
    }
}

/**
 * YouTube-style vertical video card for Shorts.
 */
@Composable
fun ShortVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        // Vertical thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Duration badge
            if (video.duration.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = video.duration,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Title
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        if (video.viewCount.isNotBlank()) {
            Text(
                text = "${video.viewCount} views",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
