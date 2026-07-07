package com.nearbygames.nearby

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import com.nearbygames.data.NearbyMessage
import com.nearbygames.utils.DeviceIdManager

/**
 * Singleton that owns all Nearby Connections logic.
 *
 * Call [init] once (e.g. from Application or Activity.onCreate).
 *
 * Advertising and discovery are explicit, time-bounded, user-triggered actions
 * (see [advertiseFor] and [discoverFor]) rather than always-on background behaviour.
 * Call [disconnectAll] to tear down every connection (also done automatically when
 * the app is closed).
 *
 * Uses the P2P_CLUSTER strategy so that every running instance automatically
 * connects to every other running instance on the same local network / Bluetooth.
 */
object NearbyConnectionsManager {

    private const val TAG = "NearbyConnMgr"
    private const val SERVICE_ID = "com.nearbygames"
    private val STRATEGY = Strategy.P2P_CLUSTER

    private val gson = Gson()
    private var appContext: Context? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var advertisingTimeoutRunnable: Runnable? = null
    private var discoveryTimeoutRunnable: Runnable? = null

    private var isAdvertising = false
    private var isDiscovering = false

    // endpointId -> display name (only confirmed, live connections)
    private val connectedEndpoints = mutableMapOf<String, String>()

    // endpointId -> display name (connection request in flight)
    private val pendingEndpoints = mutableMapOf<String, String>()

    // ---- Listener interfaces ----------------------------------------------------------------

    interface MessageListener {
        fun onMessage(fromEndpointId: String, message: NearbyMessage)
    }

    interface ConnectionStateListener {
        fun onConnected(endpointId: String, endpointName: String)
        fun onDisconnected(endpointId: String)
    }

    /** Reports start/stop of advertising or discovery, e.g. so the UI can update its buttons. */
    interface RadioStateListener {
        fun onAdvertisingStateChanged(isAdvertising: Boolean)
        fun onDiscoveryStateChanged(isDiscovering: Boolean)
    }

    private val messageListeners = mutableListOf<MessageListener>()
    private val connectionListeners = mutableListOf<ConnectionStateListener>()
    private val radioStateListeners = mutableListOf<RadioStateListener>()

    // ---- Public API -------------------------------------------------------------------------

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Start advertising this device for [durationMs] milliseconds so a nearby device can
     * discover and connect to it. Automatically stops after the duration elapses, or sooner
     * if [stopAdvertising] is called.
     */
    fun advertiseFor(durationMs: Long) {
        val ctx = appContext ?: return
        stopAdvertising()
        val localName = DeviceIdManager.getDeviceName(ctx)
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(ctx)
            .startAdvertising(localName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                Log.d(TAG, "Advertising started as \"$localName\"")
                isAdvertising = true
                notifyAdvertisingState(true)
            }
            .addOnFailureListener { e -> Log.e(TAG, "Advertising failed: ${e.message}") }

        val timeoutRunnable = Runnable { stopAdvertising() }
        advertisingTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, durationMs)
    }

    /** Stop advertising immediately. Safe to call even if not currently advertising. */
    fun stopAdvertising() {
        val ctx = appContext
        advertisingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        advertisingTimeoutRunnable = null
        if (!isAdvertising) return
        isAdvertising = false
        try {
            ctx?.let { Nearby.getConnectionsClient(it).stopAdvertising() }
        } catch (e: Exception) {
            Log.w(TAG, "stopAdvertising: ${e.message}")
        }
        notifyAdvertisingState(false)
    }

    /**
     * Discover nearby advertising devices for [durationMs] milliseconds, connecting to the
     * first one found. Automatically stops after the duration elapses, or sooner once a
     * connection succeeds or [stopDiscovery] is called.
     */
    fun discoverFor(durationMs: Long) {
        val ctx = appContext ?: return
        stopDiscovery()
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(ctx)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                Log.d(TAG, "Discovery started")
                isDiscovering = true
                notifyDiscoveryState(true)
            }
            .addOnFailureListener { e -> Log.e(TAG, "Discovery failed: ${e.message}") }

        val timeoutRunnable = Runnable { stopDiscovery() }
        discoveryTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, durationMs)
    }

    /** Stop discovery immediately. Safe to call even if not currently discovering. */
    fun stopDiscovery() {
        val ctx = appContext
        discoveryTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        discoveryTimeoutRunnable = null
        if (!isDiscovering) return
        isDiscovering = false
        try {
            ctx?.let { Nearby.getConnectionsClient(it).stopDiscovery() }
        } catch (e: Exception) {
            Log.w(TAG, "stopDiscovery: ${e.message}")
        }
        notifyDiscoveryState(false)
    }

    fun isAdvertising(): Boolean = isAdvertising
    fun isDiscovering(): Boolean = isDiscovering

    /** Stop advertising/discovery and disconnect every connected endpoint. Call on app close. */
    fun disconnectAll() {
        stopAdvertising()
        stopDiscovery()
        val ctx = appContext
        try {
            ctx?.let { Nearby.getConnectionsClient(it).stopAllEndpoints() }
        } catch (e: Exception) {
            Log.w(TAG, "disconnectAll: ${e.message}")
        }
        val disconnectedIds = connectedEndpoints.keys.toList()
        connectedEndpoints.clear()
        pendingEndpoints.clear()
        disconnectedIds.forEach { id ->
            connectionListeners.toList().forEach { it.onDisconnected(id) }
        }
    }

    fun sendMessage(endpointId: String, message: NearbyMessage) {
        val ctx = appContext ?: return
        if (endpointId !in connectedEndpoints) return
        val bytes = gson.toJson(message).toByteArray(Charsets.UTF_8)
        Nearby.getConnectionsClient(ctx)
            .sendPayload(endpointId, Payload.fromBytes(bytes))
            .addOnFailureListener { e -> Log.w(TAG, "sendPayload to $endpointId failed: ${e.message}") }
    }

    fun broadcastMessage(message: NearbyMessage) {
        connectedEndpoints.keys.toList().forEach { sendMessage(it, message) }
    }

    fun addMessageListener(listener: MessageListener) {
        if (listener !in messageListeners) messageListeners.add(listener)
    }

    fun removeMessageListener(listener: MessageListener) {
        messageListeners.remove(listener)
    }

    fun addConnectionListener(listener: ConnectionStateListener) {
        if (listener !in connectionListeners) connectionListeners.add(listener)
    }

    fun removeConnectionListener(listener: ConnectionStateListener) {
        connectionListeners.remove(listener)
    }

    fun addRadioStateListener(listener: RadioStateListener) {
        if (listener !in radioStateListeners) radioStateListeners.add(listener)
    }

    fun removeRadioStateListener(listener: RadioStateListener) {
        radioStateListeners.remove(listener)
    }

    /** Returns a snapshot of currently connected endpointId → name pairs. */
    fun getConnectedEndpoints(): Map<String, String> = connectedEndpoints.toMap()

    // ---- Private helpers --------------------------------------------------------------------

    private fun notifyAdvertisingState(advertising: Boolean) {
        radioStateListeners.toList().forEach { it.onAdvertisingStateChanged(advertising) }
    }

    private fun notifyDiscoveryState(discovering: Boolean) {
        radioStateListeners.toList().forEach { it.onDiscoveryStateChanged(discovering) }
    }

    private fun requestConnectionTo(endpointId: String, remoteEndpointName: String) {
        val ctx = appContext ?: return
        if (endpointId in connectedEndpoints || endpointId in pendingEndpoints) return
        pendingEndpoints[endpointId] = remoteEndpointName
        val localName = DeviceIdManager.getDeviceName(ctx)
        Nearby.getConnectionsClient(ctx)
            .requestConnection(localName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener { e ->
                Log.w(TAG, "requestConnection to $endpointId failed: ${e.message}")
                pendingEndpoints.remove(endpointId)
            }
    }

    // ---- Nearby Connections callbacks -------------------------------------------------------

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "onConnectionInitiated: $endpointId (${info.endpointName}), incoming=${info.isIncomingConnection}")
            // Store the remote name so we have it when onConnectionResult fires
            pendingEndpoints[endpointId] = info.endpointName
            val ctx = appContext ?: return
            // Auto-accept every incoming connection from the same SERVICE_ID
            Nearby.getConnectionsClient(ctx)
                .acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e -> Log.w(TAG, "acceptConnection failed: ${e.message}") }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            Log.d(TAG, "onConnectionResult: $endpointId status=${result.status.statusCode}")
            val name = pendingEndpoints.remove(endpointId) ?: endpointId
            if (result.status.isSuccess) {
                connectedEndpoints[endpointId] = name
                Log.d(TAG, "Connected to \"$name\" ($endpointId). Total: ${connectedEndpoints.size}")
                // A discovered device connected — no need to keep scanning.
                if (isDiscovering) stopDiscovery()
                connectionListeners.toList().forEach { it.onConnected(endpointId, name) }
            } else {
                Log.w(TAG, "Connection to $endpointId failed: ${result.status.statusMessage}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            val name = connectedEndpoints.remove(endpointId) ?: endpointId
            Log.d(TAG, "Disconnected from \"$name\" ($endpointId). Remaining: ${connectedEndpoints.size}")
            connectionListeners.toList().forEach { it.onDisconnected(endpointId) }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
            requestConnectionTo(endpointId, info.endpointName)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            pendingEndpoints.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            try {
                val json = String(bytes, Charsets.UTF_8)
                val message = gson.fromJson(json, NearbyMessage::class.java)
                messageListeners.toList().forEach { it.onMessage(endpointId, message) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse payload from $endpointId: ${e.message}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Not needed for BYTES payloads
        }
    }
}
