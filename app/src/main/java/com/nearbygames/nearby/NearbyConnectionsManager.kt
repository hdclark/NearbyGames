package com.nearbygames.nearby

import android.content.Context
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
 * Call [start] when the app comes to the foreground and [stop] when it leaves.
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

    private val messageListeners = mutableListOf<MessageListener>()
    private val connectionListeners = mutableListOf<ConnectionStateListener>()

    // ---- Public API -------------------------------------------------------------------------

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Start advertising and discovering.  Idempotent — safe to call multiple times. */
    fun start() {
        startAdvertising()
        startDiscovery()
    }

    /** Stop advertising, discovering, and disconnect all endpoints. */
    fun stop() {
        val ctx = appContext ?: return
        try {
            Nearby.getConnectionsClient(ctx).stopAdvertising()
            Nearby.getConnectionsClient(ctx).stopDiscovery()
            Nearby.getConnectionsClient(ctx).stopAllEndpoints()
        } catch (e: Exception) {
            Log.w(TAG, "stop: ${e.message}")
        }
        connectedEndpoints.clear()
        pendingEndpoints.clear()
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

    /** Returns a snapshot of currently connected endpointId → name pairs. */
    fun getConnectedEndpoints(): Map<String, String> = connectedEndpoints.toMap()

    // ---- Private helpers --------------------------------------------------------------------

    private fun startAdvertising() {
        val ctx = appContext ?: return
        val localName = DeviceIdManager.getDeviceName(ctx)
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(ctx)
            .startAdvertising(localName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener { Log.d(TAG, "Advertising started as \"$localName\"") }
            .addOnFailureListener { e -> Log.e(TAG, "Advertising failed: ${e.message}") }
    }

    private fun startDiscovery() {
        val ctx = appContext ?: return
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(ctx)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener { Log.d(TAG, "Discovery started") }
            .addOnFailureListener { e -> Log.e(TAG, "Discovery failed: ${e.message}") }
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
