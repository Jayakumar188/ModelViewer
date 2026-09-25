package com.infusory.modelviewer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.ImageButton
import com.infusory.modelviewer.R
import com.infusory.modelviewer.model.InteractionMode
import com.infusory.modelviewer.model.ModelInstance
import kotlin.math.max
import kotlin.math.min

interface ContainerCallbacks {
    fun onClose(view: ModelContainerView)
    fun onBringToFront(view: ModelContainerView)
}

/**
 * One draggable / pinch-resizable "window" on the stage (spec 1.4-1.7).
 *
 * This view draws no 3D content itself - it only:
 *  1. Owns/positions a [ModelInstance] (whose Filament View is rendered by
 *     [com.infusory.modelviewer.RenderLoop] into this container's on-screen
 *     rectangle, inside the single shared SwapChain).
 *  2. Hosts the always-visible button row (interaction / label / close).
 *  3. Interprets one-finger drag & two-finger pinch, routed to either
 *     "move/resize the container" (NORMAL mode) or "rotate/zoom the model"
 *     (INTERACTION mode) - never both (spec 1.7).
 *  4. Draws this model's part-label overlay in its own onDraw.
 */
class ModelContainerView(
    context: Context,
    val modelInstance: ModelInstance,
    val displayName: String
) : FrameLayout(context) {

    var callbacks: ContainerCallbacks? = null

    var mode: InteractionMode
        get() = modelInstance.mode
        set(value) { modelInstance.mode = value; refreshModeUi() }

    private val minSizePx = (100 * resources.displayMetrics.density).toInt()
    private var maxSizePx = Int.MAX_VALUE // set from host bounds by caller

    private val btnInteraction: ImageButton
    private val btnLabel: ImageButton
    private val btnClose: ImageButton

    // --- gesture state ---
    private var lastX = 0f
    private var lastY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var grabbing = false

    // Declared before the init block (and before scaleDetector) on purpose:
    // scaleDetector's constructor needs scaleListener already built, and
    // Kotlin initializes class members top-to-bottom, so scaleListener must
    // come first or scaleDetector would be constructed with an
    // uninitialized listener.
    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (mode == InteractionMode.NORMAL) {
                resizeBy(detector.scaleFactor, detector.focusX, detector.focusY)
            } else {
                // Interaction mode: pinch zooms the 3D content, the
                // container itself never resizes (spec 1.7).
                val delta = (1f - detector.scaleFactor) * ZOOM_SENSITIVITY
                modelInstance.manipulator?.scroll(detector.focusX.toInt(), detector.focusY.toInt(), delta)
            }
            return true
        }
    }
    private val scaleDetector = ScaleGestureDetector(context, scaleListener)

    // --- label paint ---
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13 * resources.displayMetrics.scaledDensity
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.label_bg)
    }
    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.label_connector)
        strokeWidth = 2 * resources.displayMetrics.density
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.label_connector)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        color = context.getColor(R.color.container_border)
    }

    init {
        setWillNotDraw(false)
        clipChildren = false

        val bar = LayoutInflater.from(context).inflate(R.layout.view_container_button_bar, this, false)
        addView(bar)
        btnInteraction = bar.findViewById(R.id.btnInteraction)
        btnLabel = bar.findViewById(R.id.btnLabel)
        btnClose = bar.findViewById(R.id.btnClose)

        btnInteraction.setOnClickListener {
            mode = if (mode == InteractionMode.NORMAL) InteractionMode.INTERACTION else InteractionMode.NORMAL
        }
        btnLabel.setOnClickListener {
            modelInstance.labelsVisible = !modelInstance.labelsVisible
            refreshModeUi()
            invalidate()
        }
        btnClose.setOnClickListener { callbacks?.onClose(this) }

        refreshModeUi()
    }

    fun setMaxSize(px: Int) { maxSizePx = px }

    private fun refreshModeUi() {
        val active = context.getColor(R.color.button_icon_active)
        val normal = context.getColor(R.color.button_icon)
        btnInteraction.imageTintList = android.content.res.ColorStateList.valueOf(
            if (mode == InteractionMode.INTERACTION) active else normal
        )
        btnLabel.imageTintList = android.content.res.ColorStateList.valueOf(
            if (modelInstance.labelsVisible) active else normal
        )
        borderPaint.color = context.getColor(
            if (mode == InteractionMode.INTERACTION) R.color.container_border_active else R.color.container_border
        )
    }

    // ---------------------------------------------------------------
    // Gestures. Buttons in the bar consume their own taps first (normal
    // Android child-view touch dispatch), so everything reaching
    // onTouchEvent here is on the open "canvas" part of the container.
    // ---------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                callbacks?.onBringToFront(this)
                activePointerId = event.getPointerId(0)
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx != -1) {
                        val x = event.getX(idx)
                        val y = event.getY(idx)
                        val dx = x - lastX
                        val dy = y - lastY
                        if (mode == InteractionMode.NORMAL) {
                            moveBy(dx, dy)
                        } else {
                            val manipulator = modelInstance.manipulator
                            if (manipulator != null) {
                                if (!grabbing) {
                                    manipulator.grabBegin(x.toInt(), y.toInt(), false)
                                    grabbing = true
                                }
                                manipulator.grabUpdate(x.toInt(), y.toInt())
                            }
                        }
                        lastX = x
                        lastY = y
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // A finger lifted (likely ending a pinch) - re-anchor drag
                // tracking to whichever pointer remains so the next MOVE
                // doesn't see a spurious large delta.
                val liftedIdx = event.actionIndex
                val remainingIdx = if (liftedIdx == 0) 1 else 0
                if (remainingIdx < event.pointerCount) {
                    lastX = event.getX(remainingIdx)
                    lastY = event.getY(remainingIdx)
                    activePointerId = event.getPointerId(remainingIdx)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (grabbing) {
                    modelInstance.manipulator?.grabEnd()
                    grabbing = false
                }
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }

    private fun moveBy(dx: Float, dy: Float) {
        val parentW = (parent as? FrameLayout)?.width ?: Int.MAX_VALUE
        val parentH = (parent as? FrameLayout)?.height ?: Int.MAX_VALUE
        val minVisible = width * 0.25f // don't allow it to be dragged fully off-screen
        x = (x + dx).coerceIn(-width + minVisible, parentW - minVisible)
        y = (y + dy).coerceIn(-minVisible, parentH - minVisible)
    }

    private fun resizeBy(scaleFactor: Float, focusX: Float, focusY: Float) {
        val lp = layoutParams as? FrameLayout.LayoutParams ?: return
        val oldW = max(lp.width, 1)
        val oldH = max(lp.height, 1)
        val newW = (oldW * scaleFactor).toInt().coerceIn(minSizePx, maxSizePx)
        val newH = (oldH * scaleFactor).toInt().coerceIn(minSizePx, maxSizePx)
        if (newW == oldW && newH == oldH) return

        // Keep the pinch focal point visually stationary while resizing.
        val actualScaleW = newW.toFloat() / oldW
        val actualScaleH = newH.toFloat() / oldH
        x -= focusX * (actualScaleW - 1f)
        y -= focusY * (actualScaleH - 1f)

        lp.width = newW
        lp.height = newH
        layoutParams = lp
    }

    // ---------------------------------------------------------------
    // Label overlay - each container only draws its own labels, already
    // projected into container-local pixel coordinates by ModelInstance.
    // ---------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        borderPaint.let { canvas.drawRoundRect(RectF(1f, 1f, width - 1f, height - 1f), 12f, 12f, it) }

        if (!modelInstance.labelsVisible || !modelInstance.isReady) return
        val labels = modelInstance.projectLabels(width, height)
        for ((sx, sy, text) in labels) {
            if (sx < 0f || sy < 0f || sx > width || sy > height) continue
            // connector line from the part to a small offset label chip
            val chipX = (sx + 18).coerceAtMost(width - 8f - labelTextPaint.measureText(text) - 12f)
            val chipY = (sy - 18).coerceAtLeast(8f)
            canvas.drawLine(sx, sy, chipX, chipY, connectorPaint)
            canvas.drawCircle(sx, sy, 4f, dotPaint)

            val textWidth = labelTextPaint.measureText(text)
            val pad = 6f
            val rect = RectF(chipX - pad, chipY - labelTextPaint.textSize, chipX + textWidth + pad, chipY + pad)
            canvas.drawRoundRect(rect, 6f, 6f, labelBgPaint)
            canvas.drawText(text, chipX, chipY, labelTextPaint)
        }
    }

    companion object {
        private const val ZOOM_SENSITIVITY = 8f
    }
}
