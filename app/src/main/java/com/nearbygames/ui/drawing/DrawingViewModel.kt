package com.nearbygames.ui.drawing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nearbygames.data.DrawingStroke
import com.nearbygames.data.NearbyMessage
import com.nearbygames.data.NearbyMessageType
import com.nearbygames.nearby.NearbyConnectionsManager
import com.nearbygames.utils.DeviceIdManager

class DrawingViewModel(application: Application) : AndroidViewModel(application) {

    private val _strokes = MutableLiveData<List<DrawingStroke>>(emptyList())
    val strokes: LiveData<List<DrawingStroke>> = _strokes

    private val _connectedCount = MutableLiveData(0)
    val connectedCount: LiveData<Int> = _connectedCount

    private val gson = Gson()

    private val connectionListener = object : NearbyConnectionsManager.ConnectionStateListener {
        override fun onConnected(endpointId: String, endpointName: String) {
            _connectedCount.postValue(NearbyConnectionsManager.getConnectedEndpoints().size)
            // Send all current strokes to the newly connected device
            val ctx = getApplication<Application>()
            val current = _strokes.value ?: emptyList()
            val message = NearbyMessage(
                type = NearbyMessageType.DRAWING_SYNC,
                senderId = DeviceIdManager.getDeviceId(ctx),
                timestamp = System.currentTimeMillis(),
                payload = gson.toJson(current)
            )
            NearbyConnectionsManager.sendMessage(endpointId, message)
        }

        override fun onDisconnected(endpointId: String) {
            _connectedCount.postValue(NearbyConnectionsManager.getConnectedEndpoints().size)
        }
    }

    private val messageListener = object : NearbyConnectionsManager.MessageListener {
        override fun onMessage(fromEndpointId: String, message: NearbyMessage) {
            when (message.type) {
                NearbyMessageType.DRAWING_STROKE -> {
                    val stroke = gson.fromJson(message.payload, DrawingStroke::class.java)
                    mergeStrokes(listOf(stroke))
                }
                NearbyMessageType.DRAWING_CLEAR -> {
                    // Conflict resolution: clear timestamp wins — remove strokes at or before it.
                    // Log a warning if the payload is malformed; fall back to the message timestamp.
                    val clearTs = message.payload.toLongOrNull() ?: run {
                        android.util.Log.w("DrawingViewModel", "DRAWING_CLEAR payload is not a long: '${message.payload}'; using message timestamp")
                        message.timestamp
                    }
                    _strokes.postValue(
                        (_strokes.value ?: emptyList()).filter { it.timestamp > clearTs }
                    )
                }
                NearbyMessageType.DRAWING_SYNC -> {
                    val type = object : TypeToken<List<DrawingStroke>>() {}.type
                    val list: List<DrawingStroke> = gson.fromJson(message.payload, type)
                    mergeStrokes(list)
                }
            }
        }
    }

    // ---- Lifecycle --------------------------------------------------------------------------

    init {
        _connectedCount.value = NearbyConnectionsManager.getConnectedEndpoints().size
        NearbyConnectionsManager.addConnectionListener(connectionListener)
        NearbyConnectionsManager.addMessageListener(messageListener)
    }

    override fun onCleared() {
        NearbyConnectionsManager.removeConnectionListener(connectionListener)
        NearbyConnectionsManager.removeMessageListener(messageListener)
    }

    // ---- Public API -------------------------------------------------------------------------

    fun addStroke(stroke: DrawingStroke) {
        val withSender = stroke.copy(senderId = DeviceIdManager.getDeviceId(getApplication()))
        val current = _strokes.value?.toMutableList() ?: mutableListOf()
        current.add(withSender)
        _strokes.value = current

        val message = NearbyMessage(
            type = NearbyMessageType.DRAWING_STROKE,
            senderId = DeviceIdManager.getDeviceId(getApplication()),
            timestamp = withSender.timestamp,
            payload = gson.toJson(withSender)
        )
        NearbyConnectionsManager.broadcastMessage(message)
    }

    fun clearCanvas() {
        val clearTimestamp = System.currentTimeMillis()
        _strokes.value = emptyList()

        val message = NearbyMessage(
            type = NearbyMessageType.DRAWING_CLEAR,
            senderId = DeviceIdManager.getDeviceId(getApplication()),
            timestamp = clearTimestamp,
            payload = clearTimestamp.toString()
        )
        NearbyConnectionsManager.broadcastMessage(message)
    }

    // ---- Private helpers --------------------------------------------------------------------

    private fun mergeStrokes(incoming: List<DrawingStroke>) {
        val current = _strokes.value?.toMutableList() ?: mutableListOf()
        val existingIds = current.mapTo(HashSet()) { it.id }
        for (stroke in incoming) {
            if (existingIds.add(stroke.id)) current.add(stroke)
        }
        _strokes.postValue(current.sortedBy { it.timestamp })
    }

}
