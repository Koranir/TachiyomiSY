package eu.kanade.tachiyomi.ui.reader.viewer.book

import android.graphics.PointF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import kotlinx.coroutines.MainScope
import tachiyomi.core.util.system.logcat
import uy.kohesive.injekt.injectLazy

class BookViewer(val activity: ReaderActivity) : BaseViewer {

    val downloadManager: DownloadManager by injectLazy()

    val scope = MainScope()

    val frame: BookFrame = BookFrame(activity, this)

    val config = BookConfig(this, scope)

    var currentPage: ReaderPage? = null

    var items: List<ReaderPage> = emptyList()

    init {
        frame.tapListener = { event ->
            val pos = PointF(event.rawX / frame.width, event.rawY / frame.height)
            logcat { "Ayo why" }
            when (config.navigator.getAction(pos)) {
                ViewerNavigation.NavigationRegion.MENU -> activity.toggleMenu()
                ViewerNavigation.NavigationRegion.NEXT -> moveToNext()
                ViewerNavigation.NavigationRegion.PREV -> moveToPrevious()
                ViewerNavigation.NavigationRegion.RIGHT -> moveRight()
                ViewerNavigation.NavigationRegion.LEFT -> moveLeft()
            }
        }
        frame.longTapListener = f@{
            if (activity.menuVisible || config.longTapEnabled) {
                if (currentPage != null) {
                    activity.onPageLongTap(currentPage!!)
                    return@f true
                }
            }
            false
        }
    }

    fun getPagesToDraw(): Triple<BookRendererPage?, BookRendererPage, BookRendererPage?>? {
        val offset = frame.renderer.offset
        if (currentPage != null) {
            val indexOfFirstPage = frame.pages.indexOf(frame.pages.find { it.isTheSamePageAs(currentPage!!) })
            if (indexOfFirstPage < frame.pages.size - 2 + offset) {
                if (indexOfFirstPage + offset > 0) {
                    return Triple(
                        frame.pages[indexOfFirstPage - 1 + offset],
                        frame.pages[indexOfFirstPage + offset],
                        frame.pages[indexOfFirstPage + 1 + offset],
                    )
                } else if (offset >= 0) {
                    return Triple(
                        null,
                        frame.pages[indexOfFirstPage + offset],
                        frame.pages[indexOfFirstPage + 1 + offset],
                    )
                } else {
                    return Triple(
                        null,
                        frame.pages[0],
                        frame.pages[indexOfFirstPage + 1 + offset],
                    )
                }
            } else {
                if (indexOfFirstPage + offset + 1 < frame.pages.size) {
                    logcat { "FROM LINE 80" }
                    return Triple(
                        frame.pages[indexOfFirstPage - 1 + offset],
                        frame.pages[indexOfFirstPage + offset],
                        frame.pages[indexOfFirstPage + offset + 1],
                    )
                } else {
                    logcat { "FROM LINE 87" }
                    return Triple(
                        frame.pages[indexOfFirstPage + offset - 1],
                        frame.pages[indexOfFirstPage + offset],
                        null, // frame.pages[indexOfFirstPage + offset + 1],
                    )
                }
            }
        } else {
            return null
        }
    }

    override fun getView(): View {
        return frame
    }

    override fun isRTL(): Boolean {
        return true
    }

    override fun setChapters(chapters: ViewerChapters) {
        logcat { "Setting up chapters." }
        frame.setPages(chapters)
        moveToPage(frame.pages.first().page)
    }

    override fun moveToPage(page: ReaderPage) {
        logcat { "Moving to page ${page.number} in ${page.chapter.chapter.name}." }
        currentPage = page
        activity.onPageSelected(currentPage!!)
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        val ctrlPressed = event.metaState.and(KeyEvent.META_CTRL_ON) > 0

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveDown() else moveUp()
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveUp() else moveDown()
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isUp) {
                    if (ctrlPressed) moveToNext() else simDrag(false)
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isUp) {
                    if (ctrlPressed) moveToPrevious() else simDrag(true)
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_DPAD_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_PAGE_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()
            else -> return false
        }
        return true
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        return false
    }

    /**
     * Moves to the page at the top (or previous).
     */
    protected open fun moveUp() {
        moveToPrevious()
    }

    /**
     * Moves to the page at the bottom (or next).
     */
    protected open fun moveDown() {
        moveToNext()
    }

    fun moveToNext() {
        if (this.isRTL()) {
            simDrag(false)
        } else {
            simDrag(true)
        }
    }

    fun moveToPrevious() {
        if (this.isRTL()) {
            simDrag(true)
        } else {
            simDrag(false)
        }
    }

    fun moveLeft() {
        if (currentPage?.index!! > 0) {
            currentPage?.chapter?.pages?.get((currentPage?.index?.minus(1)!!))
                ?.let { moveToPage(it) }
        }
    }

    fun moveRight() {
        if (currentPage?.index!! < currentPage?.chapter?.pages?.size?.minus(1)!!) {
            currentPage?.chapter?.pages?.get((currentPage?.index?.plus(1)!!))
                ?.let { moveToPage(it) }
        }
    }

    fun simDrag(fromLeft: Boolean) {
        frame.renderer.drag(0f, 0f, fromLeft)
        frame.renderer.finishDrag(fromLeft)
        if (fromLeft) {
            moveRight()
        } else {
            moveLeft()
        }
    }

    /*override fun getView(): View {
        TODO("Not yet implemented")
    }

    override fun isRTL(): Boolean {
        TODO("Not yet implemented")
    }

    override fun setChapters(chapters: ViewerChapters) {
        TODO("Not yet implemented")
    }

    override fun moveToPage(page: ReaderPage) {
        TODO("Not yet implemented")
    }

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        TODO("Not yet implemented")
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        TODO("Not yet implemented")
    }*/
}
