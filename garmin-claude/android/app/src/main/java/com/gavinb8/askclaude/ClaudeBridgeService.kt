package com.gavinb8.askclaude

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import java.util.concurrent.Executors

/**
 * The actual "bridge" in Venu 3 -> Garmin Connect Mobile -> this app ->
 * Claude API -> back. Runs as a foreground service so it survives while the
 * screen is off and the watch app is what's actually in use.
 *
 * NOTE ON THE SDK SURFACE: the Connect IQ Mobile/Companion SDK for Android
 * (com.garmin.connectiq:ciq-companion-app-sdk) has kept the
 * com.garmin.android.connectiq package and API shape stable for years. This
 * was written from that well-established API (ConnectIQ / IQDevice / IQApp,
 * registerForAppEvents / sendMessage). developer.garmin.com was not
 * reachable while researching this project (network policy blocked it), so
 * if the artifact you pull down has renamed anything, the fix is almost
 * always a 1:1 rename here -- diff against Garmin's "Comm" sample app.
 */
class ClaudeBridgeService : Service() {

    interface BridgeListener {
        fun onLog(line: String)
        fun onDeviceStatus(connected: Boolean, deviceName: String?)
    }

    inner class LocalBinder : Binder() {
        fun getService(): ClaudeBridgeService = this@ClaudeBridgeService
    }

    private val binder = LocalBinder()
    private val listeners = mutableListOf<BridgeListener>()
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var connectIQ: ConnectIQ
    private lateinit var config: AppConfig
    private val garminApp by lazy { IQApp(config.garminAppId) }
    private val conversations = ConversationStore()
    private val apiClient = ClaudeApiClient()

    private var knownDevice: IQDevice? = null

    // Outgoing AppMessage queue: the phone must wait for each send to be
    // acknowledged before firing the next one, or the SDK will drop/queue
    // unpredictably under BLE congestion.
    private val outbox = ArrayDeque<Map<String, Any>>()
    private var sending = false

    override fun onCreate() {
        super.onCreate()
        config = AppConfig(this)
        startForeground(NOTIFICATION_ID, buildNotification("Starting..."))
        initConnectIQ()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        runCatching { connectIQ.unregisterAllForEvents() }
        runCatching { connectIQ.shutdown(this) }
        super.onDestroy()
    }

    fun addListener(listener: BridgeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: BridgeListener) {
        listeners.remove(listener)
    }

    /** Entry point for the phone-typed question flow in MainActivity. */
    fun askFromPhone(prompt: String, isFollowUp: Boolean) {
        val requestId = (System.currentTimeMillis() % 1_000_000).toInt()
        val conversationId = if (isFollowUp) currentConversationId ?: requestId.toString()
        else requestId.toString().also { currentConversationId = it }
        currentConversationId = conversationId
        handlePrompt(requestId, conversationId, prompt)
    }

    private var currentConversationId: String? = null

    private fun initConnectIQ() {
        connectIQ = ConnectIQ.getInstance(applicationContext, ConnectIQ.IQConnectType.WIRELESS)
        connectIQ.initialize(applicationContext, true, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                log("Connect IQ SDK ready.")
                findDeviceAndListen()
            }

            override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                log("Connect IQ init failed: $status. Is Garmin Connect installed?")
            }

            override fun onSdkShutDown() {
                log("Connect IQ SDK shut down.")
            }
        })
    }

    private fun findDeviceAndListen() {
        val connected = runCatching { connectIQ.connectedDevices }.getOrNull().orEmpty()
        val devices = connected.ifEmpty { runCatching { connectIQ.knownDevices }.getOrNull().orEmpty() }

        if (devices.isEmpty()) {
            log("No paired Garmin device found. Pair your Venu 3 in Garmin Connect first.")
            notifyDeviceStatus(false, null)
            return
        }

        // Personal single-device setup: just use the first one.
        val device = devices[0]
        knownDevice = device
        notifyDeviceStatus(true, device.friendlyName)
        log("Found watch: ${device.friendlyName}")

        connectIQ.registerForDeviceEvents(device) { dev, status ->
            val connected = status == IQDevice.IQDeviceStatus.CONNECTED
            notifyDeviceStatus(connected, dev.friendlyName)
            log("Watch status: $status")
        }

        connectIQ.registerForAppEvents(device, garminApp) { dev, _, message, status ->
            if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
                log("Bad message from watch: $status")
                return@registerForAppEvents
            }
            val payload = message.firstOrNull() as? Map<*, *> ?: return@registerForAppEvents
            onWatchMessage(dev, payload)
        }
        log("Listening for questions from the watch.")
    }

    @Suppress("UNCHECKED_CAST")
    private fun onWatchMessage(device: IQDevice, payload: Map<*, *>) {
        val type = payload[Protocol.KEY_TYPE] as? String ?: return
        val id = (payload[Protocol.KEY_ID] as? Number)?.toInt() ?: return

        when (type) {
            Protocol.TYPE_PROMPT -> {
                val text = payload[Protocol.KEY_TEXT] as? String ?: return
                val conv = payload[Protocol.KEY_CONV] as? String ?: id.toString()
                currentConversationId = conv
                log("Watch asked: \"$text\"")
                handlePrompt(id, conv, text)
            }
            Protocol.TYPE_CANCEL -> {
                log("Watch cancelled request $id.")
            }
        }
    }

    private fun handlePrompt(requestId: Int, conversationId: String, prompt: String) {
        executor.execute {
            try {
                val history = conversations.historyFor(conversationId)
                val wantsImage = ImagePipeline.looksLikeImageRequest(prompt)
                val effectivePrompt = if (wantsImage) prompt + ImagePipeline.diagramInstructionSuffix() else prompt

                val response = apiClient.ask(history, effectivePrompt, config)
                conversations.append(conversationId, "user", prompt)
                conversations.append(conversationId, "assistant", response)

                log("Claude replied (${response.length} chars).")

                val rendered = if (wantsImage) ImagePipeline.tryParseAndRender(response) else null
                if (rendered != null) {
                    enqueueAll(ImagePipeline.protocolMessages(requestId, rendered))
                    enqueueAll(ChunkCodec.chunksFor(requestId, rendered.caption))
                } else {
                    enqueueAll(ChunkCodec.chunksFor(requestId, response))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to answer prompt", t)
                log("Error: ${t.message}")
                enqueueAll(listOf(ChunkCodec.errorMessage(requestId, t.message ?: "Unknown error")))
            }
        }
    }

    private fun enqueueAll(messages: List<Map<String, Any>>) {
        synchronized(outbox) {
            outbox.addAll(messages)
        }
        pumpOutbox()
    }

    private fun pumpOutbox() {
        val device = knownDevice ?: return
        synchronized(outbox) {
            if (sending || outbox.isEmpty()) {
                return
            }
            sending = true
            val next = outbox.removeFirst()
            connectIQ.sendMessage(device, garminApp, next) { _, _, status ->
                sending = false
                if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
                    log("Send to watch failed: $status")
                }
                pumpOutbox()
            }
        }
    }

    private fun log(line: String) {
        Log.d(TAG, line)
        listeners.forEach { it.onLog(line) }
        updateNotification(line)
    }

    private fun notifyDeviceStatus(connected: Boolean, name: String?) {
        listeners.forEach { it.onDeviceStatus(connected, name) }
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "AskClaude Bridge", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AskClaude Companion")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val TAG = "ClaudeBridgeService"
        private const val CHANNEL_ID = "ask_claude_bridge"
        private const val NOTIFICATION_ID = 42
    }
}
