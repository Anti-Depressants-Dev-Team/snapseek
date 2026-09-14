package dev.snapseek.app.ui.common

import dev.snapseek.core.settings.AppPaths
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Desktop
import java.nio.file.Path
import kotlin.io.path.createDirectories

/** Opening files and folders in the OS shell, with the Windows-specific "select this file" behaviour. */
object Reveal {
    private val log = KotlinLogging.logger {}

    fun openFolder(dir: Path) = runCatching {
        dir.createDirectories()
        Desktop.getDesktop().open(dir.toFile())
    }.onFailure { log.warn(it) { "Couldn't open $dir" } }

    fun open(file: Path) = runCatching {
        Desktop.getDesktop().open(file.toFile())
    }.onFailure { log.warn(it) { "Couldn't open $file" } }

    fun inFolder(file: Path) = runCatching {
        val abs = file.toAbsolutePath().toString()
        when {
            AppPaths.isWindows -> ProcessBuilder("explorer.exe", "/select,", abs).start()
            AppPaths.isMac -> ProcessBuilder("open", "-R", abs).start()
            else -> Desktop.getDesktop().open(file.toAbsolutePath().parent.toFile())
        }
    }.onFailure { log.warn(it) { "Couldn't reveal $file" } }
}
