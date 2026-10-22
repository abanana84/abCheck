package com.abanana.abattery.diagnostics

import android.opengl.EGL14
import android.opengl.GLES20
import com.abanana.abattery.data.device.*

object GraphicsInfo {
    val sections: List<DeviceSection> by lazy { read() }

    private fun read(): List<DeviceSection> {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        var surface = EGL14.EGL_NO_SURFACE
        var context = EGL14.EGL_NO_CONTEXT
        return try {
            check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1))
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
                    intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
                    0,
                )
            check(EGL14.eglMakeCurrent(display, surface, surface, context))
            listOf(
                DeviceSection(
                    "OpenGL ES",
                    listOf(
                        DeviceField("Vendor", GLES20.glGetString(GLES20.GL_VENDOR).orEmpty()),
                        DeviceField("Renderer", GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()),
                        DeviceField("Version", GLES20.glGetString(GLES20.GL_VERSION).orEmpty()),
                        DeviceField(
                            "Shading language",
                            GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION).orEmpty(),
                        ),
                    ),
                ),
                DeviceSection(
                    "Extensions",
                    GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty().split(' ').sorted().map {
                        DeviceField(it, "Supported")
                    },
                ),
            )
        } catch (e: Exception) {
            listOf(
                DeviceSection(
                    "Graphics",
                    listOf(DeviceField("Status", "Unavailable: ${e.message}")),
                )
            )
        } finally {
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
