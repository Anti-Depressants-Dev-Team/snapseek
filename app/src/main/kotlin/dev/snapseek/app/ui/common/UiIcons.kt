package dev.snapseek.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The app's icon set: 24x24 stroke glyphs (Feather-style, 2px), the same family the 1.x web UI used.
 * Drawn from path data so we don't depend on the Material icons artifact. Tint them via Icon(tint = …).
 */
object UiIcons {
    val ArrowLeft: ImageVector by lazy { stroked("arrow-left", "M19 12H5", "M12 19l-7-7 7-7") }
    val ArrowRight: ImageVector by lazy { stroked("arrow-right", "M5 12h14", "M12 5l7 7-7 7") }
    val ChevronLeft: ImageVector by lazy { stroked("chevron-left", "M15 18l-6-6 6-6") }
    val ChevronRight: ImageVector by lazy { stroked("chevron-right", "M9 18l6-6-6-6") }
    val ChevronDown: ImageVector by lazy { stroked("chevron-down", "M6 9l6 6 6-6") }
    val Refresh: ImageVector by lazy { stroked("refresh", "M23 4v6h-6", "M20.49 15a9 9 0 1 1-2.12-9.36L23 10") }
    val Home: ImageVector by lazy { stroked("home", "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M9 22V12h6v10") }
    val Close: ImageVector by lazy { stroked("close", "M18 6L6 18", "M6 6l12 12") }
    val Menu: ImageVector by lazy { stroked("menu", "M3 12h18", "M3 6h18", "M3 18h18") }
    val Search: ImageVector by lazy { stroked("search", "M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z", "M21 21l-4.35-4.35") }
    val Download: ImageVector by lazy { stroked("download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3") }
    val DownloadAll: ImageVector by lazy { stroked("download-all", "M12 2L2 7l10 5 10-5-10-5z", "M2 17l10 5 10-5", "M2 12l10 5 10-5") }
    val Copy: ImageVector by lazy { stroked("copy", "M20 9h-9a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2z", "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1") }
    val ExternalLink: ImageVector by lazy { stroked("external-link", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6", "M15 3h6v6", "M10 14L21 3") }
    val Globe: ImageVector by lazy { stroked("globe", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M2 12h20", "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z") }
    val Heart: ImageVector by lazy { stroked("heart", HEART) }
    val HeartFilled: ImageVector by lazy { filled("heart-filled", HEART) }
    val Bookmark: ImageVector by lazy { stroked("bookmark", "M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z") }
    val Shield: ImageVector by lazy { stroked("shield", "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z") }
    val ShieldCheck: ImageVector by lazy { stroked("shield-check", "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z", "M9 12l2 2 4-4") }
    val Check: ImageVector by lazy { stroked("check", "M20 6L9 17l-5-5") }
    val CheckCircle: ImageVector by lazy { stroked("check-circle", "M22 11.08V12a10 10 0 1 1-5.93-9.14", "M22 4L12 14.01l-3-3") }
    val Circle: ImageVector by lazy { stroked("circle", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z") }
    val Plus: ImageVector by lazy { stroked("plus", "M12 5v14", "M5 12h14") }
    val Trash: ImageVector by lazy { stroked("trash", "M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2") }
    val Key: ImageVector by lazy { stroked("key", "M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 1 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4") }
    val Tool: ImageVector by lazy {
        stroked(
            "tool",
            "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z",
        )
    }
    val Settings: ImageVector by lazy {
        stroked(
            "settings",
            "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
            "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
        )
    }
    val Folder: ImageVector by lazy {
        stroked("folder", "M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z")
    }
    val History: ImageVector by lazy {
        stroked("history", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0", "M12 6v6l4 2")
    }

    private const val HEART = "M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"

    private fun stroked(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { d ->
                    addPath(
                        pathData = PathParser().parsePathString(d).toNodes(),
                        stroke = SolidColor(Color.White),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

    private fun filled(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { d ->
                    addPath(
                        pathData = PathParser().parsePathString(d).toNodes(),
                        fill = SolidColor(Color.White),
                        stroke = SolidColor(Color.White),
                        strokeLineWidth = 2f,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()
}
