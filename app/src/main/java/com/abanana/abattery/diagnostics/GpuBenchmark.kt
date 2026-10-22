package com.abanana.abattery.diagnostics

import android.opengl.EGL14
import android.opengl.GLES20
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

object GpuBenchmark {
    suspend fun run(): Double {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        var surface = EGL14.EGL_NO_SURFACE
        var context = EGL14.EGL_NO_CONTEXT
        var program = 0
        var vertex = 0
        var fragment = 0
        try {
            check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1)) {
                "EGL initialization failed"
            }
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            check(
                EGL14.eglChooseConfig(
                    display,
                    intArrayOf(
                        EGL14.EGL_RENDERABLE_TYPE,
                        EGL14.EGL_OPENGL_ES2_BIT,
                        EGL14.EGL_SURFACE_TYPE,
                        EGL14.EGL_PBUFFER_BIT,
                        EGL14.EGL_NONE,
                    ),
                    0,
                    configs,
                    0,
                    1,
                    count,
                    0,
                ) && count[0] > 0
            )
            context =
                EGL14.eglCreateContext(
                    display,
                    configs[0],
                    EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                    0,
                )
            surface =
                EGL14.eglCreatePbufferSurface(
                    display,
                    configs[0],
                    intArrayOf(EGL14.EGL_WIDTH, 512, EGL14.EGL_HEIGHT, 512, EGL14.EGL_NONE),
                    0,
                )
            check(EGL14.eglMakeCurrent(display, surface, surface, context))
            fun shader(type: Int, source: String): Int {
                val shader = GLES20.glCreateShader(type)
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val status = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
                if (status[0] != GLES20.GL_TRUE) {
                    val log = GLES20.glGetShaderInfoLog(shader)
                    GLES20.glDeleteShader(shader)
                    error(log)
                }
                return shader
            }
            vertex =
                shader(
                    GLES20.GL_VERTEX_SHADER,
                    "attribute vec2 position; varying vec2 uv; void main(){uv=position;gl_Position=vec4(position,0.0,1.0);}",
                )
            fragment =
                shader(
                    GLES20.GL_FRAGMENT_SHADER,
                    "precision mediump float; varying vec2 uv; void main(){vec2 z=uv; for(int i=0;i<24;i++){z=vec2(z.x*z.x-z.y*z.y,2.0*z.x*z.y)*0.5+uv*0.1;} gl_FragColor=vec4(abs(z),0.5,1.0);}",
                )
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            val linked = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
            check(linked[0] == GLES20.GL_TRUE)
            GLES20.glUseProgram(program)
            val vertices =
                java.nio.ByteBuffer.allocateDirect(6 * 4)
                    .order(java.nio.ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
                        position(0)
                    }
            val position = GLES20.glGetAttribLocation(program, "position")
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
            GLES20.glViewport(0, 0, 512, 512)
            val started = System.nanoTime()
            var frames = 0L
            while (System.nanoTime() - started < 1_500_000_000) {
                coroutineContext.ensureActive()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
                GLES20.glFinish()
                frames++
            }
            check(GLES20.glGetError() == GLES20.GL_NO_ERROR)
            return frames / ((System.nanoTime() - started) / 1e9)
        } finally {
            if (program != 0) GLES20.glDeleteProgram(program)
            if (vertex != 0) GLES20.glDeleteShader(vertex)
            if (fragment != 0) GLES20.glDeleteShader(fragment)
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
}
