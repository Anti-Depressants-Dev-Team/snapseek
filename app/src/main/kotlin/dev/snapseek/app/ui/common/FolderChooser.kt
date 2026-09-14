package dev.snapseek.app.ui.common

import java.awt.Window
import java.nio.file.Path
import javax.swing.JFileChooser

object FolderChooser {
    /** Blocks on the Swing modal dialog, which is fine on the UI thread because Swing pumps events while it is open. */
    fun pick(parent: Window?, start: Path): Path? {
        val chooser = JFileChooser(start.toFile()).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose download folder"
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.toPath() else null
    }
}
