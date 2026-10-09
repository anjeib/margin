package ru.anjeib.margin.paper

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Files

/*
 * A photo kept on a line, over everything: pinch to look closer. Anything that is not a picture opens in the app the
 * phone has for it — Margin does not pretend to be a PDF reader.
 */

/** Opens a file: a photo here, the rest with the phone's own app. */
fun openFile(ctx: android.content.Context, a: Attachment, onPhoto: (Attachment) -> Unit) {
    if (a.kind == "photo") { onPhoto(a); return }
    Files.openIntent(ctx, a)?.let { runCatching { ctx.startActivity(it) } }
}

@Composable
fun PhotoViewer(a: Attachment, onClose: () -> Unit, onRemove: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val w = P.w
    val bmp by produceState<Bitmap?>(null, a.id) { value = Files.thumb(ctx, a, 2000) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            bmp?.let {
                Image(it.asImageBitmap(), a.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale == 1f) Offset.Zero else offset + pan
                            }
                        }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y })
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(w.close, style = Voice.reply.copy(color = Color.White),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(12.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(w.share, style = Voice.reply.copy(color = Color.White),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        Files.shareIntent(ctx, a)?.let { i -> runCatching { ctx.startActivity(android.content.Intent.createChooser(i, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    }.padding(12.dp))
            }
            if (onRemove != null) Text(w.removeFile, style = Voice.reply.copy(color = Color.White.copy(alpha = 0.85f)),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { onRemove(); onClose() }.padding(12.dp))
        }
    }
}
