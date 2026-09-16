package com.gavinb8.askclaude

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.gavinb8.askclaude.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), ClaudeBridgeService.BridgeListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var config: AppConfig
    private var service: ClaudeBridgeService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as ClaudeBridgeService.LocalBinder).getService()
            service?.addListener(this@MainActivity)
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        config = AppConfig(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        binding.sendButton.setOnClickListener {
            val text = binding.promptInput.text.toString().trim()
            if (text.isNotEmpty()) {
                appendLog("You: $text")
                service?.askFromPhone(text, binding.followUpCheckbox.isChecked)
                binding.promptInput.setText("")
            }
        }

        binding.settingsButton.setOnClickListener { showSettingsDialog() }

        if (!config.isConfigured()) {
            appendLog("No API key configured yet -- tap Settings to add one, or edit android/secrets.properties and rebuild.")
        }

        startForegroundServiceCompat()
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, ClaudeBridgeService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            service?.removeListener(this)
            unbindService(connection)
            bound = false
        }
    }

    private fun startForegroundServiceCompat() {
        val intent = Intent(this, ClaudeBridgeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun showSettingsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val apiKeyField = EditText(this).apply { hint = "Anthropic API key"; setText(config.apiKey) }
        val modelField = EditText(this).apply { hint = "Model (e.g. claude-sonnet-5)"; setText(config.model) }
        val bridgeField = EditText(this).apply { hint = "Bridge server URL (optional)"; setText(config.bridgeServerUrl) }
        layout.addView(apiKeyField)
        layout.addView(modelField)
        layout.addView(bridgeField)

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                config.apiKey = apiKeyField.text.toString().trim()
                config.model = modelField.text.toString().trim()
                config.bridgeServerUrl = bridgeField.text.toString().trim()
                appendLog("Settings saved.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun appendLog(line: String) {
        runOnUiThread {
            binding.logText.append("\n$line")
            binding.logScroll.post { binding.logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onLog(line: String) {
        appendLog(line)
    }

    override fun onDeviceStatus(connected: Boolean, deviceName: String?) {
        runOnUiThread {
            binding.statusText.text = if (connected) {
                "Connected: ${deviceName ?: "watch"}"
            } else {
                "Watch not connected"
            }
        }
    }
}
