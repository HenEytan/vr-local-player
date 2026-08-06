package com.example.vrlocalplayer

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class StereoGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    lateinit var stereoRenderer: StereoRenderer
        private set

    fun init(duplicateSbs: Boolean, onSurfaceReady: (android.view.Surface) -> Unit) {
        setEGLContextClientVersion(2)
        stereoRenderer = StereoRenderer(duplicateSbs, onSurfaceReady)
        setRenderer(stereoRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun releaseRendererIfReady() {
        if (::stereoRenderer.isInitialized) {
            stereoRenderer.release()
        }
    }
}
