package dev.snapseek.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.snapseek.app.ui.theme.SnapSeekColors

@Composable
fun RemoteImage(
    url: String,
    loader: ImageLoader,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    large: Boolean = false,
    contentDescription: String? = null,
    placeholder: Color = SnapSeekColors.CardRaised,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = loader.cached(url, large), key1 = url, key2 = large) {
        if (value == null) value = loader.load(url, large)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(bitmap = b, contentDescription = contentDescription, modifier = Modifier.fillMaxSize(), contentScale = contentScale)
        } else {
            Box(Modifier.fillMaxSize().background(placeholder))
        }
    }
}
