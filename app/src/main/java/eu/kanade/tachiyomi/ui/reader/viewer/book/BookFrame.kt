package eu.kanade.tachiyomi.ui.reader.viewer.book

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.GestureDetectorWithLongTap
import tachiyomi.core.util.system.logcat

class BookFrame(context: Context, viewer: BookViewer) : GLSurfaceView(context) {

    var pages: MutableList<BookRendererPage> = mutableListOf()

    private val listener = GestureListener()
    private val detector = Detector()

    /**
     * Tap listener function to execute when a tap is detected.
     */
    var tapListener: ((MotionEvent) -> Unit)? = null

    /**
     * Long tap listener function to execute when a long tap is detected.
     */
    var longTapListener: ((MotionEvent) -> Boolean)? = null

    private var renderer = BookRenderer(viewer)

    /*override fun onTouchEvent(event: MotionEvent): Boolean {
        logcat(message = { "WTF DOG? It's a touch event func!!" })
        // if (event != null) {
        detector.onTouchEvent(event)
        // } else {
        //    logcat(message = { "WTF DOG? The event was NULL!!" })
        // }
        return super.onTouchEvent(event)
    }*/

    /*override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        logcat(message = { "WTF DOG? It's a dispatch event func!!" })
        if (event != null) {
            detector.onTouchEvent(event)
        } else {
            logcat(message = { "WTF DOG? The event was NULL!!" })
        }
        return super.dispatchTouchEvent(event)
    }*/

    /*override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }*/

    init {
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setRenderer(renderer)
    }

    fun setPages(chapters: ViewerChapters) {
        if (chapters.currChapter.pages != null) {
            for (page in chapters.currChapter.pages!!) {
                pages.add(BookRendererPage(page))
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        logcat { "Dispatched touch event {}$event" }
        var handled = super.dispatchTouchEvent(event)
        handled = handled or detector.onTouchEvent(event)
        return handled
    }

    /**
     * Gesture listener that implements tap and long tap events.
     */
    inner class GestureListener : GestureDetectorWithLongTap.Listener() {
        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            logcat(message = { "WTF DOG? A single touch was confiremed!!" })
            tapListener?.invoke(ev)
            return true
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            logcat(message = { "WTF DOG? A loong tap was confiremd!!" })
            val listener = longTapListener
            if (listener != null && listener.invoke(ev)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    inner class Detector : GestureDetectorWithLongTap(context, listener) {
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            super.onTouchEvent(ev)
            return true
        }
    }

    /*override fun surfaceCreated(holder: SurfaceHolder) {
        setRenderer()
        super.surfaceCreated(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        super.surfaceChanged(holder, format, w, h)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        super.surfaceDestroyed(holder)
    }*/
}
