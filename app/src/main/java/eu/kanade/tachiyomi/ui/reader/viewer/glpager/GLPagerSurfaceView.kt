package eu.kanade.tachiyomi.ui.reader.viewer.glpager

import android.content.Context
import android.view.MotionEvent
import eu.kanade.tachiyomi.ui.reader.viewer.GestureDetectorWithLongTap

class GLPagerSurfaceView(context: Context) : GLTextureView(context) {
    /**
     * Tap listener function to execute when a tap is detected.
     */
    var tapListener: ((MotionEvent) -> Unit)? = null

    var mRenderer = GLPagerRenderer()

    var RTL = false

    init {
        setRenderer(mRenderer)
    }

    /**
     * Handles a touch event. Only used to prevent crashes when child views manipulate
     * [requestDisallowInterceptTouchEvent].
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP) {
            tapListener?.invoke(ev)
        }
        return true
    }

    /**
     * Gesture listener that implements tap and long tap events.
     */
    private val gestureListener = object : GestureDetectorWithLongTap.Listener() {
        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            tapListener?.invoke(ev)
            return true
        }

        /*override fun onLongTapConfirmed(ev: MotionEvent) {
            val listener = longTapListener
            if (listener != null && listener.invoke(ev)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }*/
    }
}
