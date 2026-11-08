package ru.anjeib.margin.ui

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Files
import ru.anjeib.margin.data.durationLabel

/**
 * A quiet, compact audio pill (Margin style):
 * [▶]  ──────────  0:35
 * Plays audio in place without leaving the screen.
 */
@Composable
fun VoicePlayerPill(
    a: Attachment,
    modifier: Modifier = Modifier
) {
    val c = M.c
    val s = M.s
    val ctx = LocalContext.current
    var player by remember(a.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(a.id) { mutableStateOf(false) }
    var progress by remember(a.id) { mutableFloatStateOf(0f) }
    var currentMs by remember(a.id) { mutableIntStateOf(0) }

    DisposableEffect(a.id) {
        onDispose {
            player?.runCatching {
                if (isPlaying) stop()
                release()
            }
            player = null
        }
    }

    LaunchedEffect(playing) {
        while (playing) {
            player?.let { p ->
                if (p.duration > 0) {
                    currentMs = p.currentPosition
                    progress = (p.currentPosition.toFloat() / p.duration.toFloat()).coerceIn(0f, 1f)
                }
            }
            delay(120)
        }
    }

    fun toggle() {
        val p = player ?: run {
            val f = Files.file(ctx, a) ?: return
            runCatching {
                MediaPlayer().apply {
                    setDataSource(f.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        playing = false
                        progress = 0f
                        currentMs = 0
                    }
                }
            }.getOrNull()?.also { player = it } ?: return
        }
        if (p.isPlaying) {
            p.pause()
            playing = false
        } else {
            p.start()
            playing = true
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.sea.copy(alpha = if (c.dark) 0.16f else 0.08f))
            .border(1.dp, c.link.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(c.link)
                .clickable { toggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (playing) s.voiceNote else s.voiceNote,
                tint = c.bg,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = c.link,
            trackColor = c.hairline,
            modifier = Modifier
                .weight(1f)
                .height(3.dp),
            drawStopIndicator = {},
            gapSize = 0.dp
        )
        Spacer(Modifier.width(10.dp))
        val curSec = currentMs / 1000
        val durText = a.durationLabel().ifBlank { "0:00" }
        val timeLabel = if (playing && currentMs > 0) {
            "%d:%02d / %s".format(curSec / 60, curSec % 60, durText)
        } else {
            durText
        }
        Text(
            text = timeLabel,
            style = Type.small.copy(fontFamily = Commissioner, color = c.graphite, fontSize = 12.sp)
        )
    }
}
