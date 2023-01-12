package eu.kanade.tachiyomi.ui.reader.viewer.glpager

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import eu.kanade.tachiyomi.ui.reader.viewer.GestureDetectorWithLongTap
import eu.kanade.tachiyomi.util.system.logcat

@SuppressLint("ClickableViewAccessibility")
class GLPagerSurfaceView(val scontext: Context) : GLTextureView(scontext) {
    /**
     * Tap listener function to execute when a tap is detected.
     */
    var tapListener: ((MotionEvent) -> Unit)? = null

    var mRenderer = GLPagerRenderer(scontext)

    var RTL = false

    init {
        setRenderer(mRenderer)
        setOnTouchListener(
            OnTouchListener { v, event ->
                val action = event.action
                when (action and MotionEvent.ACTION_MASK) {
                    MotionEvent.ACTION_DOWN -> {
                        mRenderer.iMouseX = event.x
                        mRenderer.iMouseY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        mRenderer.iMouseX = event.x
                        mRenderer.iMouseY = event.y
                    }
                    MotionEvent.ACTION_UP -> {
                        mRenderer.resetMouse()
                        tapListener?.invoke(event)
                    }
                }
                true
            },
        )
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        logcat(message = { "Size changed for child" })
        mRenderer.surfaceHeight = h
        mRenderer.surfaceWidth = w
        super.onSizeChanged(w, h, oldw, oldh)
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
