package dev.snapseek.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
import dev.snapseek.android.platform.ImageLoader

/**
 * One picture from the internet, decoded no larger than the space it is drawn in. That last part matters on a
 * phone: a grid cell is half the screen wide, and decoding every thumbnail at full screen width costs four times
 * the memory for pixels nobody can see.
 */
@Composable
fun RemoteImage(
    url: String,
    loader: ImageLoader,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Color = Snap.Card,
) {
    BoxWithConstraints(modifier.background(placeholder)) {
        val width = with(LocalDensity.current) {
            if (constraints.hasBoundedWidth) constraints.maxWidth else maxWidth.roundToPx()
        }
        var bitmap by remember(url) { mutableStateOf(loader.cached(url)) }

        LaunchedEffect(url, width) {
            if (bitmap == null) bitmap = loader.load(url, width)
        }

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
