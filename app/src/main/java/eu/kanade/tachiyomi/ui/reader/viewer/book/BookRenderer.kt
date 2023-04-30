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

    var deltaTimeI = 0
    var secondsI = 0
    var isDraggingI = 0

    var toDo1 = mutableListOf<(GL10) -> Unit>()
    var toDo2 = mutableListOf<(GL10) -> Unit>()
    var toDoCurrentIsOne = true

    private val startTime = System.currentTimeMillis()
    private var lastFrameTime = System.currentTimeMillis()

    private var displayAspectRatio = 1f

    private var width = 1
    private var height = 1

    var touchI = 0
    var sourceI = 0

    private var fromLeft = false

    private var touchX = 0f
    private var touchY = 0f
    private var isDragging = false

    private var isSlipI = 0

    fun drag(x: Float, y: Float, fromLeft: Boolean) {
        touchX = (x / width)
        touchY = (y / height)
        isDragging = true
        this.fromLeft = fromLeft
    }

    fun finishDrag(fromLeft: Boolean) {
        isDragging = false
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
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

        aspectRatioI = GLES20.glGetUniformLocation(shader, "aspectRatio")
        displayAspectRatioI = GLES20.glGetUniformLocation(shader, "displayAspectRatio")

        pageOneI = GLES20.glGetUniformLocation(shader, "page")

        deltaTimeI = GLES20.glGetUniformLocation(shader, "deltaTime")
        secondsI = GLES20.glGetUniformLocation(shader, "seconds")

        touchI = GLES20.glGetUniformLocation(shader, "touch")
        isDraggingI = GLES20.glGetUniformLocation(shader, "isDragging")
        sourceI = GLES20.glGetUniformLocation(shader, "source")

        isSlipI = GLES20.glGetUniformLocation(shader, "isSlip")

        logcat { "dAR: $displayAspectRatioI, aR: $aspectRatioI, p1: $pageOneI" }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        this.width = width
        this.height = height
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

        val currentTimeMillis = System.currentTimeMillis()
        val elapsedTimeMillis = currentTimeMillis - lastFrameTime
        val passedTimeTotalMillis = currentTimeMillis - startTime
        lastFrameTime = currentTimeMillis

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glUseProgram(shader)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(pageOneI, 0)
        GLES20.glUniform1f(displayAspectRatioI, displayAspectRatio)

        GLES20.glUniform1f(deltaTimeI, elapsedTimeMillis / 1000f)
        GLES20.glUniform1f(secondsI, passedTimeTotalMillis / 1000f)

        GLES20.glUniform2f(touchI, touchX, touchY)
        // logcat { "Moved to $touchX, $touchY." }
        GLES20.glUniform2f(
            sourceI,
            if (fromLeft) 0f else 1f,
            0f,
        )

        GLES20.glUniform1i(isSlipI, 0)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        if (viewer.getPagesToDraw() != null) {
            val (pageOne, pageTwo) = viewer.getPagesToDraw()!!
            if (pageTwo != null) {
                if (pageTwo.loaded) {
                    // logcat { "Loading pageTwo with value ${pageTwo.gl_texture[0]}" }
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pageTwo.gl_texture[0])
                    GLES20.glUniform1f(aspectRatioI, pageTwo.width.toFloat() / pageTwo.height.toFloat())
                    GLES20.glUniform1i(isDraggingI, 0)
                    // logcat { "Drawing page 2 with texture ${pageTwo.gl_texture[0]}" }
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
                } else {
                    logcat { "Page two is not loaded" }
                }
            } else {
                logcat { "Page two is null" }
            }
            if (pageOne.loaded) {
                // logcat { "Loading pageOne with value ${pageOne.gl_texture[0]}" }
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pageOne.gl_texture[0])
                GLES20.glUniform1f(aspectRatioI, pageOne.width.toFloat() / pageOne.height.toFloat())
                GLES20.glUniform1i(isDraggingI, if (isDragging) 1 else 0)
                // logcat { "Aspect Ratio is: ${pageOne.width.toFloat() / pageOne.height}" }
                // logcat { "Drawing page 1 with texture ${pageOne.gl_texture[0]}" }
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
                GLES20.glUniform1i(isSlipI, 1)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            } else {
                logcat { "Page One not loaded!" }
            }
        } else {
            logcat { "Viewer.GetPages IS NULL!!!" }
        }

        // GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

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
        precision mediump int;
        
        uniform float aspectRatio;
        uniform float displayAspectRatio;
        
        uniform vec2 source;
        uniform vec2 touch;
        
        attribute vec2 vert;
        
        varying vec2 texCoords;
        varying vec2 vPos;
        
        uniform int isSlip;
        
        vec2 intersects(vec2 A, vec2 B, vec2 C, vec2 D)
        {
            float a1 = B.y - A.y;
            float b1 = A.x - B.x;
            float c1 = a1*A.x + b1*A.y;
            
            float a2 = D.y - C.y;
            float b2 = C.x - D.x;
            float c2 = a2*C.x + b2*C.y;
            
            float determinate = a1*b2 - a2*b1;
            if(determinate == 0.)
            {
                return vec2(-1., -1.);
            }
            
            float x = (b2*c1 - b1*c2)/determinate;
            float y = (a1*c2 - a2*c1)/determinate;
            
            return(vec2(x, y));
        }
        
        void main() {
            vec2 modVert = vert;
            
            if(isSlip == 1) {
                vec2 touchPoint = vec2(touch.x, (touch.y - 0.5) * displayAspectRatio / aspectRatio * 0.5);
                /*if(distance(source, touchPoint) > 1.) {
                
                }*/
                vec2 touchPointVec = normalize(touchPoint - source);
                vec2 midPoint = (touchPoint + source) / 2.;
                vec2 iTouchPointVec = vec2(-touchPointVec.y, touchPointVec.x);
                vec2 leftMost = midPoint - iTouchPointVec;
                vec2 rightMost = midPoint + iTouchPointVec;
                
                vec2 intersect = intersects(leftMost, rightMost, modVert, modVert - touchPointVec);
                modVert = intersect + (intersect - modVert);
                modVert = vec2(modVert.x, 1. - modVert.y);
            }
            
            if(aspectRatio > displayAspectRatio) {
                modVert = vec2(modVert.x - 0.5, (modVert.y - 0.5) * displayAspectRatio / aspectRatio) * 2.;
            } else {
                modVert = vec2((modVert.x - 0.5) / displayAspectRatio * aspectRatio, (modVert.y - 0.5)) * 2.;
            }
            
            vec4 outPos = vec4(modVert, 0., 1.);
            gl_Position = outPos;
            texCoords = vec2(vert.x, 1. - vert.y);
            vPos = modVert;
        }
        
        """

    private val fragmentCode = """#version 100
        // Fragment Book Shader
        
        precision mediump float;
        precision mediump sampler2D;
        precision mediump int;
        
        uniform sampler2D page;
        
        uniform float aspectRatio;
        uniform float displayAspectRatio;
        
        uniform float deltaTime;
        uniform float seconds;
        
        uniform vec2 source;
        uniform vec2 touch;
        uniform int isDragging;
        uniform int isSlip;
        
        varying vec2 texCoords;
        varying vec2 vPos;
        
        void main() {
            vec2 touchCorrect = vec2(touch.x, (touch.y - 0.5) / displayAspectRatio * aspectRatio + 0.5);
            vec4 color;
            
            if(isDragging == 1 && isSlip == 0 ) {
                if(distance(touchCorrect, texCoords) > distance(source, texCoords)) {
                    discard;
                }
                gl_FragColor = vec4(texture2D(page, texCoords).rgb * min(distance(texCoords, touchCorrect) * 10., 1.) * min(distance(texCoords, source) * 10., 1.), 1.);
            } else {
                gl_FragColor = vec4(texture2D(page, texCoords).rgb, 1.);
            }
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

                logcat { "Loaded texture for page ${page.number}" }
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
