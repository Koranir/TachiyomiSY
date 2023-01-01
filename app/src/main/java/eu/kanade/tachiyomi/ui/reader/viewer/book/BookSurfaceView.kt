package eu.kanade.tachiyomi.ui.reader.viewer.book

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.annotation.NonNull
import androidx.viewpager.widget.ViewPager
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

open class BookView(context: Context, rtl: Boolean) : ViewGroup(context) {
    var glView: BookSurfaceView = BookSurfaceView(context, rtl)

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    }

    var currentItem = 0

    var offscreenPageLimit = 1
    lateinit var adapter: BookViewerAdapter
    fun addOnPageChangeListener(@NonNull listener: ViewPager.OnPageChangeListener) {
    }

    fun removeOnPageChangeListener(@NonNull listener: ViewPager.OnPageChangeListener) {
    }

    fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        if (smoothScroll) {
            currentItem = item
        } else {
            currentItem = item
        }
    }

    fun setImage(item: Int, image: Bitmap) {
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return true
    }
}

class BookSurfaceView(context: Context, rtl: Boolean) : GLSurfaceView(context) {

    var surfaceHeight = 0
    var surfaceWidth = 0

    var imageWidth = 0
    var imageHeight = 0
    var imageLoaded = false

    var subImageWidth = 0
    var subImageHeight = 0
    var subImageLoaded = false

    private val renderer: BookRenderer

    init {
        holder.setFormat(PixelFormat.TRANSLUCENT)

        setEGLContextClientVersion(2)

        renderer = BookRenderer()

        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        surfaceHeight = w
        surfaceHeight = h
    }
}

class BookRenderer : GLSurfaceView.Renderer {
    var mProgram = 0
    var vao = 0

    var vertsI = 0

    val vertices = floatArrayOf(
        -1f,
        -1f,
        3f,
        -1f,
        -1f,
        3f,
    )

    var mVertices: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    val vertCode: String = "# version 320 es\n" +
        "in vec2 vert;\n" +
        "\n" +
        "out vec2 fragCoord;\n" +
        "\n" +
        "void main() {\n" +
        "  gl_Position = vec4(vert, 0, 1);\n" +
        "  fragCoord = 0.5 * vert + vec2(0.5);\n" +
        "}\n"

    val fragCode: String = "# version 320 es\n" +
        "\n" +
        "precision mediump float;\n" +
        "\n" +
        "in vec2 fragCoord;\n" +
        "out vec4 finalColor;\n" +
        "\n" +
        "void main()\n" +
        "{\n" +
        "  finalColor = vec4(fragCoord, 0, 1);\n" +
        "}\n"

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        mVertices.put(vertices)

        // Set the background frame color
        GLES20.glClearColor(0.0f, 0.2f, 0.8f, 1.0f)

        var mVert = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        if (mVert != 0) {
            GLES20.glShaderSource(mVert, vertCode)

            GLES20.glCompileShader(mVert)

            // Get the compilation status.
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(mVert, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

            // If the compilation failed, delete the shader.
            if (compileStatus[0] == 0) {
                logcat(LogPriority.ERROR) { "Vertex Shader Compilation Failed ${GLES20.glGetShaderInfoLog(mVert)}" }
                GLES20.glDeleteShader(mVert)
                mVert = 0
            }
        } else {
            logcat(LogPriority.ERROR) { "Vertex Shader Creation Failed" }
        }

        var mFrag = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        if (mFrag != 0) {
            GLES20.glShaderSource(mFrag, fragCode)

            GLES20.glCompileShader(mFrag)

            // Get the compilation status.
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(mFrag, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

            // If the compilation failed, delete the shader.
            if (compileStatus[0] == 0) {
                logcat(LogPriority.ERROR) { "Fragment Shader Compilation Failed ${GLES20.glGetShaderInfoLog(mFrag)}" }
                GLES20.glDeleteShader(mFrag)
                mFrag = 0
            }
        } else {
            logcat(LogPriority.ERROR) { "Fragment Shader Creation Failed" }
        }

        if (mVert == 0 || mFrag == 0) {
            logcat(LogPriority.ERROR) { "Missing compiled shader" }
        }

        mProgram = GLES20.glCreateProgram()

        if (mProgram != 0) {
            // Bind the vertex shader to the program.
            GLES20.glAttachShader(mProgram, mVert)

            // Bind the fragment shader to the program.
            GLES20.glAttachShader(mProgram, mFrag)

            GLES20.glBindAttribLocation(mProgram, 0, "vert")
            GLES20.glBindAttribLocation(mProgram, 1, "fragCoord")

            // Link the two shaders together into a program.
            GLES20.glLinkProgram(mProgram)

            // var status: IntBuffer = IntBuffer.allocate(1)
            // GLES20.glGetProgramiv(mProgram, GLES20.GL_LINK_STATUS, status)
            logcat(LogPriority.ERROR) { "Drew frame ${GLES20.glGetProgramInfoLog(mProgram)}" }

            GLES20.glDetachShader(mProgram, mVert)
            GLES20.glDetachShader(mProgram, mFrag)

            // Get the link status.
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(mProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)

            // If the link failed, delete the program.
            if (linkStatus[0] == 0) {
                logcat(LogPriority.ERROR) { "Drew frame ${GLES20.glGetError()}" }
                GLES20.glDeleteProgram(mProgram)
                mProgram = 0
            }
        }

        vertsI = GLES20.glGetAttribLocation(mProgram, "vert")

        GLES20.glUseProgram(mProgram)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        mVertices.position(0)
        GLES20.glVertexAttribPointer(vertsI, 3, GLES20.GL_FLOAT, false, 2 * 4, mVertices)
        GLES20.glEnableVertexAttribArray(vertsI)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        // logcat(LogPriority.ERROR) { "${GLES20.glGetError()}" }
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }
}
