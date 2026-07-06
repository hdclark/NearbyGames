package com.nearbygames.utils

import android.content.Context
import java.util.UUID

/**
 * Generates and persists a stable UUID and friendly display name for this device installation.
 * The same values are returned on every call after the first.
 */
object DeviceIdManager {

    private const val PREFS_NAME = "nearby_games_device"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            id
        }
    }

    fun getDeviceName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_NAME, null) ?: run {
            // Friendly name: "Player" + last 4 chars of UUID
            val suffix = getDeviceId(context).takeLast(4).uppercase()
            val name = "Player_$suffix"
            prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
            name
        }
    }
}
