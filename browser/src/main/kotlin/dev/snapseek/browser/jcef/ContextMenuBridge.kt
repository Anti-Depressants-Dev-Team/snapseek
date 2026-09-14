package dev.snapseek.browser.jcef

import dev.snapseek.browser.TabEvent
import dev.snapseek.core.model.OutputFormat
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.handler.CefContextMenuHandlerAdapter
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Replaces Chromium's context menu with ours, but only when the right-click landed on an image.
 * Text, links and everything else keep the default menu.
 */
internal class ContextMenuBridge(
    private val hints: HintStore,
    private val emit: (TabEvent) -> Unit,
) : CefContextMenuHandlerAdapter() {

    private val saveIds: Map<Int, OutputFormat> = OutputFormat.entries.withIndex()
        .associate { (i, fmt) -> (CefMenuModel.MenuId.MENU_ID_USER_FIRST + i) to fmt }
    private val copyUrlId = CefMenuModel.MenuId.MENU_ID_USER_FIRST + 50
    private val openImageId = CefMenuModel.MenuId.MENU_ID_USER_FIRST + 51

    override fun onBeforeContextMenu(browser: CefBrowser, frame: CefFrame, params: CefContextMenuParams, model: CefMenuModel) {
        if (!params.hasImageContents()) return
        model.clear()
        saveIds.forEach { (id, fmt) -> model.addItem(id, fmt.menuLabel) }
        model.addSeparator()
        model.addItem(copyUrlId, "Copy image address")
        model.addItem(openImageId, "Open image here")
    }

    override fun onContextMenuCommand(
        browser: CefBrowser,
        frame: CefFrame,
        params: CefContextMenuParams,
        commandId: Int,
        eventFlags: Int,
    ): Boolean {
        val shown = params.getSourceUrl() ?: return false
        val best = hints.bestFor(shown) ?: shown
        saveIds[commandId]?.let { fmt ->
            emit(TabEvent.SaveImage(best, params.getPageUrl().orEmpty(), fmt))
            return true
        }
        return when (commandId) {
            copyUrlId -> {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(best), null)
                true
            }
            openImageId -> {
                browser.loadURL(best)
                true
            }
            else -> false
        }
    }
}
