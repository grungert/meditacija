package com.grungert.nebo.sky

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** One frame of sky state, handed from the UI thread to the GL thread. */
class SkyFrame(
    val time: Float, val drift: Float, val morph: Float, val wind: Float, val walk: Float,
    val breath: Float, val motion: Float, val pal: Palette,
    val sun: FloatArray, val moon: FloatArray, val sunAmt: Float, val moonAmt: Float, val light: FloatArray,
)

/** Renders the painted sky shader (the same GLSL as the browser prototype) at reduced resolution. */
class SkyRenderer(private val context: Context) : GLSurfaceView.Renderer {
    @Volatile var frame: SkyFrame? = null
    var onSlow: (() -> Unit)? = null

    private var program = 0
    private val loc = HashMap<String, Int>()
    private var width = 1
    private var height = 1
    private var frames = 0
    private var acc = 0L
    private var last = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val frag = context.assets.open("sky.frag").bufferedReader().readText()
        val vs = compile(GLES20.GL_VERTEX_SHADER, "attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}")
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, frag)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glBindAttribLocation(program, 0, "a")
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) Log.e(TAG, "link failed: " + GLES20.glGetProgramInfoLog(program))
        GLES20.glUseProgram(program)

        val tri = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        val buf = ByteBuffer.allocateDirect(tri.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(tri)
        buf.position(0)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, buf)

        loc.clear()
        for (n in listOf("uRes", "uTime", "uDrift", "uMorph", "uWind", "uWalk", "uBreath", "uMotion", "uSkyTop", "uSkyMid",
            "uSkyHor", "uSh", "uMd", "uLt", "uRim", "uSunCol", "uGrassTop", "uGrassBot", "uSunPos", "uMoonPos",
            "uSunAmt", "uMoonAmt", "uStars", "uLight")) {
            loc[n] = GLES20.glGetUniformLocation(program, n)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w; height = h
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        val f = frame ?: return
        val now = System.nanoTime()
        if (last != 0L) {
            acc += now - last; frames++
            if (frames == 60) {
                if (acc / frames > 24_000_000L) onSlow?.invoke()
                frames = 0; acc = 0
            }
        }
        last = now

        u1("uTime", f.time); u1("uDrift", f.drift); u1("uMorph", f.morph); u1("uWind", f.wind)
        u1("uWalk", f.walk); u1("uBreath", f.breath); u1("uMotion", f.motion)
        GLES20.glUniform2f(loc["uRes"]!!, width.toFloat(), height.toFloat())
        u3("uSkyTop", f.pal.skyTop); u3("uSkyMid", f.pal.skyMid); u3("uSkyHor", f.pal.skyHor)
        u3("uSh", f.pal.sh); u3("uMd", f.pal.md); u3("uLt", f.pal.lt); u3("uRim", f.pal.rim)
        u3("uSunCol", f.pal.sun); u3("uGrassTop", f.pal.gTop); u3("uGrassBot", f.pal.gBot)
        GLES20.glUniform2f(loc["uSunPos"]!!, f.sun[0], f.sun[1])
        GLES20.glUniform2f(loc["uMoonPos"]!!, f.moon[0], f.moon[1])
        u1("uSunAmt", f.sunAmt); u1("uMoonAmt", f.moonAmt); u1("uStars", f.pal.stars)
        u3("uLight", f.light)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
    }

    private fun u1(n: String, v: Float) = GLES20.glUniform1f(loc[n]!!, v)
    private fun u3(n: String, v: FloatArray) = GLES20.glUniform3f(loc[n]!!, v[0], v[1], v[2])

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) Log.e(TAG, "shader failed: " + GLES20.glGetShaderInfoLog(s))
        return s
    }

    companion object { private const val TAG = "NeboSky" }
}

/** Clouds are soft, so the sky renders below native resolution and the compositor scales it up. */
class SkyView(context: Context) : GLSurfaceView(context) {
    val renderer = SkyRenderer(context)
    private var scale = 0.6f

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        renderer.onSlow = { post { if (scale > 0.42f) { scale -= 0.08f; applyScale() } } }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyScale()
    }

    private fun applyScale() {
        if (width > 0 && height > 0) holder.setFixedSize((width * scale).toInt(), (height * scale).toInt())
    }
}
