package com.nearbygames.ui.announcements

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nearbygames.data.Announcement
import com.nearbygames.data.NearbyMessage
import com.nearbygames.data.NearbyMessageType
import com.nearbygames.nearby.NearbyConnectionsManager
import com.nearbygames.utils.DeviceIdManager

class AnnouncementsViewModel(application: Application) : AndroidViewModel(application) {

    private val _announcements = MutableLiveData<List<Announcement>>(emptyList())
    val announcements: LiveData<List<Announcement>> = _announcements

    private val _connectedCount = MutableLiveData(0)
    val connectedCount: LiveData<Int> = _connectedCount

    private val gson = Gson()

    private val connectionListener = object : NearbyConnectionsManager.ConnectionStateListener {
        override fun onConnected(endpointId: String, endpointName: String) {
            _connectedCount.postValue(NearbyConnectionsManager.getConnectedEndpoints().size)
            sendSyncTo(endpointId)
        }

        override fun onDisconnected(endpointId: String) {
            _connectedCount.postValue(NearbyConnectionsManager.getConnectedEndpoints().size)
        }
    }

    private val messageListener = object : NearbyConnectionsManager.MessageListener {
        override fun onMessage(fromEndpointId: String, message: NearbyMessage) {
            when (message.type) {
                NearbyMessageType.ANNOUNCEMENT -> {
                    val a = gson.fromJson(message.payload, Announcement::class.java)
                    mergeAndSave(listOf(a))
                }
                NearbyMessageType.ANNOUNCEMENT_SYNC -> {
                    val type = object : TypeToken<List<Announcement>>() {}.type
                    val list: List<Announcement> = gson.fromJson(message.payload, type)
                    mergeAndSave(list)
                }
            }
        }
    }

    // ---- Lifecycle --------------------------------------------------------------------------

    init {
        loadAnnouncements()
        _connectedCount.value = NearbyConnectionsManager.getConnectedEndpoints().size
        NearbyConnectionsManager.addConnectionListener(connectionListener)
        NearbyConnectionsManager.addMessageListener(messageListener)
    }

    override fun onCleared() {
        NearbyConnectionsManager.removeConnectionListener(connectionListener)
        NearbyConnectionsManager.removeMessageListener(messageListener)
    }

    // ---- Public API -------------------------------------------------------------------------

    fun sendAnnouncement(text: String) {
        val ctx = getApplication<Application>()
        val announcement = Announcement(
            senderId = DeviceIdManager.getDeviceId(ctx),
            senderName = DeviceIdManager.getDeviceName(ctx),
            text = text.trim(),
            timestamp = System.currentTimeMillis()
        )
        mergeAndSave(listOf(announcement))

        val message = NearbyMessage(
            type = NearbyMessageType.ANNOUNCEMENT,
            senderId = DeviceIdManager.getDeviceId(ctx),
            timestamp = announcement.timestamp,
            payload = gson.toJson(announcement)
        )
        NearbyConnectionsManager.broadcastMessage(message)
    }

    // ---- Private helpers --------------------------------------------------------------------

    /** Merge [incoming] with the current list, deduplicate by ID, keep latest 10. */
    private fun mergeAndSave(incoming: List<Announcement>) {
        val current = _announcements.value?.toMutableList() ?: mutableListOf()
        val existingIds = current.mapTo(HashSet()) { it.id }
        for (a in incoming) {
            if (existingIds.add(a.id)) current.add(a)
        }
        val merged = current.sortedByDescending { it.timestamp }.take(MAX_MESSAGES)
        _announcements.postValue(merged)
        saveAnnouncements(merged)
    }

    private fun saveAnnouncements(list: List<Announcement>) {
        getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST, gson.toJson(list))
            .apply()
    }

    private fun loadAnnouncements() {
        val json = getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIST, null) ?: return
        val type = object : TypeToken<List<Announcement>>() {}.type
        _announcements.value = gson.fromJson(json, type)
    }

    private fun sendSyncTo(endpointId: String) {
        val ctx = getApplication<Application>()
        val current = _announcements.value ?: emptyList()
        val message = NearbyMessage(
            type = NearbyMessageType.ANNOUNCEMENT_SYNC,
            senderId = DeviceIdManager.getDeviceId(ctx),
            timestamp = System.currentTimeMillis(),
            payload = gson.toJson(current)
        )
        NearbyConnectionsManager.sendMessage(endpointId, message)
    }

    // ---- Nearby listeners -------------------------------------------------------------------

    companion object {
        private const val MAX_MESSAGES = 10
        private const val PREFS_NAME = "announcements_prefs"
        private const val KEY_LIST = "announcements_json"
    }
}
