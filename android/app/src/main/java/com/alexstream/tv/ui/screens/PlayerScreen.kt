@file:OptIn(ExperimentalComposeUiApi::class)

package com.alexstream.tv.ui.screens

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.graphics.Color as AndroidColor
import android.util.TypedValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.core.content.res.ResourcesCompat
import com.alexstream.tv.PlaybackProgressStore
import com.alexstream.tv.R
import com.alexstream.tv.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/** An external subtitle track to sideload, fetched from /api/subtitles. */
data class SubtitleSpec(
    val url: String,
    val label: String,
    val language: String
)

@Composable
fun PlayerScreen(
    streamUrl: String,
    mediaPath: String,
    title: String,
    initialResumePositionMs: Long,
    subtitles: List<SubtitleSpec> = emptyList(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setTunnelingEnabled(true)
                .build()
        }
    }
    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                50_000,   // minBufferMs – buffer at least 50s ahead
                120_000,  // maxBufferMs – allow up to 2 min buffer
                2_500,    // playbackStart – start after 2.5s buffered
                5_000     // rebuffer – resume after 5s buffered
            )
            .setBackBuffer(30_000, false)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isBuffering by remember { mutableStateOf(true) }
    var showBufferingSpinner by remember { mutableStateOf(false) }
    var hasPlaybackReachedReady by remember(streamUrl) { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var retryToken by remember { mutableLongStateOf(0L) }
    var pendingInitialSeekMs by remember(streamUrl, mediaPath) {
        mutableLongStateOf(initialResumePositionMs.coerceAtLeast(0L))
    }
    var showCaptionMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    val isMenuOpen = showCaptionMenu || showAudioMenu
    val scope = rememberCoroutineScope()
    
    val seekbarFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val rewindFocusRequester = remember { FocusRequester() }
    val forwardFocusRequester = remember { FocusRequester() }
    val captionFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val screenFocusRequester = remember { FocusRequester() }
    var resumeOnStart by remember { mutableStateOf(false) }
    var wasPausedAtMs by remember { mutableLongStateOf(-1L) }
    var actionFeedback by remember { mutableStateOf<PlayerActionFeedback?>(null) }
    var playWhenReadyState by remember { mutableStateOf(true) }

    var pendingResumeSeekMs by remember { mutableLongStateOf(-1L) }
    val isPlaybackIntended = isPlaying || playWhenReadyState

    fun showActionFeedback(type: PlayerActionFeedbackType) {
        actionFeedback = PlayerActionFeedback(type)
    }

    fun playerPause() {
        showActionFeedback(PlayerActionFeedbackType.Pause)
        wasPausedAtMs = exoPlayer.currentPosition
        exoPlayer.pause()
    }

    fun playerPlay() {
        showActionFeedback(PlayerActionFeedbackType.Play)
        if (wasPausedAtMs >= 0L) {
            // Seek to the exact pause position to flush decoder buffers and realign
            // audio+video clocks. Store it so STATE_READY handler can call play()
            // only after the seek completes — this avoids both jitter and desync.
            pendingResumeSeekMs = wasPausedAtMs
            exoPlayer.seekTo(wasPausedAtMs)
            exoPlayer.playWhenReady = true
            wasPausedAtMs = -1L
        } else {
            exoPlayer.play()
        }
    }

    fun persistPlaybackProgress() {
        if (mediaPath.isBlank()) return
        PlaybackProgressStore.saveProgress(
            context = context,
            mediaPath = mediaPath,
            title = title,
            positionMs = exoPlayer.currentPosition,
            durationMs = exoPlayer.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
        )
    }

    fun dismissTrackMenus() {
        showCaptionMenu = false
        showAudioMenu = false
        showControls = true
        lastInteraction = System.currentTimeMillis()
        playPauseFocusRequester.requestFocus()
    }

    BackHandler(enabled = isMenuOpen) {
        dismissTrackMenus()
    }

    // Listen to exoPlayer play state changes
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playWhenReadyState = playWhenReady
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.message ?: "Playback error"
                if (retryCount < 5) {
                    retryCount += 1
                    scope.launch {
                        delay(1000L * retryCount)
                        retryToken = System.currentTimeMillis()
                    }
                } else {
                    scope.launch {
                        delay(1500)
                        onClose()
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        hasPlaybackReachedReady = true
                        wasPausedAtMs = -1L
                        if (playbackError != null) {
                            playbackError = null
                        }
                        if (pendingInitialSeekMs > 0L) {
                            val rawDuration = exoPlayer.duration
                            val seekTarget = if (rawDuration > 0L && rawDuration != C.TIME_UNSET) {
                                pendingInitialSeekMs.coerceIn(0L, rawDuration)
                            } else {
                                pendingInitialSeekMs
                            }
                            exoPlayer.seekTo(seekTarget)
                            pendingInitialSeekMs = 0L
                        } else if (pendingResumeSeekMs >= 0L) {
                            // Resume seek completed — start playback now that decoders are realigned
                            pendingResumeSeekMs = -1L
                            exoPlayer.play()
                        }
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        PlaybackProgressStore.clearProgress(context, mediaPath)
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = true
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            persistPlaybackProgress()
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    persistPlaybackProgress()
                    resumeOnStart = exoPlayer.isPlaying
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (resumeOnStart && playbackError == null) {
                        exoPlayer.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(streamUrl, retryToken) {
        playbackError = null
        val shouldResume = if (retryToken != 0L) exoPlayer.playWhenReady else true
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        if (streamUrl.isNotBlank()) {
            val subConfigs = subtitles.mapNotNull { sub ->
                if (sub.url.isBlank()) return@mapNotNull null
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(sub.language.ifBlank { null })
                    .setLabel(sub.label.ifBlank { null })
                    .build()
            }
            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setSubtitleConfigurations(subConfigs)
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = shouldResume
        } else {
            playbackError = "Stream unavailable"
        }
    }

    LaunchedEffect(streamUrl) {
        // Reset track overrides when loading a new item to avoid stale audio selections.
        resetTrackOverrides(trackSelector)
        retryCount = 0
        retryToken = 0L
        pendingInitialSeekMs = initialResumePositionMs.coerceAtLeast(0L)
        hasPlaybackReachedReady = false
        showBufferingSpinner = false
        showCaptionMenu = false
        showAudioMenu = false
    }

    LaunchedEffect(Unit) {
        screenFocusRequester.requestFocus()
    }

    LaunchedEffect(lastInteraction, showControls, isMenuOpen) {
        if (showControls && !isMenuOpen) {
            delay(3000)
            if (System.currentTimeMillis() - lastInteraction >= 3000) {
                showControls = false
                screenFocusRequester.requestFocus() // Steal focus so hidden controls don't keep it
            }
        }
    }

    LaunchedEffect(showControls, isMenuOpen) {
        if (showControls && !isMenuOpen) {
            playPauseFocusRequester.requestFocus() // Return focus to controls when shown
        }
    }

    LaunchedEffect(isBuffering, playbackError, isMenuOpen, hasPlaybackReachedReady) {
        val canShowSpinner = isBuffering && playbackError == null && !isMenuOpen
        if (!canShowSpinner) {
            showBufferingSpinner = false
            return@LaunchedEffect
        }

        delay(if (hasPlaybackReachedReady) 650 else 150)
        if (isBuffering && playbackError == null && !isMenuOpen) {
            showBufferingSpinner = true
        }
    }

    LaunchedEffect(exoPlayer, mediaPath, title) {
        if (mediaPath.isBlank()) return@LaunchedEffect
        while (isActive) {
            delay(5_000)
            persistPlaybackProgress()
        }
    }

    // Wrap AndroidView in a focusable box to capture D-Pad events when controls are hidden
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(screenFocusRequester)
            .focusable(!isMenuOpen)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    if (isMenuOpen) {
                        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                            dismissTrackMenus()
                            return@onKeyEvent true
                        }
                        // Let the menu handle navigation keys (D-pad up/down).
                        return@onKeyEvent false
                    }
                    when (keyCode) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                            if (showControls) {
                                showControls = false
                                screenFocusRequester.requestFocus()
                                true
                            } else {
                                onClose()
                                true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_SPACE -> {
                            lastInteraction = System.currentTimeMillis()
                            if (!showControls) {
                                showControls = true
                            }
                            if (playbackError == null) {
                                if (isPlaybackIntended) playerPause() else playerPlay()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            lastInteraction = System.currentTimeMillis()
                            if (!showControls) {
                                val offset = if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -10000L else 10000L
                                if (playbackError == null) {
                                    val rawDuration = exoPlayer.duration
                                    val safeDuration = if (rawDuration > 0 && rawDuration != C.TIME_UNSET) rawDuration else Long.MAX_VALUE
                                    val next = (exoPlayer.currentPosition + offset).coerceAtLeast(0L).coerceAtMost(safeDuration)
                                    showActionFeedback(
                                        if (offset < 0) PlayerActionFeedbackType.Rewind else PlayerActionFeedbackType.Forward
                                    )
                                    exoPlayer.seekTo(next)
                                }
                                true
                            } else {
                                false
                            }
                        }
                        else -> {
                            lastInteraction = System.currentTimeMillis()
                            if (!showControls) {
                                showControls = true
                                true
                            } else {
                                false
                            }
                        }
                    }
                } else {
                    false
                }
            }
    ) {
        AndroidView(
            factory = {
                val subtitleTypeface = ResourcesCompat.getFont(it, R.font.sf_pro_rounded_regular)
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = isPlaying
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    subtitleView?.apply {
                        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setStyle(
                            CaptionStyleCompat(
                                AndroidColor.WHITE,
                                AndroidColor.BLACK,
                                AndroidColor.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_NONE,
                                AndroidColor.BLACK,
                                subtitleTypeface
                            )
                        )
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.keepScreenOn = isPlaying
            },
            modifier = Modifier
                .fillMaxSize()
                .focusable(false)
        )

        if (playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = playbackError ?: "Playback error",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = SfProRounded
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Press Back to exit",
                        color = Color(0xB3FFFFFF),
                        fontSize = 12.sp,
                        fontFamily = SfProRounded
                    )
                }
            }
        }

        if (showBufferingSpinner) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PlayerBufferingSpinner()
            }
        }

        PlayerActionFeedbackOverlay(
            feedback = actionFeedback,
            onFinished = { finishedId ->
                if (actionFeedback?.id == finishedId) {
                    actionFeedback = null
                }
            }
        )

        PlayerControlsOverlay(
            exoPlayer = exoPlayer,
            title = title,
            isPlaying = isPlaybackIntended,
            showControls = showControls,
            isMenuOpen = isMenuOpen,
            onInteraction = { lastInteraction = System.currentTimeMillis() },
            onTogglePlayPause = {
                lastInteraction = System.currentTimeMillis()
                if (isPlaybackIntended) playerPause() else playerPlay()
            },
            onSeekBy = { offsetMs ->
                lastInteraction = System.currentTimeMillis()
                val rawDuration = exoPlayer.duration
                val safeDuration = if (rawDuration > 0 && rawDuration != C.TIME_UNSET) rawDuration else Long.MAX_VALUE
                val next = (exoPlayer.currentPosition + offsetMs).coerceAtLeast(0L).coerceAtMost(safeDuration)
                showActionFeedback(
                    if (offsetMs < 0) PlayerActionFeedbackType.Rewind else PlayerActionFeedbackType.Forward
                )
                exoPlayer.seekTo(next)
            },
            onShowCaptions = {
                lastInteraction = System.currentTimeMillis()
                showCaptionMenu = true
                showAudioMenu = false
                showControls = true
            },
            onShowAudio = {
                lastInteraction = System.currentTimeMillis()
                showAudioMenu = true
                showCaptionMenu = false
                showControls = true
            },
            seekbarFocusRequester = seekbarFocusRequester,
            playPauseFocusRequester = playPauseFocusRequester,
            rewindFocusRequester = rewindFocusRequester,
            forwardFocusRequester = forwardFocusRequester,
            captionFocusRequester = captionFocusRequester,
            audioFocusRequester = audioFocusRequester
        )

        if (isMenuOpen) {
            val menuType = if (showCaptionMenu) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO
            val menuTitle = if (showCaptionMenu) "Subtitles" else "Audio"
            TrackSelectionMenu(
                title = menuTitle,
                exoPlayer = exoPlayer,
                trackSelector = trackSelector,
                trackType = menuType,
                onDismiss = {
                    showCaptionMenu = false
                    showAudioMenu = false
                    lastInteraction = System.currentTimeMillis()
                    playPauseFocusRequester.requestFocus()
                }
            )
        }
    }
}

@Composable
private fun PlayerBufferingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    val transition = rememberInfiniteTransition(label = "playerBufferingSpinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 760, easing = LinearEasing)
        ),
        label = "playerBufferingRotation"
    )

    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = 2.75.dp.toPx()
        val inset = strokeWidth / 2f
        val arcSize = Size(
            width = this.size.width - strokeWidth,
            height = this.size.height - strokeWidth
        )
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        val radius = (minOf(this.size.width, this.size.height) - strokeWidth) / 2f

        drawCircle(
            color = Color.White.copy(alpha = 0.14f),
            radius = radius,
            style = stroke
        )

        rotate(degrees = rotation) {
            drawArc(
                color = Color.White.copy(alpha = 0.42f),
                startAngle = 0f,
                sweepAngle = 62f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = Color.White.copy(alpha = 0.86f),
                startAngle = -86f,
                sweepAngle = 88f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
        }
    }
}

@Composable
private fun BoxScope.PlayerControlsOverlay(
    exoPlayer: ExoPlayer,
    title: String,
    isPlaying: Boolean,
    showControls: Boolean,
    isMenuOpen: Boolean,
    onInteraction: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onShowCaptions: () -> Unit,
    onShowAudio: () -> Unit,
    seekbarFocusRequester: FocusRequester,
    playPauseFocusRequester: FocusRequester,
    rewindFocusRequester: FocusRequester,
    forwardFocusRequester: FocusRequester,
    captionFocusRequester: FocusRequester,
    audioFocusRequester: FocusRequester
) {
    val controlsAlpha by animateFloatAsState(
        targetValue = if (showControls) 1f else 0f,
        animationSpec = tween(durationMillis = 220)
    )
    val bottomScrimColors = remember {
        listOf(
            Color.Transparent,
            Color(0x12000000),
            Color(0x52000000),
            Color(0x99000000),
            Color(0xC7000000)
        )
    }
    val topScrimColors = remember {
        listOf(
            Color(0xB3000000),
            Color.Transparent
        )
    }

    val controlsVisible = showControls && !isMenuOpen

    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(208.dp)
            .alpha(controlsAlpha)
            .playerVerticalScrim(bottomScrimColors)
    )

    PlayerBottomControls(
        exoPlayer = exoPlayer,
        isPlaying = isPlaying,
        controlsAlpha = controlsAlpha,
        controlsVisible = controlsVisible,
        onInteraction = onInteraction,
        onTogglePlayPause = onTogglePlayPause,
        onSeekBy = onSeekBy,
        onShowCaptions = onShowCaptions,
        onShowAudio = onShowAudio,
        seekbarFocusRequester = seekbarFocusRequester,
        playPauseFocusRequester = playPauseFocusRequester,
        rewindFocusRequester = rewindFocusRequester,
        forwardFocusRequester = forwardFocusRequester,
        captionFocusRequester = captionFocusRequester,
        audioFocusRequester = audioFocusRequester
    )

    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .height(88.dp)
            .alpha(controlsAlpha)
            .playerVerticalScrim(topScrimColors)
    )

    Text(
        text = title,
        color = TextColor,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = SfProRounded,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .fillMaxWidth()
            .alpha(controlsAlpha)
    )
}

@Composable
private fun BoxScope.PlayerBottomControls(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    controlsAlpha: Float,
    controlsVisible: Boolean,
    onInteraction: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onShowCaptions: () -> Unit,
    onShowAudio: () -> Unit,
    seekbarFocusRequester: FocusRequester,
    playPauseFocusRequester: FocusRequester,
    rewindFocusRequester: FocusRequester,
    forwardFocusRequester: FocusRequester,
    captionFocusRequester: FocusRequester,
    audioFocusRequester: FocusRequester
) {
    var currentPositionMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var durationMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var scrubPositionMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var isScrubbing by remember(exoPlayer) { mutableStateOf(false) }
    var resumeAfterScrub by remember(exoPlayer) { mutableStateOf(false) }

    fun syncPlayerTimelineState() {
        currentPositionMs = exoPlayer.currentPosition
        bufferedPositionMs = exoPlayer.bufferedPosition
        val rawDuration = exoPlayer.duration
        durationMs = if (rawDuration > 0 && rawDuration != C.TIME_UNSET) rawDuration else 0L
    }

    LaunchedEffect(exoPlayer) {
        syncPlayerTimelineState()
    }

    LaunchedEffect(exoPlayer, controlsVisible, isScrubbing) {
        while (isActive) {
            if (!controlsVisible || isScrubbing) {
                delay(80)
                continue
            }

            syncPlayerTimelineState()
            delay(if (exoPlayer.isPlaying) 50 else 150)
        }
    }

    LaunchedEffect(controlsVisible) {
        syncPlayerTimelineState()
        if (!controlsVisible) {
            isScrubbing = false
        }
    }

    val displayedPositionMs = if (isScrubbing) scrubPositionMs else currentPositionMs

    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .alpha(controlsAlpha)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        PlayerSeekBar(
            positionMs = displayedPositionMs,
            durationMs = durationMs,
            bufferedPositionMs = bufferedPositionMs,
            isSeekable = durationMs > 0L && exoPlayer.isCurrentMediaItemSeekable,
            onInteraction = onInteraction,
            controlsEnabled = controlsVisible,
            focusRequester = seekbarFocusRequester,
            downRequester = playPauseFocusRequester,
            onSeekStart = {
                if (!isScrubbing) {
                    resumeAfterScrub = exoPlayer.isPlaying
                    if (resumeAfterScrub) {
                        exoPlayer.pause()
                    }
                    isScrubbing = true
                    scrubPositionMs = currentPositionMs
                }
            },
            onSeekPreview = { previewMs ->
                scrubPositionMs = previewMs
            },
            onSeekCommit = { finalPosition ->
                scrubPositionMs = finalPosition
                currentPositionMs = finalPosition
                isScrubbing = false
                exoPlayer.seekTo(finalPosition)
                if (resumeAfterScrub) {
                    exoPlayer.play()
                }
                resumeAfterScrub = false
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerButton(
                    icon = if (isPlaying) PlayerIcons.Pause else PlayerIcons.Play,
                    onClick = onTogglePlayPause,
                    isPrimary = true,
                    modifier = Modifier.focusRequester(playPauseFocusRequester),
                    focusProps = {
                        up = seekbarFocusRequester
                        right = rewindFocusRequester
                        left = FocusRequester.Cancel
                    },
                    enabled = controlsVisible
                )
                Spacer(modifier = Modifier.width(12.dp))
                PlayerButton(
                    icon = PlayerIcons.Rewind,
                    onClick = { onSeekBy(-10_000L) },
                    modifier = Modifier.focusRequester(rewindFocusRequester),
                    focusProps = {
                        up = seekbarFocusRequester
                        left = playPauseFocusRequester
                        right = forwardFocusRequester
                    },
                    enabled = controlsVisible
                )
                Spacer(modifier = Modifier.width(12.dp))
                PlayerButton(
                    icon = PlayerIcons.Forward,
                    onClick = { onSeekBy(10_000L) },
                    modifier = Modifier.focusRequester(forwardFocusRequester),
                    focusProps = {
                        up = seekbarFocusRequester
                        left = rewindFocusRequester
                        right = captionFocusRequester
                    },
                    enabled = controlsVisible
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerButton(
                    icon = PlayerIcons.Caption,
                    onClick = onShowCaptions,
                    modifier = Modifier.focusRequester(captionFocusRequester),
                    focusProps = {
                        up = seekbarFocusRequester
                        left = forwardFocusRequester
                        right = audioFocusRequester
                    },
                    enabled = controlsVisible
                )
                Spacer(modifier = Modifier.width(12.dp))
                PlayerButton(
                    icon = PlayerIcons.Audio,
                    onClick = onShowAudio,
                    modifier = Modifier.focusRequester(audioFocusRequester),
                    focusProps = {
                        up = seekbarFocusRequester
                        left = captionFocusRequester
                        right = FocusRequester.Cancel
                    },
                    enabled = controlsVisible
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${if (durationMs > 0L) formatTime(displayedPositionMs, durationMs) else "--:--"} / ${formatTimeOrUnknown(durationMs, durationMs)}",
                    color = Color(0xE6FFFFFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = SfProRounded,
                    textAlign = TextAlign.End,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier.widthIn(min = 108.dp),
                    maxLines = 1
                )
            }
        }
    }
}

private fun Modifier.playerVerticalScrim(colors: List<Color>): Modifier = drawWithCache {
    val brush = Brush.verticalGradient(
        colors = colors,
        startY = 0f,
        endY = size.height
    )
    onDrawBehind {
        drawRect(brush = brush)
    }
}

// Extract seekbar to scope state reads and prevent whole-screen recomposition
@Composable
private fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    isSeekable: Boolean,
    onInteraction: () -> Unit,
    controlsEnabled: Boolean,
    focusRequester: FocusRequester,
    downRequester: FocusRequester,
    onSeekStart: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekCommit: (Long) -> Unit
) {
    var isProgressFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var seekJob by remember { mutableStateOf<Job?>(null) }
    var seekDirection by remember { mutableIntStateOf(0) }
    var seekStartNanos by remember { mutableLongStateOf(0L) }
    var didSeekDuringHold by remember { mutableStateOf(false) }
    var previewPositionMs by remember { mutableLongStateOf(positionMs) }

    DisposableEffect(Unit) {
        onDispose {
            seekJob?.cancel()
            seekJob = null
        }
    }

    fun cancelActiveSeek(resetPreview: Boolean) {
        seekJob?.cancel()
        seekJob = null
        seekStartNanos = 0L
        didSeekDuringHold = false
        if (resetPreview) {
            previewPositionMs = positionMs
        }
    }

    LaunchedEffect(positionMs, seekJob) {
        if (seekJob == null) {
            previewPositionMs = positionMs
        }
    }

    LaunchedEffect(controlsEnabled) {
        if (!controlsEnabled) {
            cancelActiveSeek(resetPreview = true)
        }
    }

    val isDurationKnown = durationMs > 0L
    val barHeight = 4.dp
    val dotSize by animateDpAsState(targetValue = if (isProgressFocused) 14.dp else 10.dp)

    val progress = if (isDurationKnown) previewPositionMs.toFloat() / durationMs.toFloat() else 0f
    val bufferedProgress = if (isDurationKnown) bufferedPositionMs.toFloat() / durationMs.toFloat() else 0f
    val clampedProgress = progress.coerceIn(0f, 1f)
    val clampedBufferedProgress = bufferedProgress.coerceIn(0f, 1f)
    val containerHeight = 16.dp

    val canvasModifier = Modifier
        .fillMaxWidth()
        .height(containerHeight)
        .focusRequester(focusRequester)
        .focusProperties { down = downRequester }
        .onFocusChanged {
            if (isProgressFocused != it.isFocused) {
                isProgressFocused = it.isFocused
            }
            if (!it.isFocused) {
                cancelActiveSeek(resetPreview = true)
            }
            if (it.isFocused) onInteraction()
        }
        .onKeyEvent { keyEvent ->
            onInteraction()

            val isLeft = keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
            val isRight = keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            if (!isSeekable) return@onKeyEvent false

            if (isLeft || isRight) {
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val newDirection = if (isLeft) -1 else 1
                    if (seekJob != null && seekDirection == newDirection) {
                        return@onKeyEvent true
                    }
                    if (seekJob == null) {
                        onSeekStart()
                    }
                    val hadActiveSeek = seekJob != null
                    seekJob?.cancel()
                    seekDirection = newDirection
                    seekStartNanos = System.nanoTime()
                    didSeekDuringHold = false
                    previewPositionMs = if (hadActiveSeek) previewPositionMs else positionMs
                    seekJob = scope.launch {
                        delay(70)
                        var lastFrameNanos = withFrameNanos { it }
                        var frameCount = 0
                        var lastInteractionTime = 0L
                        while (isActive) {
                            val frameNanos = withFrameNanos { it }
                            val deltaMs = ((frameNanos - lastFrameNanos) / 1_000_000f).coerceIn(8f, 40f)
                            lastFrameNanos = frameNanos

                            // Determine seek stage based on total hold time
                            val heldMs = ((frameNanos - seekStartNanos) / 1_000_000f).coerceAtLeast(0f)
                            val pctPerSecond = when {
                                heldMs < 1000f -> 3.0f     // 3% per second (fine scrub)
                                heldMs < 3000f -> 10.0f    // 10% per second (normal scan)
                                else -> 30.0f              // 30% per second (fast scan)
                            }

                            // Convert pct/second to pct-per-frame, then to ms
                            val delta = (pctPerSecond / 100f) * durationMs * (deltaMs / 1000f) * seekDirection
                            val next = (previewPositionMs + delta).roundToLong()
                                .coerceIn(0L, durationMs)
                            if (next != previewPositionMs) {
                                didSeekDuringHold = true
                                frameCount++
                                if (frameCount % 2 == 0) {
                                    previewPositionMs = next
                                    onSeekPreview(next)
                                    val now = System.currentTimeMillis()
                                    if (now - lastInteractionTime > 500) {
                                        onInteraction()
                                        lastInteractionTime = now
                                    }
                                }
                            }
                        }
                    }
                    true
                } else if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    val activeJob = seekJob
                    seekJob = null
                    activeJob?.cancel()
                    val finalPosition = if (didSeekDuringHold) {
                        previewPositionMs
                    } else {
                        (positionMs + (10_000L * seekDirection)).coerceIn(0L, durationMs)
                    }
                    seekStartNanos = 0L
                    didSeekDuringHold = false
                    previewPositionMs = finalPosition
                    onSeekCommit(finalPosition)
                    true
                } else {
                    false
                }
            } else false
        }
        .focusable(controlsEnabled)
        .padding(vertical = 4.dp)

    Canvas(modifier = canvasModifier) {
        val trackTop = (size.height - barHeight.toPx()) / 2f
        val barHeightPx = barHeight.toPx()
        val dotSizePx = dotSize.toPx()
        val cornerRadius = barHeightPx / 2f

        val trackRect = androidx.compose.ui.geometry.Rect(
            left = 0f,
            top = trackTop,
            right = size.width,
            bottom = trackTop + barHeightPx
        )
        drawRoundRect(
            color = ProgressTrack,
            topLeft = trackRect.topLeft,
            size = androidx.compose.ui.geometry.Size(trackRect.width, trackRect.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
        )

        val bufferedWidth = size.width * clampedBufferedProgress
        if (bufferedWidth > 0f) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = trackRect.topLeft,
                size = androidx.compose.ui.geometry.Size(bufferedWidth, trackRect.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
            )
        }

        val progressWidth = size.width * clampedProgress
        if (progressWidth > 0f) {
            drawRoundRect(
                color = ProgressFill,
                topLeft = trackRect.topLeft,
                size = androidx.compose.ui.geometry.Size(progressWidth, trackRect.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
            )
        }

        if (isProgressFocused) {
            val dotCenterX = progressWidth.coerceIn(0f, size.width)
            val dotCenterY = size.height / 2f
            drawCircle(
                color = Color.White,
                radius = dotSizePx / 2f,
                center = androidx.compose.ui.geometry.Offset(dotCenterX, dotCenterY)
            )
        }
    }
}

@Composable
private fun PlayerButton(
    icon: ImageVector,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusProps: (androidx.compose.ui.focus.FocusProperties.() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused && !isPrimary) 1.1f else 1f)

    Box(
        modifier = modifier
            .scale(scale)
            .size(44.dp)
            .clip(CircleShape)
            .focusProperties { focusProps?.invoke(this) }
            .onFocusChanged {
                if (isFocused != it.isFocused) {
                    isFocused = it.isFocused
                }
            }
            .focusable(enabled)
            .onKeyEvent { keyEvent ->
                if (!enabled) return@onKeyEvent false
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_SPACE -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .clickable(enabled = enabled) { onClick() }
            .background(if (isFocused) Color.White else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

private data class TrackOption(
    val label: String,
    val trackIndex: Int?,
    val groupIndex: Int?,
    val isSelected: Boolean,
    val isSupported: Boolean,
    val isOff: Boolean = false,
    val isAuto: Boolean = false
)

private fun trackLabel(format: Format, index: Int, trackType: Int): String {
    val language = format.language?.trim().orEmpty()
    if (language.isNotBlank() && language.lowercase() != "und") {
        return language.lowercase()
    }
    val label = format.label?.trim().orEmpty()
    if (label.isNotBlank()) return label
    return if (trackType == C.TRACK_TYPE_TEXT) "Subtitle ${index + 1}" else "Track ${index + 1}"
}

@Suppress("DEPRECATION")
private fun resetTrackOverrides(trackSelector: DefaultTrackSelector) {
    trackSelector.parameters = trackSelector.parameters.buildUpon()
        .clearSelectionOverrides()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .build()
}

@Composable
@Suppress("DEPRECATION")
private fun TrackSelectionMenu(
    title: String,
    exoPlayer: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    trackType: Int,
    onDismiss: () -> Unit
) {
    val tracks = exoPlayer.currentTracks
    val mapped = trackSelector.currentMappedTrackInfo
    val rendererIndex = mapped?.run {
        (0 until rendererCount).firstOrNull { getRendererType(it) == trackType }
    }
    val isTypeDisabled = rendererIndex?.let { trackSelector.parameters.getRendererDisabled(it) } ?: false
    val trackGroups = if (rendererIndex != null) mapped.getTrackGroups(rendererIndex) else null
    val selectionOverride = remember(rendererIndex, trackGroups, trackSelector.parameters) {
        if (rendererIndex != null && trackGroups != null) {
            trackSelector.parameters.getSelectionOverride(rendererIndex, trackGroups)
        } else null
    }
    val hasExplicitOverride = selectionOverride != null
    val options = remember(tracks, trackType, isTypeDisabled, hasExplicitOverride, trackGroups) {
        val built = mutableListOf<TrackOption>()
        val autoSelected = !isTypeDisabled && !hasExplicitOverride
        built.add(
            TrackOption(
                label = "Auto",
                trackIndex = null,
                groupIndex = null,
                isSelected = autoSelected,
                isSupported = true,
                isAuto = true
            )
        )
        if (trackType == C.TRACK_TYPE_TEXT || trackType == C.TRACK_TYPE_AUDIO) {
            built.add(
                TrackOption(
                    label = "Off",
                    trackIndex = null,
                    groupIndex = null,
                    isSelected = isTypeDisabled,
                    isSupported = true,
                    isOff = true
                )
            )
        }
        tracks.groups.forEach { group ->
            if (group.type != trackType) return@forEach
            val trackGroup = group.mediaTrackGroup
            for (i in 0 until trackGroup.length) {
                val format = trackGroup.getFormat(i)
                val label = trackLabel(format, i, trackType)
                val groupIndex = trackGroups?.let { findGroupIndex(it, trackGroup) }
                val isSupported = group.isTrackSupported(i) && groupIndex != null
                built.add(
                    TrackOption(
                        label = label,
                        trackIndex = i,
                        groupIndex = groupIndex,
                        isSelected = group.isTrackSelected(i) && !isTypeDisabled,
                        isSupported = isSupported
                    )
                )
            }
        }
        built
    }

    val focusRequesters = remember(options.size) { List(options.size) { FocusRequester() } }
    val selectedIndex = options.indexOfFirst { it.isSelected }
    val firstEnabledIndex = options.indexOfFirst { it.isSupported }
    val initialFocusIndex = when {
        selectedIndex >= 0 && options.getOrNull(selectedIndex)?.isSupported == true -> selectedIndex
        firstEnabledIndex >= 0 -> firstEnabledIndex
        else -> 0
    }
    val hasRealTracks = options.any { !it.isOff && !it.isAuto }

    LaunchedEffect(options.size, initialFocusIndex) {
        if (options.isNotEmpty() && initialFocusIndex in options.indices) {
            // Wait a frame so LazyColumn items are placed before requesting focus.
            withFrameNanos { }
            focusRequesters[initialFocusIndex].requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 320.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF101010))
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { /* consume */ }
        ) {
            Text(
                text = title,
                color = TextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = SfProRounded
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (!hasRealTracks) {
                Text(
                    text = "No tracks available",
                    color = Color(0xB3FFFFFF),
                    fontSize = 12.sp,
                    fontFamily = SfProRounded
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.heightIn(max = 260.dp)
            ) {
                itemsIndexed(options) { index, option ->
                    var isFocused by remember { mutableStateOf(false) }
                    val isSelected = option.isSelected
                    val isEnabled = option.isSupported
                    val rowBg = when {
                        isFocused -> Color.White
                        isSelected -> Color(0x1AFFFFFF)
                        else -> Color.Transparent
                    }
                    val textColor = when {
                        !isEnabled -> Color(0x66FFFFFF)
                        isFocused -> Color.Black
                        else -> Color.White
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequesters[index])
                            .focusProperties {
                                if (index == 0) up = FocusRequester.Cancel
                                if (index == options.lastIndex) down = FocusRequester.Cancel
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            }
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable(isEnabled)
                            .onKeyEvent { keyEvent ->
                                if (!isEnabled) return@onKeyEvent false
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                        KeyEvent.KEYCODE_ENTER,
                                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                            applyTrackSelection(trackSelector, trackType, rendererIndex, trackGroups, option)
                                            onDismiss()
                                            true
                                        }
                                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                                            onDismiss()
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .clickable(enabled = isEnabled) {
                                applyTrackSelection(trackSelector, trackType, rendererIndex, trackGroups, option)
                                onDismiss()
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(rowBg)
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.label,
                            color = textColor,
                            fontSize = 13.sp,
                            fontFamily = SfProRounded,
                            maxLines = 1
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = PlayerIcons.CircleCheck,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (index != options.lastIndex) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun applyTrackSelection(
    trackSelector: DefaultTrackSelector,
    trackType: Int,
    rendererIndex: Int?,
    trackGroups: TrackGroupArray?,
    option: TrackOption
) {
    if (!option.isSupported) return
    val rIdx = rendererIndex ?: return
    val groups = trackGroups ?: return
    if (groups.length == 0) return

    val paramsBuilder = trackSelector.parameters.buildUpon()
        .setTrackTypeDisabled(trackType, option.isOff)
        .clearSelectionOverrides(rIdx)

    if (option.isAuto) {
        // Auto = no override, renderer enabled
        paramsBuilder.setTrackTypeDisabled(trackType, false)
    } else if (!option.isOff && option.groupIndex != null && option.trackIndex != null) {
        val groupIndex = option.groupIndex
        val trackIndex = option.trackIndex
        if (groupIndex !in 0 until groups.length) return
        val group = groups[groupIndex]
        if (trackIndex !in 0 until group.length) return
        paramsBuilder.setSelectionOverride(
            rIdx,
            groups,
            DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex)
        )
    }

    trackSelector.parameters = paramsBuilder.build()
}

private fun findGroupIndex(trackGroups: TrackGroupArray, target: TrackGroup): Int? {
    for (i in 0 until trackGroups.length) {
        val group = trackGroups[i]
        if (group === target) return i
        if (group.length != target.length) continue
        var allMatch = true
        for (t in 0 until group.length) {
            if (!formatsSimilar(group.getFormat(t), target.getFormat(t))) {
                allMatch = false
                break
            }
        }
        if (allMatch) return i
    }
    return null
}

private fun formatsSimilar(a: Format, b: Format): Boolean {
    if (a.id != b.id) return false
    if (a.sampleMimeType != b.sampleMimeType) return false
    if (a.language != b.language) return false
    if (a.label != b.label) return false
    if (a.bitrate != b.bitrate) return false
    return true
}

private fun formatTime(milliseconds: Long, durationMs: Long = 0L): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    val showHours = hours > 0 || durationMs >= 3_600_000L
    return if (showHours) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun formatTimeOrUnknown(milliseconds: Long, durationMs: Long = 0L): String {
    if (milliseconds <= 0L) return "--:--"
    return formatTime(milliseconds, durationMs)
}
