package com.metrolist.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.state.app.AppState
import com.metrolist.desktop.state.app.UiState
import com.metrolist.desktop.state.content.ArtistState
import com.metrolist.desktop.state.content.PlaylistAlbumState
import com.metrolist.desktop.state.playback.LyricsState
import com.metrolist.desktop.state.playback.PlayerState
import com.metrolist.desktop.storage.DownloadManager
import com.metrolist.desktop.storage.DownloadState
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_like
import com.metrolist.shared.generated.resources.add_to_playlist
import com.metrolist.shared.generated.resources.album
import com.metrolist.shared.generated.resources.bitrate
import com.metrolist.shared.generated.resources.bitrate_value
import com.metrolist.shared.generated.resources.codecs
import com.metrolist.shared.generated.resources.details
import com.metrolist.shared.generated.resources.downloaded
import com.metrolist.shared.generated.resources.explicit
import com.metrolist.shared.generated.resources.lyrics
import com.metrolist.shared.generated.resources.now_playing
import com.metrolist.shared.generated.resources.now_playing_collapse_panel
import com.metrolist.shared.generated.resources.now_playing_expand_player
import com.metrolist.shared.generated.resources.now_playing_open_queue
import com.metrolist.shared.generated.resources.now_playing_show_lyrics
import com.metrolist.shared.generated.resources.now_playing_track_number
import com.metrolist.shared.generated.resources.now_playing_year_label
import com.metrolist.shared.generated.resources.sample_rate
import com.metrolist.shared.generated.resources.sample_rate_value
import com.metrolist.shared.generated.resources.share
import com.metrolist.shared.generated.resources.unlike
import com.metrolist.shared.generated.resources.up_next
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.previewThumbnailUrl
import com.metrolist.shared.playback.MusicQueueEntry
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.components.LiveBadge
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.icons.SongStatusIcons
import com.metrolist.shared.ui.player.PlayerArtistLine
import com.metrolist.shared.utils.ShareUrls
import com.metrolist.shared.utils.formatDuration
import com.metrolist.shared.youtube.playback.PlaybackResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NowPlayingDrawer(
    colorScheme: ColorScheme,
    availableWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val widthClass = currentWindowAdaptiveInfoV2().windowSizeClass.minWidthDp
    val preferredWidth = if (widthClass < 600) 320.dp else 360.dp
    val drawerOpen =
        UiState.isNowPlayingDrawerVisible &&
            PlayerState.currentTrack != null &&
            availableWidth >= 280.dp
    val targetDrawerWidth = if (drawerOpen) minOf(preferredWidth, availableWidth) else 0.dp
    val animatedDrawerWidth by animateDpAsState(
        targetValue = targetDrawerWidth,
        animationSpec = tween(durationMillis = 280, easing = EaseOutCubic),
        label = "nowPlayingDrawerWidth",
    )

    AnimatedVisibility(
        visible = drawerOpen,
        modifier = modifier.fillMaxHeight(),
        enter =
            expandHorizontally(
                expandFrom = Alignment.End,
                animationSpec = tween(durationMillis = 280, easing = EaseOutCubic),
            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
        exit =
            shrinkHorizontally(
                shrinkTowards = Alignment.End,
                animationSpec = tween(durationMillis = 240, easing = EaseOutCubic),
            ) + fadeOut(animationSpec = tween(durationMillis = 150)),
    ) {
        DesktopNowPlayingContent(
            onClose = UiState::toggleNowPlayingDrawer,
            colorScheme = colorScheme,
            modifier =
                Modifier
                    .padding(top = 8.dp, bottom = 8.dp, end = 8.dp)
                    .width(animatedDrawerWidth)
                    .fillMaxHeight(),
            isVisible = !PlayerState.isExpanded && !UiState.videoFullscreenObscuresPlayer,
            isOverlay = true,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun DesktopNowPlayingContent(
    onClose: () -> Unit,
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    isOverlay: Boolean = false,
) {
    val track = PlayerState.currentTrack
    var showDetailsDialog by remember { mutableStateOf(false) }

    val animatedSeedColor by animateColorAsState(
        targetValue = UiState.seedColor,
        animationSpec = tween(durationMillis = 450),
        label = "nowPlayingSeedColor",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        tonalElevation = if (isOverlay) 4.dp else 0.dp,
        shadowElevation = if (isOverlay) 12.dp else 0.dp,
        border =
            if (isOverlay) {
                BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
            } else {
                null
            },
    ) {
        if (track == null) return@Surface

        val playbackResource by PlayerState.player.currentPlaybackResource.collectAsState()
        val stream = playbackResource?.takeIf { it.videoId == track.id }
        val durationMs by PlayerState.playbackDuration.collectAsState()
        val isLive = playerIsLiveStream()
        val queueState by PlayerState.queue.state.collectAsState()
        val downloadStates by DownloadManager.downloadStates.collectAsState()
        val isDownloaded = downloadStates[track.id] == DownloadState.DOWNLOADED
        val isLiked = PlayerState.isLiked(track.id)
        val scrollState = rememberScrollState()

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        animatedSeedColor.copy(alpha = 0.22f),
                                        colorScheme.primaryContainer.copy(alpha = 0.08f),
                                        Color.Transparent,
                                    ),
                            ),
                        ),
            )

            Column(modifier = Modifier.fillMaxSize()) {
                NowPlayingHeader(
                    colorScheme = colorScheme,
                    isLive = isLive,
                    onExpand = PlayerState::enterExpanded,
                    onClose = onClose,
                )

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    NowPlayingArtworkCard(
                        track = track,
                        isPlaying = PlayerState.isPlaying,
                        isVisible = isVisible,
                        colorScheme = colorScheme,
                        onExpand = PlayerState::enterExpanded,
                    )

                    NowPlayingTitleAndActions(
                        track = track,
                        isLiked = isLiked,
                        isDownloaded = isDownloaded,
                        colorScheme = colorScheme,
                        onToggleLike = { PlayerState.toggleLike(track) },
                        onAddToPlaylist = { PlayerState.showAddToPlaylistSong = track },
                        onShare = { AppState.copyToClipboard(ShareUrls.song(track.id)) },
                        onShowDetails = { showDetailsDialog = true },
                    )

                    NowPlayingLyricsTeaserCard(
                        track = track,
                        durationMs = durationMs,
                        isLive = isLive,
                        isVisible = isVisible,
                        colorScheme = colorScheme,
                        onOpenLyrics = LyricsState::toggleLyricsDrawer,
                    )

                    track.album?.let { album ->
                        NowPlayingAlbumCard(
                            albumName = album.name,
                            year = track.year,
                            colorScheme = colorScheme,
                            onClick = { PlaylistAlbumState.openSongAlbum(track) },
                        )
                    }

                    NowPlayingStreamInfoCard(
                        track = track,
                        stream = stream,
                        colorScheme = colorScheme,
                    )

                    NowPlayingUpNextCard(
                        queueEntries = queueState.entries,
                        currentIndex = queueState.currentIndex,
                        relatedSongs = PlayerState.relatedSongs,
                        colorScheme = colorScheme,
                        onOpenQueue = UiState::togglePersistentPlayerDrawer,
                    )
                }
            }
        }
    }

    if (showDetailsDialog) {
        MediaDetailsDialog(
            song = track,
            onDismiss = { showDetailsDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingHeader(
    colorScheme: ColorScheme,
    isLive: Boolean,
    onExpand: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = colorScheme.primaryContainer.copy(alpha = 0.55f),
            shape = CircleShape,
        ) {
            Icon(
                imageVector = MetrolistIcons.Rounded.MusicNote,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.padding(6.dp).size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(Res.string.now_playing),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isLive) {
            LiveBadge(compact = true, modifier = Modifier.padding(end = 4.dp))
        }
        IconButton(
            onClick = onExpand,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = MetrolistIcons.Outlined.Fullscreen,
                contentDescription = stringResource(Res.string.now_playing_expand_player),
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(
            onClick = onClose,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = MetrolistIcons.Outlined.Close,
                contentDescription = stringResource(Res.string.now_playing_collapse_panel),
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun NowPlayingArtworkCard(
    track: SongItem,
    isPlaying: Boolean,
    isVisible: Boolean,
    colorScheme: ColorScheme,
    onExpand: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val animatedCoverUrl = PlayerState.animatedCoverUrl
    var hasAnimatedFrame by remember(animatedCoverUrl) { mutableStateOf(false) }
    val artworkUrl = PlayerState.currentArtworkUrl ?: track.previewThumbnailUrl ?: track.thumbnail.orEmpty()
    val staticAlpha by animateFloatAsState(
        targetValue = if (hasAnimatedFrame) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "nowPlayingArtworkAlpha",
    )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(12.dp, shape)
                .clip(shape)
                .background(colorScheme.surfaceContainerHigh, shape)
                .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f), shape)
                .clickable(onClick = onExpand),
    ) {
        AsyncImage(
            url = artworkUrl,
            artworkPreview = PlayerState.artworkPreview,
            shape = shape,
            contentScale = if (Settings.shared.cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize().alpha(staticAlpha),
        )
        AnimatedCover(
            url = animatedCoverUrl,
            active = isPlaying && !UiState.videoFullscreenObscuresPlayer && isVisible,
            modifier = Modifier.fillMaxSize().clip(shape),
            contentScale = ContentScale.Crop,
            onFrameAvailabilityChanged = { hasAnimatedFrame = it },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingTitleAndActions(
    track: SongItem,
    isLiked: Boolean,
    isDownloaded: Boolean,
    colorScheme: ColorScheme,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onShowDetails: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val hasAlbum = track.album?.id != null
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        if (hasAlbum) {
                            Modifier.clickable { PlaylistAlbumState.openSongAlbum(track) }
                        } else {
                            Modifier
                        },
                )
                Spacer(Modifier.height(2.dp))
                PlayerArtistLine(
                    artists = track.artists,
                    onNavigateToArtist = ArtistState::fetchArtistData,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    iterations = Int.MAX_VALUE,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = onToggleLike,
                shapes = IconButtonDefaults.shapes(),
                colors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor =
                            if (isLiked) {
                                colorScheme.primaryContainer
                            } else {
                                colorScheme.surfaceContainerHigh
                            },
                        contentColor =
                            if (isLiked) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                    ),
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector =
                        if (isLiked) {
                            MetrolistIcons.Rounded.Favorite
                        } else {
                            MetrolistIcons.Rounded.FavoriteBorder
                        },
                    contentDescription = stringResource(if (isLiked) Res.string.unlike else Res.string.action_like),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (track.isExplicit || isDownloaded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (track.isExplicit) {
                    StatusBadgeChip(
                        icon = SongStatusIcons.Explicit,
                        label = stringResource(Res.string.explicit),
                        colorScheme = colorScheme,
                    )
                }
                if (isDownloaded) {
                    StatusBadgeChip(
                        icon = SongStatusIcons.Downloaded,
                        label = stringResource(Res.string.downloaded),
                        colorScheme = colorScheme,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickActionButton(
                icon = MetrolistIcons.AutoMirrored.Outlined.PlaylistAdd,
                contentDescription = stringResource(Res.string.add_to_playlist),
                colorScheme = colorScheme,
                onClick = onAddToPlaylist,
                modifier = Modifier.weight(1f),
            )
            QuickActionButton(
                icon = MetrolistIcons.Outlined.Share,
                contentDescription = stringResource(Res.string.share),
                colorScheme = colorScheme,
                onClick = onShare,
                modifier = Modifier.weight(1f),
            )
            QuickActionButton(
                icon = MetrolistIcons.Outlined.Info,
                contentDescription = stringResource(Res.string.details),
                colorScheme = colorScheme,
                onClick = onShowDetails,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatusBadgeChip(
    icon: ImageVector,
    label: String,
    colorScheme: ColorScheme,
) {
    Surface(
        color = colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    contentDescription: String,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(10.dp),
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun NowPlayingAlbumCard(
    albumName: String,
    year: Int?,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = colorScheme.secondaryContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(
                    imageVector = MetrolistIcons.Outlined.Album,
                    contentDescription = null,
                    tint = colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(8.dp).size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.album),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (year != null && year > 0) {
                    Text(
                        text = "${stringResource(Res.string.now_playing_year_label)}: $year",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = MetrolistIcons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NowPlayingStreamInfoCard(
    track: SongItem,
    stream: PlaybackResource?,
    colorScheme: ColorScheme,
) {
    val codec = stream?.codecs?.takeIf { it.isNotBlank() }
    val bitrateKbps = stream?.bitrate?.takeIf { it > 0 }?.div(1000)
    val sampleRate = stream?.sampleRate?.takeIf { it > 0 }
    val standaloneReleaseYear = track.year?.takeIf { it > 0 && track.album == null }
    if (codec == null && bitrateKbps == null && sampleRate == null && standaloneReleaseYear == null) {
        return
    }

    Surface(
        color = colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            codec?.let {
                StreamMetaPill(
                    label = stringResource(Res.string.codecs),
                    value = it.uppercase(),
                    colorScheme = colorScheme,
                )
            }
            bitrateKbps?.let {
                StreamMetaPill(
                    label = stringResource(Res.string.bitrate),
                    value = stringResource(Res.string.bitrate_value, it),
                    colorScheme = colorScheme,
                )
            }
            sampleRate?.let {
                StreamMetaPill(
                    label = stringResource(Res.string.sample_rate),
                    value = stringResource(Res.string.sample_rate_value, it),
                    colorScheme = colorScheme,
                )
            }
            standaloneReleaseYear?.let { releaseYear ->
                StreamMetaPill(
                    label = stringResource(Res.string.now_playing_year_label),
                    value = releaseYear.toString(),
                    colorScheme = colorScheme,
                )
            }
        }
    }
}

@Composable
private fun StreamMetaPill(
    label: String,
    value: String,
    colorScheme: ColorScheme,
) {
    Surface(
        color = colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun NowPlayingLyricsTeaserCard(
    track: SongItem,
    durationMs: Long,
    isLive: Boolean,
    isVisible: Boolean,
    colorScheme: ColorScheme,
    onOpenLyrics: () -> Unit,
) {
    LaunchedEffect(
        isVisible,
        track.id,
        Settings.shared.lyricsProviderOrder,
        Settings.shared.lyricsEnabledProviders,
    ) {
        if (isVisible && LyricsState.currentLyricsSongId != track.id && !LyricsState.isLyricsLoading) {
            LyricsState.fetchLyrics(track)
        }
    }

    val isMatchingLyrics = LyricsState.currentLyricsSongId == track.id
    val entries = if (isMatchingLyrics) LyricsState.currentLyricsEntries else emptyList()
    val rawLyrics = if (isMatchingLyrics) LyricsState.currentLyrics else null
    val syncedPositionMs = rememberPlayerProgressPosition(durationMs = durationMs, widthPx = 400, isLive = isLive)
    val offsetMs = LyricsState.currentLyricsOffsetMs

    val activeIndex by remember(entries, syncedPositionMs.longValue, offsetMs) {
        derivedStateOf { findActiveLyricIndex(entries, syncedPositionMs.longValue + offsetMs) }
    }

    val previewLines =
        remember(entries, rawLyrics, activeIndex) {
            buildLyricsPreviewLines(entries = entries, rawLyrics = rawLyrics, activeIndex = activeIndex, maxLines = 3)
        }

    Surface(
        onClick = onOpenLyrics,
        color = colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = MetrolistIcons.Outlined.Lyrics,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.lyrics),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(Res.string.now_playing_show_lyrics),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = MetrolistIcons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }

            if (previewLines.isNotEmpty()) {
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))
                previewLines.forEachIndexed { idx, line ->
                    Text(
                        text = line,
                        style =
                            if (idx == 0) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                        fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (idx == 0) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingUpNextCard(
    queueEntries: List<MusicQueueEntry>,
    currentIndex: Int,
    relatedSongs: List<SongItem>,
    colorScheme: ColorScheme,
    onOpenQueue: () -> Unit,
) {
    val upcomingEntries =
        if (currentIndex in queueEntries.indices) {
            queueEntries.drop(currentIndex + 1).take(3)
        } else {
            emptyList()
        }
    val fallbackRelated = if (upcomingEntries.isEmpty()) relatedSongs.take(2) else emptyList()

    Surface(
        color = colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenQueue),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = MetrolistIcons.AutoMirrored.Outlined.QueueMusic,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.up_next),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    if (currentIndex >= 0 && queueEntries.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.now_playing_track_number, currentIndex + 1, queueEntries.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.now_playing_open_queue),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = MetrolistIcons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }

            if (upcomingEntries.isNotEmpty()) {
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))
                upcomingEntries.forEach { entry ->
                    UpNextSongRow(
                        song = entry.song,
                        colorScheme = colorScheme,
                        onClick = { PlayerState.playQueueOccurrence(entry.key) },
                    )
                }
            } else if (fallbackRelated.isNotEmpty()) {
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))
                fallbackRelated.forEach { song ->
                    UpNextSongRow(
                        song = song,
                        colorScheme = colorScheme,
                        onClick = { PlayerState.playTrack(song, relatedSongs) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpNextSongRow(
    song: SongItem,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            url = song.previewThumbnailUrl ?: song.thumbnail.orEmpty(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.artists.isNotEmpty()) {
                Text(
                    text = song.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        song.duration?.takeIf { it > 0L }?.let { seconds ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatDuration(seconds * 1000L),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun findActiveLyricIndex(
    entries: List<LyricsEntry>,
    positionMs: Long,
): Int {
    if (entries.isEmpty()) return -1
    var active = -1
    for (i in entries.indices) {
        if (entries[i].time <= positionMs) {
            active = i
        } else {
            break
        }
    }
    return active.coerceAtLeast(0)
}

internal fun buildLyricsPreviewLines(
    entries: List<LyricsEntry>,
    rawLyrics: String?,
    activeIndex: Int,
    maxLines: Int = 3,
): List<String> {
    if (entries.isNotEmpty()) {
        val start = if (activeIndex in entries.indices) activeIndex else 0
        return entries
            .drop(start)
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .take(maxLines)
    }
    if (rawLyrics.isNullOrBlank()) return emptyList()
    return rawLyrics
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("[") }
        .take(maxLines)
        .toList()
}
