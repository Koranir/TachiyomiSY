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

    val frame: BookFrame = BookFrame(activity)

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

    override fun getView(): View {
        return frame
    }

    override fun isRTL(): Boolean {
        return true
    }

    override fun setChapters(chapters: ViewerChapters) {
        var newList = mutableListOf<ReaderPage>()
        chapters.currChapter.pages?.let { newList.addAll(it) }
        items = newList
        currentPage = items.first()
    }

    override fun moveToPage(page: ReaderPage) {
        currentPage = page
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
                    if (ctrlPressed) moveToNext() else moveRight()
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isUp) {
                    if (ctrlPressed) moveToPrevious() else moveLeft()
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
            moveLeft()
        } else {
            moveRight()
        }
    }

    fun moveToPrevious() {
        if (this.isRTL()) {
            moveRight()
        } else {
            moveLeft()
        }
    }

    fun moveLeft() {
    }

    fun moveRight() {
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
