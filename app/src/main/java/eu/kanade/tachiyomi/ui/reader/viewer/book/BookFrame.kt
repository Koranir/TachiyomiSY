package eu.kanade.tachiyomi.ui.reader.viewer.book

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.GestureDetectorWithLongTap
import tachiyomi.core.util.system.logcat
import kotlin.math.abs

class BookFrame(context: Context, val viewer: BookViewer) : GLSurfaceView(context) {

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

    var renderer = BookRenderer(viewer)

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
        setEGLContextClientVersion(2)
        setRenderer(renderer)
    }

    fun setPages(chapters: ViewerChapters) {
        if (chapters.currChapter.pages != null) {
            for (page in chapters.currChapter.pages!!) {
                pages.add(BookRendererPage(page, renderer = renderer))
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // logcat { "Dispatched touch event {}$event" }
        var handled = super.dispatchTouchEvent(event)
        handled = handled or detector.onTouchEvent(event)
        return handled
    }

    /**
     * Gesture listener that implements tap and long tap events.
     */
    inner class GestureListener : GestureDetectorWithLongTap.Listener() {
        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            // logcat(message = { "WTF DOG? A single touch was confiremed!!" })
            tapListener?.invoke(ev)
            return true
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            // logcat(message = { "WTF DOG? A loong tap was confiremd!!" })
            val listener = longTapListener
            if (listener != null && listener.invoke(ev)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    inner class Detector : GestureDetectorWithLongTap(context, listener) {
        private var scrollPointerId = 0
        private var downX = 0
        private var downY = 0
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        private var dragX = 0
        private var dragY = 0
        private var isDragging = false

        private var draggingFromRight = false

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val action = ev.actionMasked
            val actionIndex = ev.actionIndex
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    scrollPointerId = ev.getPointerId(0)
                    downX = (ev.x + 0.5f).toInt()
                    downY = (ev.y + 0.5f).toInt()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    scrollPointerId = ev.getPointerId(actionIndex)
                    downX = (ev.getX(actionIndex) + 0.5f).toInt()
                    downY = (ev.getY(actionIndex) + 0.5f).toInt()
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = ev.findPointerIndex(scrollPointerId)
                    if (index < 0) {
                        return false
                    }

                    val x = (ev.getX(index) + 0.5f).toInt()
                    val y = (ev.getY(index) + 0.5f).toInt()
                    var dx = x - downX
                    var dy = y - downY

                    draggingFromRight = dx < 0f

                    isDragging = abs(dx) > touchSlop

                    if (isDragging) {
                        renderer.drag(x.toFloat(), y.toFloat(), !draggingFromRight)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        logcat { "Released drag" }
                        if (!draggingFromRight) {
                            viewer.moveRight()
                        } else {
                            viewer.moveLeft()
                        }
                        renderer.finishDrag(!draggingFromRight)
                        /* val thisAction = viewer.config.navigator.getAction(PointF(ev.x, ev.y))
                        when (viewer.config.navigator.getAction(
                            PointF(
                                downX.toFloat(),
                                downY.toFloat()
                            )
                        )) {
                            ViewerNavigation.NavigationRegion.NEXT -> {
                                if (thisAction == ViewerNavigation.NavigationRegion.PREV) {
                                    viewer.moveToPrevious()
                                }
                            }
                            ViewerNavigation.NavigationRegion.PREV -> {
                                if (thisAction == ViewerNavigation.NavigationRegion.NEXT) {
                                    viewer.moveToNext()
                                }
                            }
                            ViewerNavigation.NavigationRegion.RIGHT -> {
                                if (thisAction == ViewerNavigation.NavigationRegion.LEFT) {
                                    viewer.moveLeft()
                                }
                            }
                            ViewerNavigation.NavigationRegion.LEFT -> {
                                if (thisAction == ViewerNavigation.NavigationRegion.RIGHT) {
                                    viewer.moveRight()
                                }
                            }
                            else -> {
                                when (thisAction) {
                                    ViewerNavigation.NavigationRegion.MENU -> viewer.activity.toggleMenu()
                                    ViewerNavigation.NavigationRegion.NEXT -> viewer.moveToNext()
                                    ViewerNavigation.NavigationRegion.PREV -> viewer.moveToPrevious()
                                    ViewerNavigation.NavigationRegion.RIGHT -> viewer.moveRight()
                                    ViewerNavigation.NavigationRegion.LEFT -> viewer.moveLeft()
                                }
                            }
                        }*/
                    }
                    isDragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                }
            }
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
