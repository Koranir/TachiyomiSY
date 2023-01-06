package eu.kanade.tachiyomi.ui.reader.viewer.glpager

import eu.kanade.tachiyomi.ui.reader.ReaderActivity

/**
 * Implementation of a left to right PagerViewer.
 */
class L2RGLPagerViewer(activity: ReaderActivity) : GLPagerViewer(activity)

/**
 * Implementation of a right to left PagerViewer.
 */
class R2LGLPagerViewer(activity: ReaderActivity) : GLPagerViewer(activity) {

    override fun update() {
        viewer.RTL = true
    }

    /**
     * Moves to the next page. On a R2L pager the next page is the one at the left.
     */
    override fun moveToNext() {
        moveLeft()
    }

    /**
     * Moves to the previous page. On a R2L pager the previous page is the one at the right.
     */
    override fun moveToPrevious() {
        moveRight()
    }
}
