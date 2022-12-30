package eu.kanade.tachiyomi.ui.reader.viewer.book

import eu.kanade.tachiyomi.ui.reader.ReaderActivity

/**
 * Implementation of a left to right PagerViewer.
 */
class L2RBookViewer(activity: ReaderActivity) : BookViewer(activity) {
    /**
     * Creates a new left to right pager.
     */
    override fun createPager(): Book {
        return Book(activity, false)
    }
}

/**
 * Implementation of a right to left PagerViewer.
 */
class R2LBookViewer(activity: ReaderActivity) : BookViewer(activity) {
    /**
     * Creates a new right to left pager.
     */
    override fun createPager(): Book {
        return Book(activity, true)
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
