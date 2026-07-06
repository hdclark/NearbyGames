package com.nearbygames.ui.drawing

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.nearbygames.data.DrawingPoint
import com.nearbygames.data.DrawingStroke
import java.util.UUID

/**
 * A custom [View] that renders a list of [DrawingStroke]s and collects new strokes from touch.
 *
 * New completed strokes are delivered via [onStrokeComplete].  The fragment is responsible
 * for adding the stroke (with the correct senderId) to the [DrawingViewModel] and calling
 * [updateStrokes] when the list changes.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ---- Brush settings (settable from the fragment) ----------------------------------------

    var currentColor: Int = Color.BLACK
    var currentBrushSize: Float = 12f

    // ---- Callbacks --------------------------------------------------------------------------

    var onStrokeComplete: ((DrawingStroke) -> Unit)? = null

    // ---- Internal state ---------------------------------------------------------------------

    private var strokes: List<DrawingStroke> = emptyList()

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val currentPath = Path()
    private val currentPoints = mutableListOf<DrawingPoint>()

    // ---- Public API -------------------------------------------------------------------------

    /** Replace the displayed stroke list and redraw. */
    fun updateStrokes(newStrokes: List<DrawingStroke>) {
        strokes = newStrokes
        invalidate()
    }

    // ---- Drawing ----------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            paint.color = stroke.color
            paint.strokeWidth = stroke.brushSize

            val path = Path()
            path.moveTo(stroke.points[0].x, stroke.points[0].y)
            for (i in 1 until stroke.points.size) {
                path.lineTo(stroke.points[i].x, stroke.points[i].y)
            }
            canvas.drawPath(path, paint)
        }

        // Draw the in-progress stroke
        if (currentPoints.isNotEmpty()) {
            paint.color = currentColor
            paint.strokeWidth = currentBrushSize
            canvas.drawPath(currentPath, paint)
        }
    }

    // ---- Touch handling ---------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.reset()
                currentPath.moveTo(x, y)
                currentPoints.clear()
                currentPoints.add(DrawingPoint(x, y))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                currentPoints.add(DrawingPoint(x, y))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                currentPoints.add(DrawingPoint(x, y))

                if (currentPoints.size >= 2) {
                    val stroke = DrawingStroke(
                        id = UUID.randomUUID().toString(),
                        senderId = "", // filled in by the fragment
                        points = currentPoints.toList(),
                        color = currentColor,
                        brushSize = currentBrushSize,
                        timestamp = System.currentTimeMillis()
                    )
                    onStrokeComplete?.invoke(stroke)
                }

                currentPath.reset()
                currentPoints.clear()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
