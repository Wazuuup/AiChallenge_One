package ru.sber.cb.aichallenge_one.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun VoiceInputButton(
    isRecording: Boolean,
    recordingDuration: Long,  // milliseconds
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timer during recording
        if (isRecording) {
            RecordingTimer(
                duration = recordingDuration,
                modifier = Modifier
            )
        }

        // Recording/Send button
        val infiniteTransition = rememberInfiniteTransition(label = "recording")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )

        IconButton(
            onClick = onToggleRecording,
            modifier = Modifier
                .size(56.dp)
                .then(
                    if (isRecording) Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    else Modifier
                )
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (isRecording) "Stop recording" else "Start recording",
                tint = if (isRecording)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun RecordingTimer(
    duration: Long,  // milliseconds
    modifier: Modifier = Modifier
) {
    val seconds = (duration / 1000).toInt()
    val minutes = seconds / 60
    val secs = seconds % 60
    val displayTime = "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"

    Text(
        text = displayTime,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier
    )
}
