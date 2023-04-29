package eu.kanade.tachiyomi.ui.reader.viewer.book

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.system.logcat
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

fun floatArrayToBuffer(floatArray: FloatArray): FloatBuffer {
    val byteBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(floatArray.size * 4)
    byteBuffer.order(ByteOrder.nativeOrder())
    val floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()
    floatBuffer.put(floatArray) /*ww w . jav a 2s .  c  o m*/
    floatBuffer.position(0)
    return floatBuffer
}

class BookRenderer(val viewer: BookViewer) : GLSurfaceView.Renderer {

    var shader: Int = 0
    var vbo = intArrayOf(1)

    var vertI = 0

    var displayAspectRatioI = 0
    var aspectRatioI = 0

    var pageOneI: Int = 0
    var pageTwoI: Int = 0

    var toDo1 = mutableListOf<(GL10) -> Unit>()
    var toDo2 = mutableListOf<(GL10) -> Unit>()
    var toDoCurrentIsOne = true

    private var displayAspectRatio = 1f

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig?) {
        GLES20.glClearColor(1f, 0f, 1f, 1f)
        logcat { GLES20.glGetString(GLES20.GL_VERSION) }
        logcat { "Initing prog!" }
        shader = GLES20.glCreateProgram()
        logcat { "Created program with data $shader!" }
        if (shader == 0) {
            logcat { "Failed to init program!" }
        }

        var vert = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        logcat { "Created vertex shader with data $vert!" }
        if (vert == 0) {
            logcat(message = { "FAILED TO CREATE VERTEX SHADER." })
        }
        GLES20.glShaderSource(vert, vertexCode)
        GLES20.glCompileShader(vert)

        var status = intArrayOf(1)
        GLES20.glGetShaderiv(vert, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == GLES20.GL_FALSE) {
            logcat(message = { "FAILED TO COMPILE VERTEX SHADER. PROBLEM: ${GLES20.glGetShaderInfoLog(vert)}" })
        }

        var frag = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        logcat { "Created fragment shader with data $frag!" }
        if (frag == 0) {
            logcat(message = { "FAILED TO CREATE VERTEX SHADER." })
        }
        GLES20.glShaderSource(frag, fragmentCode)
        GLES20.glCompileShader(frag)

        GLES20.glGetShaderiv(frag, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == GLES20.GL_FALSE) {
            logcat(message = { "FAILED TO COMPILE FRAGMENT SHADER. PROBLEM: ${GLES20.glGetShaderInfoLog(frag)}" })
        }

        GLES20.glAttachShader(shader, vert)
        GLES20.glAttachShader(shader, frag)

        GLES20.glLinkProgram(shader)
        var lnik = intArrayOf(1)
        GLES20.glGetProgramiv(shader, GLES20.GL_LINK_STATUS, lnik, 0)
        if (lnik[0] == GLES20.GL_FALSE) {
            val info = GLES20.glGetProgramInfoLog(shader)
            logcat(message = { "FAILED TO LINK PROGRAM. PROBLEM: $info" })
        }

        GLES20.glUseProgram(shader)

        GLES20.glGenBuffers(1, vbo, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * Float.SIZE_BYTES, vertexBuffer, GLES20.GL_STATIC_DRAW)

        vertI = GLES20.glGetAttribLocation(shader, "vert")

        GLES20.glVertexAttribPointer(vertI, 2, GLES20.GL_FLOAT, false, 8, 0)
        GLES20.glEnableVertexAttribArray(vertI)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        displayAspectRatioI = GLES20.glGetUniformLocation(shader, "displayAspectRatio")
        aspectRatioI = GLES20.glGetUniformLocation(shader, "aspectRatio")

        pageOneI = GLES20.glGetUniformLocation(shader, "page")
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        displayAspectRatio = width.toFloat() / height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10) {
        if (toDoCurrentIsOne) {
            toDoCurrentIsOne = false
            for (func in toDo1) {
                // logcat { "LOL, THIS SUCKS. LOADING TEXTURE!" }
                func(gl)
            }
            toDo1.clear()
        } else {
            toDoCurrentIsOne = true
            for (func in toDo2) {
                // logcat { "LOL, THIS SUCKS. LOADING TEXTURE!" }
                func(gl)
            }
            toDo2.clear()
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(shader)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(pageOneI, 0)
        GLES20.glUniform1f(displayAspectRatioI, displayAspectRatio)

        if (viewer.getPagesToDraw() != null) {
            var (pageOne, pageTwo) = viewer.getPagesToDraw()!!
            if (pageOne.loaded) {
                // logcat { "Loading pageOne with value ${pageOne.gl_texture[0]}" }
                GLES20.glUniform1f(aspectRatioI, pageOne.width.toFloat() / pageOne.height)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pageOne.gl_texture[0])
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            } else {
                logcat { "Page One not loaded!" }
            }
            if (pageTwo != null) {
                if (pageTwo!!.loaded) {
                    // logcat { "Loading pageTwo with value ${pageTwo.gl_texture[0]}" }
                    GLES20.glUniform1f(aspectRatioI, pageTwo.width.toFloat() / pageTwo.height)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pageTwo.gl_texture[0])
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
                } else {
                    logcat { "Page two is not loaded" }
                }
            } else {
                logcat { "Page two is null" }
            }
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private val vertices = floatArrayOf(
        // X, Y
        0f, 0f,
        1f, 1f,
        0f, 1f,
        0f, 0f,
        1f, 0f,
        1f, 1f,
    )

    private val vertexBuffer = floatArrayToBuffer(vertices)

    private val vertexCode = """#version 100
        // Vertex Book Shader
        
        precision mediump float;
        
        varying float displayAspectRatio;
        varying float aspectRatio;
        
        attribute vec2 vert;
        
        varying vec2 texCoords;
        
        void main() {
            vec2 modVert = vec2(vert.x - 0.5, (vert.y - 0.5) * displayAspectRatio / aspectRatio) * 2.;
            gl_Position = vec4(modVert, 0, 1);
            texCoords = vec2(vert);
        }
        
        """

    private val fragmentCode = """#version 100
        // Fragment Book Shader
        
        precision mediump float;
        precision mediump sampler2D;
        
        uniform sampler2D page;
        
        varying vec2 texCoords;
        
        varying vec4 color;
        
        void main() {
            vec2 nTexCoords = vec2(texCoords.x, -texCoords.y);
            gl_FragColor = vec4(texture2D(page, nTexCoords).rgb, 1.);
        }
        
        """
}

class BookRendererPage(
    val page: ReaderPage,
    val extraPage: ReaderPage? = null,
    val renderer: BookRenderer,
) : Closeable {
    var gl_texture = intArrayOf(1)
    var baseBitmap: Bitmap? = null

    var width = 1
    var height = 1

    var loaded = false

    var progress = 0

    private val scope = MainScope()

    /**
     * Job for loading the page and processing changes to the page's status.
     */
    private var loadJob: Job? = null

    init {
        loadJob = scope.launch { loadPageAndProcessStatus(1) }
    }

    /**
     * Loads the page and processes changes to the page's status.
     *
     * Returns immediately if the page has no PageLoader.
     * Otherwise, this function does not return. It will continue to process status changes until
     * the Job is cancelled.
     */
    private suspend fun loadPageAndProcessStatus(pageIndex: Int) {
        // SY -->
        val page = if (pageIndex == 1) page else extraPage
        page ?: return
        // SY <--
        val loader = page.chapter.pageLoader ?: return
        supervisorScope {
            launchIO {
                loader.loadPage(page)
            }
            page.statusFlow.collectLatest { state ->
                when (state) {
                    Page.State.QUEUE -> setQueued()
                    Page.State.LOAD_PAGE -> setLoading()
                    Page.State.DOWNLOAD_IMAGE -> {
                        setDownloading()
                        page.progressFlow.collectLatest { value ->
                            progress = value
                        }
                    }
                    Page.State.READY -> {
                        progress = 100
                        setImage()
                    }
                    Page.State.ERROR -> setError()
                }
            }
        }
    }

    fun setQueued() {
        logcat(message = { "By the gods! Image in ${page.number} just queued!" })
    }

    fun setLoading() {
        logcat(message = { "By the gods! Image in ${page.number} is loading!" })
    }

    fun setDownloading() {
        logcat(message = { "By the gods! Image in ${page.number} is downloading!" })
    }

    fun setImage() {
        logcat(message = { "By the gods! Image in ${page.number} just loaded!" })
        baseBitmap = BitmapFactory.decodeStream(page.stream?.invoke())

        width = baseBitmap?.width!!
        height = baseBitmap?.height!!

        if (renderer.toDoCurrentIsOne) {
            renderer.toDo1.add {
                GLES20.glGenTextures(1, gl_texture, 0)

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, gl_texture[0])

                // Set filtering
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)

                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, baseBitmap, 0)

                baseBitmap!!.recycle()
                baseBitmap = null
                loaded = true
            }
        } else {
            renderer.toDo2.add {
                GLES20.glGenTextures(1, gl_texture, 0)

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, gl_texture[0])

                // Set filtering
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)

                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, baseBitmap, 0)

                baseBitmap!!.recycle()
                baseBitmap = null
                loaded = true
            }
        }
    }

    fun setError() {
        logcat(message = { "By the gods! Image in ${page.number} just failed!" })
    }

    fun isTheSamePageAs(other: ReaderPage): Boolean {
        return other == page
    }

    override fun close() {
        logcat { "Deleting unused texture from page ${page.number}." }
        GLES20.glDeleteTextures(1, gl_texture, 0)
    }
}
