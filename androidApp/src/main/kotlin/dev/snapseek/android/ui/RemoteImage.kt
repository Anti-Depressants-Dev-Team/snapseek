package dev.snapseek.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import dev.snapseek.android.platform.ImageLoader

/**
 * One picture from the internet. It shows the placeholder colour until the bytes arrive, and asks the loader for
 * something no wider than the screen, which keeps a grid of phone-sized thumbnails off the heap.
 */
@Composable
fun RemoteImage(
    url: String,
    loader: ImageLoader,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Color = Snap.Card,
    maxWidth: Int = 0,
) {
    val containerWidth = LocalWindowInfo.current.containerSize.width
    var bitmap by remember(url) { mutableStateOf(loader.cached(url)) }

    LaunchedEffect(url) {
        if (bitmap == null) bitmap = loader.load(url, maxWidth.takeIf { it > 0 } ?: containerWidth.coerceAtLeast(720))
    }

    Box(modifier.background(placeholder)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}
