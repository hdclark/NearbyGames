package com.nearbygames.data

import java.util.UUID

/**
 * A single announcement message. Immutable once created.
 *
 * [id] is a stable UUID so that duplicates can be detected when syncing across devices.
 */
data class Announcement(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
