package dev.qokhelper

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var apiKey: EditText
    private lateinit var model: AutoCompleteTextView
    private lateinit var baseUrl: EditText
    private lateinit var status: TextView
    private lateinit var provider: Provider

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
        model = findViewById(R.id.model)
        baseUrl = findViewById(R.id.baseUrl)
        status = findViewById(R.id.status)
        model.setOnFocusChangeListener { _, hasFocus -> if (hasFocus && model.adapter != null) model.showDropDown() }

        provider = prefs.provider
        loadFields()
        findViewById<RadioGroup>(R.id.provider).apply {
            check(if (provider == Provider.CLAUDE) R.id.providerClaude else R.id.providerOpenAi)
            setOnCheckedChangeListener { _, id ->
                saveFields()
                provider = if (id == R.id.providerClaude) Provider.CLAUDE else Provider.OPENAI
                prefs.provider = provider
                loadFields()
            }
        }

        findViewById<CheckBox>(R.id.webSearch).apply {
            isChecked = prefs.webSearch
            setOnCheckedChangeListener { _, checked -> prefs.webSearch = checked }
        }

        findViewById<Button>(R.id.overlayPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.start).setOnClickListener { start() }
        findViewById<Button>(R.id.stop).setOnClickListener { CaptureService.stop(this) }

        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun loadFields() {
        val s = prefs.settings(provider)
        apiKey.setText(s.apiKey)
        apiKey.hint = if (provider == Provider.CLAUDE) "sk-ant-..." else "sk-... (blank for keyless local servers)"
        model.setText(s.model)
        model.setAdapter(
            if (provider == Provider.CLAUDE) {
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ClaudeAnswerer.SUGGESTED_MODELS)
            } else null
        )
        model.hint = if (provider == Provider.CLAUDE) "" else "any vision model, e.g. gpt-4o"
        baseUrl.setText(s.baseUrl)
        baseUrl.hint = provider.defaultBaseUrl
    }

    private fun saveFields() {
        prefs.save(provider, ProviderSettings(apiKey.text.toString(), model.text.toString(), baseUrl.text.toString()))
    }

    override fun onResume() {
        super.onResume()
        status.text = if (Settings.canDrawOverlays(this)) "Ready." else "Step 1: allow display over other apps."
    }

    override fun onPause() {
        super.onPause()
        saveFields()
    }

    private fun start() {
        saveFields()
        val s = prefs.settings(provider)
        // OpenAI-compatible local servers often need no key, but the official endpoints always do.
        val keyRequired = provider == Provider.CLAUDE || s.baseUrl.isBlank()
        when {
            keyRequired && s.apiKey.isBlank() -> status.text = "Enter your API key first."
            !Settings.canDrawOverlays(this) -> status.text = "Allow display over other apps first."
            else -> {
                // The service reads the settings when it starts, so restart it to pick up changes.
                CaptureService.stop(this)
                val mpm = getSystemService(MediaProjectionManager::class.java)
                captureConsent.launch(mpm.createScreenCaptureIntent())
            }
        }
    }
}
