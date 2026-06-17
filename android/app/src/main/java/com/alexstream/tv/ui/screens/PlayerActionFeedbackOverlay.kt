package com.alexstream.tv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.alexstream.tv.ui.theme.PlayerIcons
import kotlinx.coroutines.delay

internal enum class PlayerActionFeedbackType {
    Play,
    Pause,
    Rewind,
    Forward
}

internal data class PlayerActionFeedback(
    val type: PlayerActionFeedbackType,
    val id: Long = System.nanoTime()
)

@Composable
internal fun PlayerActionFeedbackOverlay(
    feedback: PlayerActionFeedback?,
    onFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    var activeFeedback by remember { mutableStateOf<PlayerActionFeedback?>(null) }

    LaunchedEffect(feedback?.id) {
        val nextFeedback = feedback ?: return@LaunchedEffect
        activeFeedback = nextFeedback
        visible = true
        delay(520)
        visible = false
        delay(220)
        onFinished(nextFeedback.id)
    }

    val currentFeedback = activeFeedback ?: return

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(120)) +
                scaleIn(
                    initialScale = 0.86f,
                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                ),
            exit = fadeOut(animationSpec = tween(220)) +
                scaleOut(
                    targetScale = 1.08f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ),
            modifier = Modifier.offset { currentFeedback.type.feedbackOffset() }
        ) {
            Icon(
                imageVector = currentFeedback.type.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(62.dp)
            )
        }
    }
}

private val PlayerActionFeedbackType.icon: ImageVector
    get() = when (this) {
        PlayerActionFeedbackType.Play -> PlayerIcons.Play
        PlayerActionFeedbackType.Pause -> PlayerIcons.Pause
        PlayerActionFeedbackType.Rewind -> PlayerIcons.Rewind
        PlayerActionFeedbackType.Forward -> PlayerIcons.Forward
    }

private fun PlayerActionFeedbackType.feedbackOffset(): IntOffset {
    return when (this) {
        PlayerActionFeedbackType.Rewind -> IntOffset(x = -320, y = 0)
        PlayerActionFeedbackType.Forward -> IntOffset(x = 320, y = 0)
        PlayerActionFeedbackType.Play,
        PlayerActionFeedbackType.Pause -> IntOffset.Zero
    }
}
