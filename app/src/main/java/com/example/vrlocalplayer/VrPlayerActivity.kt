package com.example.vrlocalplayer

import android.net.Uri
import android.os.Bundle
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.roundToInt

class VrPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_DUPLICATE_SBS = "extra_duplicate_sbs"
    }

    private var player: ExoPlayer? = null
    private lateinit var glView: StereoGLSurfaceView
    private lateinit var currentValues: VrSettings.Values

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        setContentView(R.layout.activity_vr_player)
        glView = findViewById(R.id.glSurfaceView)

        val videoUriString = intent.getStringExtra(EXTRA_VIDEO_URI)
        val requestedDuplicate = intent.getBooleanExtra(EXTRA_DUPLICATE_SBS, true)

        if (videoUriString == null) {
            finish()
            return
        }
        val videoUri = Uri.parse(videoUriString)

        currentValues = VrSettings.load(this, requestedDuplicate)

        glView.init(currentValues.duplicateSbs, currentValues) { surface ->
            startPlayback(videoUri, surface)
        }

        setupControlPanel()
    }

    private fun startPlayback(uri: Uri, surface: Surface) {
        val exoPlayer = ExoPlayer.Builder(this).build().also { player = it }
        exoPlayer.setVideoSurface(surface)
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ONE
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    private fun setupControlPanel() {
        val panelScroll = findViewById<View>(R.id.panelScroll)
        val btnToggle = findViewById<ImageButton>(R.id.btnToggleControls)
        val switchVrMode = findViewById<SwitchMaterial>(R.id.switchVrMode)

        val seekZoom = findViewById<SeekBar>(R.id.seekZoom)
        val seekPanX = findViewById<SeekBar>(R.id.seekPanX)
        val seekPanY = findViewById<SeekBar>(R.id.seekPanY)
        val seekGap = findViewById<SeekBar>(R.id.seekGap)
        val seekIpd = findViewById<SeekBar>(R.id.seekIpd)

        val tvZoom = findViewById<TextView>(R.id.tvZoomValue)
        val tvPanX = findViewById<TextView>(R.id.tvPanXValue)
        val tvPanY = findViewById<TextView>(R.id.tvPanYValue)
        val tvGap = findViewById<TextView>(R.id.tvGapValue)
        val tvIpd = findViewById<TextView>(R.id.tvIpdValue)

        btnToggle.setOnClickListener {
            panelScroll.visibility = if (panelScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<MaterialButton>(R.id.btnDoneControls).setOnClickListener {
            panelScroll.visibility = View.GONE
        }

        switchVrMode.isChecked = currentValues.duplicateSbs
        seekZoom.progress = fractionToProgress(currentValues.scale, VrSettings.SCALE_MIN, VrSettings.SCALE_MAX, seekZoom.max)
        seekPanX.progress = fractionToProgress(currentValues.panX, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanX.max)
        seekPanY.progress = fractionToProgress(currentValues.panY, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanY.max)
        seekGap.progress = fractionToProgress(currentValues.gap, VrSettings.GAP_MIN, VrSettings.GAP_MAX, seekGap.max)
        seekIpd.progress = fractionToProgress(currentValues.ipdShift, VrSettings.IPD_SHIFT_MIN, VrSettings.IPD_SHIFT_MAX, seekIpd.max)

        updateZoomLabel(tvZoom, currentValues.scale)
        updatePercentLabel(tvPanX, currentValues.panX)
        updatePercentLabel(tvPanY, currentValues.panY)
        updatePercentLabel(tvGap, currentValues.gap)
        updatePercentLabel(tvIpd, currentValues.ipdShift)

        switchVrMode.setOnCheckedChangeListener { _, isChecked ->
            currentValues = currentValues.copy(duplicateSbs = isChecked)
            glView.stereoRenderer.duplicateSbs = isChecked
            persist()
        }

        seekZoom.setOnSeekBarChangeListener(onChange { progress ->
            val value = progressToFraction(progress, VrSettings.SCALE_MIN, VrSettings.SCALE_MAX, seekZoom.max)
            currentValues = currentValues.copy(scale = value)
            glView.stereoRenderer.scale = value
            updateZoomLabel(tvZoom, value)
            persist()
        })

        seekPanX.setOnSeekBarChangeListener(onChange { progress ->
            val value = progressToFraction(progress, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanX.max)
            currentValues = currentValues.copy(panX = value)
            glView.stereoRenderer.panX = value
            updatePercentLabel(tvPanX, value)
            persist()
        })

        seekPanY.setOnSeekBarChangeListener(onChange { progress ->
            val value = progressToFraction(progress, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanY.max)
            currentValues = currentValues.copy(panY = value)
            glView.stereoRenderer.panY = value
            updatePercentLabel(tvPanY, value)
            persist()
        })

        seekGap.setOnSeekBarChangeListener(onChange { progress ->
            val value = progressToFraction(progress, VrSettings.GAP_MIN, VrSettings.GAP_MAX, seekGap.max)
            currentValues = currentValues.copy(gap = value)
            glView.stereoRenderer.gap = value
            updatePercentLabel(tvGap, value)
            persist()
        })

        seekIpd.setOnSeekBarChangeListener(onChange { progress ->
            val value = progressToFraction(progress, VrSettings.IPD_SHIFT_MIN, VrSettings.IPD_SHIFT_MAX, seekIpd.max)
            currentValues = currentValues.copy(ipdShift = value)
            glView.stereoRenderer.ipdShift = value
            updatePercentLabel(tvIpd, value)
            persist()
        })

        findViewById<MaterialButton>(R.id.btnResetControls).setOnClickListener {
            currentValues = VrSettings.Values(duplicateSbs = currentValues.duplicateSbs)
            glView.stereoRenderer.scale = currentValues.scale
            glView.stereoRenderer.panX = currentValues.panX
            glView.stereoRenderer.panY = currentValues.panY
            glView.stereoRenderer.gap = currentValues.gap
            glView.stereoRenderer.ipdShift = currentValues.ipdShift
            persist()

            seekZoom.progress = fractionToProgress(currentValues.scale, VrSettings.SCALE_MIN, VrSettings.SCALE_MAX, seekZoom.max)
            seekPanX.progress = fractionToProgress(currentValues.panX, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanX.max)
            seekPanY.progress = fractionToProgress(currentValues.panY, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanY.max)
            seekGap.progress = fractionToProgress(currentValues.gap, VrSettings.GAP_MIN, VrSettings.GAP_MAX, seekGap.max)
            seekIpd.progress = fractionToProgress(currentValues.ipdShift, VrSettings.IPD_SHIFT_MIN, VrSettings.IPD_SHIFT_MAX, seekIpd.max)
            updateZoomLabel(tvZoom, currentValues.scale)
            updatePercentLabel(tvPanX, currentValues.panX)
            updatePercentLabel(tvPanY, currentValues.panY)
            updatePercentLabel(tvGap, currentValues.gap)
            updatePercentLabel(tvIpd, currentValues.ipdShift)
        }
    }

    private fun persist() {
        VrSettings.save(this, currentValues)
    }

    private fun onChange(action: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) action(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun fractionToProgress(value: Float, min: Float, max: Float, maxProgress: Int): Int {
        val t = ((value - min) / (max - min)).coerceIn(0f, 1f)
        return (t * maxProgress).roundToInt()
    }

    private fun progressToFraction(progress: Int, min: Float, max: Float, maxProgress: Int): Float {
        val t = progress.toFloat() / maxProgress.toFloat()
        return min + t * (max - min)
    }

    private fun updateZoomLabel(tv: TextView, scale: Float) {
        tv.text = "${(scale * 100).roundToInt()}%"
    }

    private fun updatePercentLabel(tv: TextView, fraction: Float) {
        val pct = (fraction * 100).roundToInt()
        tv.text = if (pct > 0) "+$pct%" else "$pct%"
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
        if (::glView.isInitialized) {
            glView.releaseRendererIfReady()
        }
    }
}
