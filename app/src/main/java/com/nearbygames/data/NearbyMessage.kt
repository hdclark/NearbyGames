package com.nearbygames.data

/**
 * Envelope for every message sent over Nearby Connections.
 *
 * @param type     one of [NearbyMessageType]
 * @param senderId stable UUID of the originating device (see [com.nearbygames.utils.DeviceIdManager])
 * @param timestamp epoch milliseconds on the sending device; used for conflict resolution
 * @param payload  JSON-encoded game-specific data
 */
data class NearbyMessage(
    val type: String,
    val senderId: String,
    val timestamp: Long,
    val payload: String
)
