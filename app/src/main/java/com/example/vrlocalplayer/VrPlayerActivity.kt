package com.example.vrlocalplayer

import android.net.Uri
import android.os.Bundle
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * Full-screen VR playback activity.
 *
 * Local video files are decoded normally by ExoPlayer, which is pointed at a
 * Surface backed by an OpenGL external texture (see StereoRenderer). The GL
 * renderer then either duplicates that single decoded frame into a left/right
 * pair for a Cardboard-style side-by-side VR view, or draws it once, flat.
 */
class VrPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_DUPLICATE_SBS = "extra_duplicate_sbs"
    }

    private var player: ExoPlayer? = null
    private lateinit var glView: StereoGLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen on and hide system bars for an immersive VR view.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        setContentView(R.layout.activity_vr_player)
        glView = findViewById(R.id.glSurfaceView)

        val videoUriString = intent.getStringExtra(EXTRA_VIDEO_URI)
        val duplicateSbs = intent.getBooleanExtra(EXTRA_DUPLICATE_SBS, true)

        if (videoUriString == null) {
            finish()
            return
        }
        val videoUri = Uri.parse(videoUriString)

        glView.init(duplicateSbs) { surface ->
            startPlayback(videoUri, surface)
        }
    }

    private fun startPlayback(uri: Uri, surface: Surface) {
        val exoPlayer = ExoPlayer.Builder(this).build().also { player = it }
        exoPlayer.setVideoSurface(surface)
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ONE
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        if (::glView.isInitialized && glView::stereoRenderer.isInitialized) {
            glView.stereoRenderer.release()
        }
    }
}
