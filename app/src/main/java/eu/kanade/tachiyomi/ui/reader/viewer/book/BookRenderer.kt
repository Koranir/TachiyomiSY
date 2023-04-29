package eu.kanade.tachiyomi.ui.reader.viewer.book

import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.system.logcat
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLES30.*
import java.nio.IntBuffer

class BookRenderer(val viewer: BookViewer) : GLSurfaceView.Renderer {

    var shader: Int = 0

    var vao: IntArray

    var pageOneI: Int = 0
    var pageTwoI: Int = 0

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig?) {
        GLES30.glClearColor(1f, 0f, 1f, 1f)
        shader = GLES30.glCreateProgram()

        var vert = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER)
        GLES30.glShaderSource(vert, vertexCode)
        glCompileShader(vert)

        var frag = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER)
        GLES30.glShaderSource(frag, fragmentCode)
        glCompileShader(frag)

        glAttachShader(shader, vert)
        glAttachShader(shader, frag)

        glLinkProgram(shader)

        glUseProgram(shader)



    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        viewer.getPagesToDraw()?.let {
            val (firstPage, secondPage) = viewer.getPagesToDraw()!!
        }
        glUseProgram(shader)
        glDrawArrays()
    }

    private val vertices = floatArrayOf(
        // X, Y
        0f,
        1f,
        1f,
        1f,
        1f,
        0f,
        0f,
        0f,
    )

    private val vertexCode = """"
        #version 300 es
        // Vertex Book Shader
        
        precision mediump float;
        
        uniform float displayAspectRatio;
        uniform float aspectRatio;
        
        in vec2 vert;
        
        out vec2 texCoords;
        
        void main() {
            float2 fVert;
            /*if(aspectRatio > displayAspectRatio) {
                fVert.x = aVert.x;
                fVert.y = (aVert.y / displayAspectRatio) * aspectRatio;
            } else {
                fVert.x = aVert.x / displa
            }*/
            gl_Position = vec4(vert, 0, 1);
            texCoords = vec2(vert);
        }
        
        """

    private val fragmentCode = """"
        #version 300 es
        // Fragment Book Shader
        
        precision mediump float;
        
        in vec2 texCoords;
        
        out vec4 color;
        
        void main() {
            color = vec4(texCoords, 0, 1);
        }
        
        """
}

class BookRendererPage(
    val page: ReaderPage,
    val extraPage: ReaderPage? = null,
) {
    var gl_texture: Int? = null

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
    }

    fun setError() {
        logcat(message = { "By the gods! Image in ${page.number} just failed!" })
    }

    fun isTheSamePageAs(other: ReaderPage): Boolean {
        return other == page
    }
}
