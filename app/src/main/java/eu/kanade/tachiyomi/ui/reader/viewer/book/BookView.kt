package eu.kanade.tachiyomi.ui.reader.viewer.book

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.annotation.NonNull
import androidx.viewpager.widget.ViewPager
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

open class BookView(context: Context, rtl: Boolean) : GLSurfaceView(context) {

    var currentItem = 0

    var offscreenPageLimit = 1
    lateinit var adapter: BookViewerAdapter
    fun addOnPageChangeListener(@NonNull listener: ViewPager.OnPageChangeListener) {
    }

    fun removeOnPageChangeListener(@NonNull listener: ViewPager.OnPageChangeListener) {
    }

    private val renderer: BookRenderer

    init {
        setEGLContextClientVersion(2)

        renderer = BookRenderer()

        setRenderer(renderer)
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        if (smoothScroll) {
        } else {
            currentItem = item
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return true
    }
}

class BookRenderer : GLSurfaceView.Renderer {
    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // Set the background frame color
        GLES20.glClearColor(0.0f, 0.2f, 0.8f, 1.0f)
    }

    override fun onDrawFrame(unused: GL10) {
        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }
}
