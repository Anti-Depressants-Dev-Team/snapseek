package dev.snapseek.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.snapseek.core.model.Service

/** Brand-tinted tile: the web UI's glyphs for the original sites, a lettered tile for boorus added later. */
@Composable
fun ServiceIcon(service: Service, size: Dp) {
    val key = service.icon.takeIf { it != "default" && !it.startsWith("http") } ?: service.id
    val brand = brandColor(key)
    Box(
        Modifier.size(size).background(brand.copy(alpha = 0.14f), RoundedCornerShape(size / 4)),
        contentAlignment = Alignment.Center,
    ) {
        val vector = ServiceGlyphs.forId(key, brand)
        if (vector != null) {
            Icon(vector, contentDescription = service.name, tint = Color.Unspecified, modifier = Modifier.size(size * 0.6f))
        } else {
            Text(
                service.name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = brand,
            )
        }
    }
}

private fun brandColor(key: String): Color = when (key) {
    "pinterest" -> Color(0xFFE60023)
    "pixiv" -> Color(0xFF0096FA)
    "deviantart" -> Color(0xFF05CC47)
    "safebooru" -> Color(0xFF7C3AED)
    "giphy" -> Color(0xFF00CCFF)
    "tenor" -> Color(0xFF2D93DD)
    "wallpapers" -> Color(0xFFA855F7)
    "danbooru", "danbooru_safe" -> Color(0xFF0075F8)
    "aibooru" -> Color(0xFF06B6D4)
    "yandere" -> Color(0xFFF472B6)
    "konachan" -> Color(0xFF818CF8)
    "gelbooru" -> Color(0xFF006FFA)
    "rule34" -> Color(0xFF86EFAC)
    "tbib" -> Color(0xFF60A5FA)
    "xbooru" -> Color(0xFF8B5CF6)
    "hypnohub" -> Color(0xFFE879F9)
    "realbooru" -> Color(0xFFF97316)
    "e621" -> Color(0xFF3B82F6)
    "derpibooru" -> Color(0xFF7C3AED)
    "ponybooru" -> Color(0xFFEC4899)
    "furbooru" -> Color(0xFF10B981)
    "zerochan" -> Color(0xFF60A5FA)
    "wallhaven" -> Color(0xFF34D399)
    "unsplash" -> Color(0xFFF1F5F9)
    "pexels" -> Color(0xFF05A081)
    "pixabay" -> Color(0xFF2EC66D)
    else -> Color(0xFFA698BA)
}

private object ServiceGlyphs {
    fun forId(id: String, color: Color): ImageVector? = when (id) {
        "pinterest" -> vector("pinterest", 48f) {
            fill(color, "M24 4C12.96 4 4 12.96 4 24C4 32.52 9.48 39.72 17.04 42.36C16.92 40.92 16.8 38.64 17.16 37.08C17.52 35.64 19.32 27.84 19.32 27.84C19.32 27.84 18.72 26.64 18.72 24.84C18.72 21.96 20.4 19.8 22.56 19.8C24.36 19.8 25.2 21.12 25.2 22.68C25.2 24.48 24 27.24 23.4 29.76C22.92 31.92 24.48 33.72 26.64 33.72C30.48 33.72 33.48 29.52 33.48 23.64C33.48 18.48 29.76 14.88 24 14.88C17.4 14.88 13.56 19.68 13.56 24.48C13.56 26.28 14.28 28.2 15.12 29.28C15.24 29.52 15.24 29.64 15.24 29.88C15 31.08 14.52 32.64 14.4 33.12C14.28 33.72 14.04 33.84 13.44 33.6C10.68 32.28 8.88 28.68 8.88 24.36C8.88 17.28 14.04 10.8 24.6 10.8C33.12 10.8 39.72 16.8 39.72 23.52C39.72 30.6 34.92 36.36 28.44 36.36C26.28 36.36 24.24 35.16 23.52 33.84C23.52 33.84 22.44 38.16 22.2 39C21.72 40.92 20.4 43.32 19.56 44.88C21.24 45.36 22.56 45.6 24 45.6C35.04 45.6 44 36.6 44 24.6C44 12.96 35.04 4 24 4Z")
        }
        "pixiv" -> vector("pixiv", 48f) {
            fill(color, "M24 4C12.96 4 4 12.96 4 24C4 35.04 12.96 44 24 44C35.04 44 44 35.04 44 24C44 12.96 35.04 4 24 4ZM28.8 26.4H20.4V32.4H16.8V15.6H28.8C31.68 15.6 34.08 18 34.08 20.88C34.08 23.76 31.68 26.4 28.8 26.4ZM20.4 19.2V22.8H28.8C29.76 22.8 30.48 22.08 30.48 21.12C30.48 20.16 29.76 19.2 28.8 19.2H20.4Z")
        }
        "deviantart" -> vector("deviantart", 24f) {
            fill(color, "M19.207 4.794l.23-.43V0H15.07l-.436.44-2.058 3.925-.646.436H4.58v5.993h4.04l.36.436-4.175 7.98-.24.43V24H8.93l.436-.44 2.07-3.925.644-.436h7.35v-5.993h-4.05l-.36-.438 4.186-7.977z")
        }
        "safebooru" -> vector("safebooru", 48f) {
            stroke(color, 3f, "M12 8h24a4 4 0 0 1 4 4v24a4 4 0 0 1-4 4H12a4 4 0 0 1-4-4V12a4 4 0 0 1 4-4z")
            fill(color, "M15 18a3 3 0 1 0 6 0a3 3 0 1 0-6 0")
            fill(color, "M40 28L32 20L20 32L12 24L8 28V36C8 37.1046 8.89543 38 10 38H38C39.1046 38 40 37.1046 40 36V28Z")
        }
        "wallpapers" -> vector("wallpapers", 48f) {
            stroke(color, 3f, "M8 8h32a4 4 0 0 1 4 4v24a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V12a4 4 0 0 1 4-4z")
            fill(color, "M11 16a3 3 0 1 0 6 0a3 3 0 1 0-6 0")
            stroke(color, 3f, "M44 34L30 18L10 38")
            stroke(color, 3f, "M30 32L22 24L14 32")
        }
        "giphy" -> vector("giphy", 48f) {
            fill(color, "M10 10h28v6h-22v16h22v-10h-8v-6h14v22h-34z")
        }
        "tenor" -> vector("tenor", 48f) {
            fill(color, "M14 14h20v6h-7v14h-6v-14h-7z")
        }
        else -> null
    }

    private fun vector(name: String, viewport: Float, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 48.dp, defaultHeight = 48.dp, viewportWidth = viewport, viewportHeight = viewport)
            .apply(build)
            .build()

    private fun ImageVector.Builder.fill(color: Color, d: String) {
        addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(color))
    }

    private fun ImageVector.Builder.stroke(color: Color, width: Float, d: String) {
        addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(color),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}
