package dev.qokhelper

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var apiKey: EditText
    private lateinit var status: TextView

    private val captureConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            CaptureService.start(this, result.resultCode, data)
            moveTaskToBack(true)
        } else {
            status.text = "Screen capture was not allowed."
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        apiKey = findViewById(R.id.apiKey)
        status = findViewById(R.id.status)
        apiKey.setText(prefs.apiKey)

        findViewById<Spinner>(R.id.model).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, AnswerEngine.MODELS)
            setSelection(AnswerEngine.MODELS.indexOf(prefs.model).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    prefs.model = AnswerEngine.MODELS[pos]
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

        findViewById<Button>(R.id.overlayPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.start).setOnClickListener { start() }
        findViewById<Button>(R.id.stop).setOnClickListener { CaptureService.stop(this) }

        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (Settings.canDrawOverlays(this)) "Ready." else "Step 1: allow display over other apps."
    }

    override fun onPause() {
        super.onPause()
        prefs.apiKey = apiKey.text.toString()
    }

    private fun start() {
        prefs.apiKey = apiKey.text.toString()
        when {
            prefs.apiKey.isBlank() -> status.text = "Enter your API key first."
            !Settings.canDrawOverlays(this) -> status.text = "Allow display over other apps first."
            else -> {
                // The service reads the key and model when it starts, so restart it to pick up changes.
                CaptureService.stop(this)
                val mpm = getSystemService(MediaProjectionManager::class.java)
                captureConsent.launch(mpm.createScreenCaptureIntent())
            }
        }
    }
}
