package com.nearbygames.data

import java.util.UUID

/** A single (x, y) coordinate in canvas-space. */
data class DrawingPoint(val x: Float, val y: Float)

/**
 * One complete stroke drawn on the shared canvas.
 *
 * [id] is a stable UUID used for deduplication during sync.
 */
data class DrawingStroke(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val points: List<DrawingPoint>,
    val color: Int,
    val brushSize: Float,
    val timestamp: Long = System.currentTimeMillis()
)
