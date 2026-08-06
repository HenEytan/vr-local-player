package com.example.vrlocalplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.TextView

/**
 * Landing screen: lets the user pick a local video file (from device storage)
 * and choose whether it should be duplicated into a side by side stereo pair
 * for VR / Google Cardboard style headsets, or played flat/normal.
 */
class MainActivity : AppCompatActivity() {

    private var selectedUri: Uri? = null

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                selectedUri = uri
                // Persist read permission across the launched player activity / process restarts.
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Some providers do not support persistable permissions; safe to ignore.
                }
                findViewById<TextView>(R.id.tvSelectedFile).text = uri.lastPathSegment ?: uri.toString()
                findViewById<MaterialButton>(R.id.btnPlay).isEnabled = true
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.btnPickVideo).setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        findViewById<MaterialButton>(R.id.btnPlay).setOnClickListener {
            val uri = selectedUri ?: return@setOnClickListener
            val duplicateSbs = findViewById<SwitchMaterial>(R.id.switchDuplicateSbs).isChecked

            val intent = Intent(this, VrPlayerActivity::class.java).apply {
                putExtra(VrPlayerActivity.EXTRA_VIDEO_URI, uri.toString())
                putExtra(VrPlayerActivity.EXTRA_DUPLICATE_SBS, duplicateSbs)
            }
            startActivity(intent)
        }
    }
}
