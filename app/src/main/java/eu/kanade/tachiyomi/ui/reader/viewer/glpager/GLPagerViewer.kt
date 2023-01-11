package eu.kanade.tachiyomi.ui.reader.viewer.glpager

import android.graphics.PointF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.MainScope
import uy.kohesive.injekt.injectLazy
import kotlin.math.min

@Suppress("LeakingThis")
abstract class GLPagerViewer(val activity: ReaderActivity) : BaseViewer {

    val downloadManager: DownloadManager by injectLazy()

    val scope = MainScope()

    var viewer = GLPagerSurfaceView(activity)

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = GLPagerConfig(this, scope)

    val adapter = GLPagerAdapter(this)

    var currentPage: Int = 0
        private set

    private var currentReaderPage: ReaderPage? = null

    private var maxPage: Int = 0

    init {
        update()

        viewer.tapListener = { event ->
            val pos = PointF(event.rawX / viewer.width, event.rawY / viewer.height)
            val navigator = config.navigator

            when (navigator.getAction(pos)) {
                ViewerNavigation.NavigationRegion.MENU -> activity.toggleMenu()
                ViewerNavigation.NavigationRegion.NEXT -> moveToNext()
                ViewerNavigation.NavigationRegion.PREV -> moveToPrevious()
                ViewerNavigation.NavigationRegion.RIGHT -> moveRight()
                ViewerNavigation.NavigationRegion.LEFT -> moveLeft()
            }
        }
        /*viewer.longTapListener = f@{
            if (activity.menuVisible || config.longTapEnabled) {
                val item = //adapter.joinedItems.getOrNull(pager.currentItem)
                val firstPage = item?.first as? ReaderPage
                val secondPage = item?.second as? ReaderPage
                if (firstPage is ReaderPage) {
                    activity.onPageLongTap(firstPage, secondPage)
                    return@f true
                }
            }
            false
        }*/

        /*config.dualPageSplitChangedListener = { enabled ->
            if (!enabled) {
                cleanupPageSplit()
            }
        }*/

        config.reloadChapterListener = {
            activity.reloadChapters(it)
        }

        /*config.imagePropertyChangedListener = {
            refreshAdapter()
        }*/

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }
    }

    open fun update() {}

    fun moveLeft() {
        if (currentPage > 0) {
            setPage(currentPage - 1)
        }
    }

    fun moveRight() {
        if (currentPage <= maxPage) {
            setPage(currentPage + 1)
        }
    }

    override fun getView(): View {
        return viewer
    }

    override fun setChapters(chapters: ViewerChapters) {
        maxPage = chapters.currChapter.pages?.size?.minus(1)!!
    }

    override fun moveToPage(page: ReaderPage) {
        currentReaderPage = page
        val position = adapter.joinedItems.indexOfFirst { it.first == page || it.second == page }
        logcat { "moveToPage ${page.number}, index: $position" }
        setPage(position)
        if (position != -1) {
            val currentPosition = currentPage
            currentPage = position
            // manually call onPageChange since ViewPager listener is not triggered in this case
            if (currentPosition == position) {
            } else {
                // Call this since with double shift onPageChange wont get called (it shouldn't)
                // Instead just update the page count in ui
                val joinedItem = adapter.joinedItems.firstOrNull { it.first == page || it.second == page }
                activity.onPageSelected(
                    joinedItem?.first as? ReaderPage ?: page,
                    joinedItem?.second != null,
                )
            }
        } else {
            logcat { "Page $page not found in adapter" }
        }
    }

    fun setPage(index: Int) {
        logcat { "SetPage $currentPage" }
        adapter.getPageHolder(currentPage)?.getImage()?.let { viewer.mRenderer.loadTexture(0, it) }
        currentPage = index
        currentReaderPage?.let { activity.onPageSelected(it) }
        // currentReaderPage?.stream?.invoke()?.let { viewer.mRenderer.loadTexture(0, decodeStream(it)) }
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

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0) {
            when (event.action) {
                MotionEvent.ACTION_SCROLL -> {
                    if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) {
                        moveDown()
                    } else {
                        moveUp()
                    }
                    return true
                }
            }
        }
        return false
    }

    /**
     * Moves to the next page.
     */
    open fun moveToNext() {
        moveRight()
    }

    /**
     * Moves to the previous page.
     */
    open fun moveToPrevious() {
        moveLeft()
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

    /**
     * Sets the active [chapters] on this pager.
     */
    private fun setChaptersInternal(chapters: ViewerChapters) {
        logcat { "setChaptersInternal" }
        val forceTransition = config.alwaysShowChapterTransition || adapter.joinedItems.getOrNull(currentPage)?.first is ChapterTransition
        adapter.setChapters(chapters, forceTransition)

        // Layout the pager once a chapter is being set
        if (viewer.isGone) {
            logcat { "Pager first layout" }
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)])
            viewer.isVisible = true
        }
    }

    // SY -->
    fun setChaptersDoubleShift(chapters: ViewerChapters) {
        // Remove Listener since we're about to change the size of the items
        // If we don't the size change could put us on a new chapter
        // pager.removeOnPageChangeListener(pagerListener)
        setChaptersInternal(chapters)
        // pager.addOnPageChangeListener(pagerListener)
        // Since we removed the listener while shifting, call page change to update the ui
        currentReaderPage?.let { moveToPage(it) }
    }

    fun updateShifting(page: ReaderPage? = null) {
        adapter.pageToShift = page ?: adapter.joinedItems.getOrNull(currentPage)?.first as? ReaderPage
    }

    fun splitDoublePages(currentPage: ReaderPage) {
        adapter.splitDoublePages(currentPage)
    }

    fun getShiftedPage(): ReaderPage? = adapter.pageToShift
    // SY <--
}
