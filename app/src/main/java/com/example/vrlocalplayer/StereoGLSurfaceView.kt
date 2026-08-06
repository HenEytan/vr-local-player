package com.example.vrlocalplayer

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

/**
 * GLSurfaceView configured for GLES 2.0 rendering of the video frame,
 * either duplicated left/right (stereo SBS for VR headsets) or as a
 * single flat view.
 */
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
        // Continuous mode keeps drawing new video frames as they arrive from the decoder.
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
