package eu.kanade.tachiyomi.ui.reader.viewer.book

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import androidx.annotation.NonNull
import androidx.viewpager.widget.ViewPager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

open class BookView(activity: Activity, rtl: Boolean) : ViewGroup(activity) {
    private var glView: BookSurfaceView

    init {
        glView = BookSurfaceView(activity, rtl)
        addView(glView)
        addView(View(activity))
        glView.setOnTouchListener(
            OnTouchListener { v, event ->
                val action = event.action
                when (action and MotionEvent.ACTION_MASK) {
                    MotionEvent.ACTION_DOWN -> {
                        glView.renderer.iMouseX = event.x
                        glView.renderer.iMouseY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        glView.renderer.iMouseX = event.x
                        glView.renderer.iMouseY = event.y
                    }
                    MotionEvent.ACTION_UP -> {
                        glView.renderer.resetMouse()
                    }
                }
                true
            },
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        updateLayout(l, t, r, b)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        logcat(message = { "Size changed" })
        super.onSizeChanged(w, h, oldw, oldh)
    }

    fun updateLayout(l: Int, t: Int, r: Int, b: Int) {
        logcat(message = { "Layout changed" })
        glView.layout(l, t, r, b)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        logcat(message = { "Measured" })
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    var currentItem = 0

    var offscreenPageLimit = 1
    lateinit var adapter: BookViewerAdapter
    fun addOnPageChangeListener(@NonNull listener: ViewPager.OnPageChangeListener) {
    }

    fun removeOnPageChangeListener(@NonNull listener: ViewPager.OnPageChangeListener) {
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        glView.renderer.drawFrame()
    }

    fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        if (smoothScroll) {
            currentItem = item
        } else {
            currentItem = item
        }
    }

    fun setImage(item: Int, image: Bitmap) {
        glView.setImage(item, image)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}

class BookSurfaceView(context: Context, rtl: Boolean) : GLSurfaceView(context) {

    val renderer: BookRenderer

    init {
        holder.setFormat(PixelFormat.TRANSLUCENT)

        setEGLContextClientVersion(2)

        renderer = BookRenderer(context)

        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setImage(item: Int, image: Bitmap) {
        renderer.loadTexture(item, image)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        renderer.drawFrame()
        // renderer.drawFrame()
    }

    override fun layout(l: Int, t: Int, r: Int, b: Int) {
        super.layout(l, t, r, b)
        logcat(message = { "Layout changed for child" })
        logcat(message = { "values: ${renderer.iMouseX}, ${renderer.iMouseY}, ${renderer.surfaceWidth}, ${renderer.surfaceHeight}, ${renderer.imageHeight}, ${renderer.imageWidth}" })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        logcat(message = { "Size changed for child" })
        renderer.surfaceHeight = h
        renderer.surfaceWidth = w
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        logcat(message = { "Measured child" })
    }
}

class BookRenderer(contexts: Context) : GLSurfaceView.Renderer {
    var mProgram = 0
    var vao = 0

    var context: Context

    init {
        context = contexts
    }

    var iResolutionI = 0
    var iMouseI = 0

    var imageSizeI = 0
    var subImageSizeI = 0

    var iChannel0I = 0
    var iChannel1I = 0

    var surfaceHeight = 800 //
    var surfaceWidth = 600 //

    var iMouseX = 0f //
    var iMouseY = 0f //

    var smoothMouseX = 0f
    var smoothMouseY = 0f

    var imageWidth = 100
    var imageHeight = 141
    var imageLoaded = false
    var mImage = IntArray(1)

    var subImageWidth = 100
    var subImageHeight = 141
    var subImageLoaded = false
    var mSubImage = IntArray(1)

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

    val vertCode: String = "#version 300 es\n" +
        "in vec2 vert;\n" +
        "\n" +
        "out vec2 fragTexCoord;\n" +
        "\n" +
        "void main() {\n" +
        "  gl_Position = vec4(vert, 0, 1);\n" +
        "  fragTexCoord = 0.5 * vert + vec2(0.5);\n" +
        "}\n"

    val fragCode: String = "#version 300 es\n" +
        "\n" +
        "#define topleft vec2(0., 1./imageRatio)\n" +
        "#define topright vec2(1., 1./imageRatio)\n" +
        "#define bottomright vec2(1., 0.)\n" +
        "#define scale1 vec2(1., 1./imageRatio)\n" +
        "#define fragCoord (fragTexCoord*iResolution)\n" +
        "\n" +
        "precision mediump float;\n" +
        "\n" +
        "uniform sampler2D iChannel0;\n" +
        "uniform sampler2D iChannel1;\n" +
        "uniform vec2 iResolution;\n" +
        "uniform vec2 iMouse;\n" +
        "uniform vec2 subImageSize;\n" +
        "uniform vec2 mainImageSize;\n" +
        "\n" +
        "in vec2 fragTexCoord;\n" +
        "out vec4 fragColor;\n" +
        "\n" +
        "vec2 scaledImageSize;\n" +
        "vec2 imageOffset;\n" +
        "\n" +
        "vec2 intersects(vec2 A, vec2 B, vec2 C, vec2 D)\n" +
        "{\n" +
        "    float a1 = B.y - A.y;\n" +
        "    float b1 = A.x - B.x;\n" +
        "    float c1 = a1*A.x + b1*A.y;\n" +
        "    \n" +
        "    float a2 = D.y - C.y;\n" +
        "    float b2 = C.x - D.x;\n" +
        "    float c2 = a2*C.x + b2*C.y;\n" +
        "    \n" +
        "    float determinate = a1*b2 - a2*b1;\n" +
        "    if(determinate == 0.)\n" +
        "    {\n" +
        "        return vec2(-1., -1.);\n" +
        "    }\n" +
        "    \n" +
        "    float x = (b2*c1 - b1*c2)/determinate;\n" +
        "    float y = (a1*c2 - a2*c1)/determinate;\n" +
        "    \n" +
        "    return(vec2(x, y));\n" +
        "}\n" +
        "\n" +
        "\n" +
        "\n" +
        "vec2 uvmap(vec2 point)\n" +
        "{\n" +
        "    return (point - imageOffset)/scaledImageSize;\n" +
        "}\n" +
        "\n" +
        "\n" +
        "\n" +
        "bool clampVec(vec2 uv)\n" +
        "{\n" +
        "    if(uv.x < 0. || uv.y < 0. || uv.x > 1. || uv.y > 1.)\n" +
        "        return true;\n" +
        "    return false;\n" +
        "}\n" +
        "\n" +
        "\n" +
        "\n" +
        "void main()\n" +
        "{\n" +
        "    float scale;\n" +
        "    float imageRatio;\n" +
        "    vec3 outp;\n" +
        "    \n" +
        "    float screenRatio = iResolution.x/iResolution.y;\n" +
        "    imageRatio = mainImageSize.x/mainImageSize.y;\n" +
        "    \n" +
        "\n" +
        "    \n" +
        "    if(imageRatio > screenRatio)\n" +
        "    {\n" +
        "        scale = iResolution.x/mainImageSize.x;\n" +
        "        scaledImageSize = mainImageSize * scale;\n" +
        "        imageOffset = (iResolution.xy - scaledImageSize)/2.;\n" +
        "    }\n" +
        "    else\n" +
        "    {\n" +
        "        scale = iResolution.y/mainImageSize.y;\n" +
        "        scaledImageSize = mainImageSize * scale;\n" +
        "        imageOffset = (iResolution.xy - scaledImageSize)/2.;\n" +
        "    }\n" +
        "    \n" +
        "    // Normalized pixel coordinates (from 0 to 1)\n" +
        "    vec2 uv = uvmap(fragCoord)*scale1;\n" +
        "    vec2 mouseuv = uvmap(iMouse.xy)*scale1;\n" +
        "    if(distance(mouseuv, topright) > 1.)\n" +
        "    {\n" +
        "        mouseuv = topright + normalize(mouseuv - topright);\n" +
        "    }\n" +
        "    \n" +
        "    vec2 tlmousevec = normalize(mouseuv - topleft);\n" +
        "    vec2 midpoint = (mouseuv + topleft)/2.;\n" +
        "\n" +
        "    vec2 itlmousevec = vec2(-tlmousevec.y, tlmousevec.x);\n" +
        "    vec2 leftmost = midpoint - itlmousevec;\n" +
        "    vec2 rightmost = midpoint + itlmousevec;\n" +
        "    \n" +
        "    vec2 sIntersects = intersects(leftmost, rightmost, vec2(1., -10.), vec2(1., 10.));\n" +
        "    \n" +
        "    if(sIntersects.y > 0. && mouseuv.y > topleft.y)\n" +
        "    {\n" +
        "        mouseuv.y -= sIntersects.y;\n" +
        "        midpoint.y -= sIntersects.y;\n" +
        "        leftmost.y -= sIntersects.y;\n" +
        "        rightmost.y -= sIntersects.y;\n" +
        "    }\n" +
        "\n" +
        "    vec2 intersect = intersects(leftmost, rightmost, uv, uv - tlmousevec);\n" +
        "    \n" +
        "    vec2 mirroredpoint = intersect + (intersect - uv);\n" +
        "    float distfrom = distance(intersect, uv);\n" +
        "    \n" +
        "    bool inBounds = dot(normalize(uv - midpoint), tlmousevec) > 0.;\n" +
        "    \n" +
        "    vec3 color;\n" +
        "    vec3 color2;\n" +
        "    mirroredpoint /= scale1;\n" +
        "    uv /= scale1;\n" +
        "    \n" +
        "    if(clampVec(mirroredpoint))\n" +
        "    {\n" +
        "        color = texture(iChannel0, uv/scale1).rgb;\n" +
        "        color = vec3(mirroredpoint/scale1, 0);\n" +
        "        color *=  clamp(pow(5.*distfrom, .1), 0., 1.);\n" +
        "    }\n" +
        "    else\n" +
        "    {\n" +
        "        color = texture(iChannel0, mirroredpoint/scale1).rgb;\n" +
        "        color = vec3(mirroredpoint/scale1, 0);\n" +
        "        color *=  clamp(pow(5.*distfrom, .2), 0., 1.);\n" +
        "    }\n" +
        "    \n" +
        "    imageRatio = subImageSize.x/subImageSize.y;\n" +
        "    if(imageRatio > screenRatio)\n" +
        "        scale = iResolution.x/subImageSize.x;\n" +
        "    else\n" +
        "        scale = iResolution.y/subImageSize.y;\n" +
        "    scaledImageSize = subImageSize * scale;\n" +
        "    imageOffset = (iResolution.xy - scaledImageSize)/2.;\n" +
        "    vec2 subUV = uvmap(fragCoord);\n" +
        "    \n" +
        "    if(((clampVec(uv) && clampVec(mirroredpoint)) || !inBounds))\n" +
        "    {\n" +
        "        if(!clampVec(subUV))\n" +
        "        {\n" +
        "            color = texture(iChannel1, subUV).rgb;\n" +
        "            color = vec3(subUV, 0);\n" +
        "        }\n" +
        "        else\n" +
        "            color = vec3(0);\n" +
        "        color *=  clamp(pow(5.*distfrom, .2), 0., 1.);\n" +
        "    }\n" +
        "    fragColor = vec4(color, 1.);\n" +
        "}"

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        logcat(message = { "Creating OpenGL Surface" })

        mVertices.put(vertices)

        // Set the background frame color
        GLES20.glClearColor(0.0f, 0.2f, 0.8f, 1.0f)

        GLES20.glGenTextures(1, mImage, 0)
        GLES20.glGenTextures(1, mSubImage, 0)

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

        iResolutionI = GLES20.glGetUniformLocation(mProgram, "iResolution")
        iMouseI = GLES20.glGetUniformLocation(mProgram, "iMouse")
        iChannel0I = GLES20.glGetUniformLocation(mProgram, "iChannel0")
        iChannel1I = GLES20.glGetUniformLocation(mProgram, "iChannel1")
        imageSizeI = GLES20.glGetUniformLocation(mProgram, "mainImageSize")
        subImageSizeI = GLES20.glGetUniformLocation(mProgram, "subImageSize")

        val options = BitmapFactory.Options()
        options.inScaled = false // No pre-scaling

        // Read in the resource
        val bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.updates_grid_widget_preview,
            options,
        )

        loadTexture(0, bitmap)
    }

    fun drawFrame() {
        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(mProgram)
        mVertices.position(0)
        GLES20.glVertexAttribPointer(vertsI, 3, GLES20.GL_FLOAT, false, 2 * 4, mVertices)
        GLES20.glEnableVertexAttribArray(vertsI)

        GLES20.glUniform2f(iResolutionI, surfaceWidth.toFloat(), surfaceHeight.toFloat())
        GLES20.glUniform2f(iMouseI, smoothMouseX, surfaceHeight.toFloat() - smoothMouseY)
        GLES20.glUniform2f(imageSizeI, imageWidth.toFloat(), imageHeight.toFloat())
        GLES20.glUniform2f(subImageSizeI, subImageWidth.toFloat(), subImageHeight.toFloat())

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mImage[0])
        GLES20.glUniform1i(iChannel0I, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mSubImage[0])
        GLES20.glUniform1i(iChannel1I, 1)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        // logcat(LogPriority.ERROR) { "${GLES20.glGetError()}" }
    }

    fun loadTexture(item: Int, image: Bitmap) {
        logcat(LogPriority.ERROR) { "Tried to load Texture to channel $item" }
        when (item) {
            0 -> {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mImage[0])
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, image, 0)
                imageWidth = image.width
                imageHeight = image.height
            }
            1 -> {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mImage[1])
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, image, 0)
                subImageWidth = image.width
                subImageHeight = image.height
            }
        }
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        smoothMouseX = lerp(smoothMouseX, iMouseX, 0.3f)
        smoothMouseY = lerp(smoothMouseY, iMouseY, 0.3f)
        drawFrame()
    }

    fun lerp(a: Float, b: Float, t: Float): Float {
        return a + ((b - a) * t)
    }

    fun resetMouse() {
        iMouseY = getOffset().second + 1f
        iMouseX = 1f + getOffset().first
    }

    fun getOffset(): Pair<Float, Float> {
        var screenRatio = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        var imageRatio = imageWidth.toFloat() / imageHeight.toFloat()
        var imageOffsetX = 0f
        var imageOffsetY = 0f
        if (imageRatio > screenRatio) {
            imageOffsetY = surfaceHeight.toFloat() - (imageHeight.toFloat() * (surfaceWidth.toFloat() / imageWidth.toFloat()))
        } else {
            imageOffsetX = surfaceWidth.toFloat() - (imageWidth.toFloat() * (surfaceHeight.toFloat() / imageHeight.toFloat()))
        }
        return Pair<Float, Float>(imageOffsetX / 2f, imageOffsetY / 2f)
    }
}
