package app.trierarch.ui

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.edit
import app.trierarch.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A draggable, in-app launcher orb.
 *
 * Its position is saved as fractions of the usable host area, so it survives
 * size changes without depending on a fixed screen resolution. This is not a
 * system overlay and needs no special Android permission.
 */
class FloatingMenuOrbView(
    context: Context,
    private val preferences: SharedPreferences,
    private val onClick: (() -> Unit)? = null,
    private val onDragStarted: (() -> Unit)? = null,
    private val onPositionChanged: (() -> Unit)? = null,
) : AppCompatImageView(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val sizePx = context.dp(68)
    private val shellInset = context.dp(10).toFloat()
    private val shellStrokeWidth = context.dp(1).toFloat()
    private val shellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SHELL_FILL }
    private val shellStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SHELL_STROKE
        style = Paint.Style.STROKE
        strokeWidth = shellStrokeWidth
    }

    private var centerXFraction = preferences.getFloat(PREF_CENTER_X, DEFAULT_CENTER_X)
    private var centerYFraction = preferences.getFloat(PREF_CENTER_Y, DEFAULT_CENTER_Y)
    private var imeBottomInset = 0
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0f
    private var startY = 0f
    private var dragging = false
    private var shellScaleX = 1f
    private var shellScaleY = 1f
    private var shellAnimator: ValueAnimator? = null
    private val jellyOffsets = FloatArray(JELLY_POINT_COUNT)
    private val jellyVelocities = FloatArray(JELLY_POINT_COUNT)
    private val jellyPath = Path()
    private val jellyPointsX = FloatArray(JELLY_POINT_COUNT)
    private val jellyPointsY = FloatArray(JELLY_POINT_COUNT)
    private var jellyFrameScheduled = false
    private var jellyDragging = false
    private var jellyLastFrameNanos = 0L
    private var jellyBodyOffsetX = 0f
    private var jellyBodyOffsetY = 0f
    private var jellyBodyVelocityX = 0f
    private var jellyBodyVelocityY = 0f
    private var lastMotionEventMillis = 0L
    private var lastMotionRawX = 0f
    private var lastMotionRawY = 0f
    private var dragVelocityX = 0f
    private var dragVelocityY = 0f

    private val jellyFrameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        jellyFrameScheduled = false
        stepJelly(frameTimeNanos)
    }

    init {
        layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
        contentDescription = "Trierarch menu"
        scaleType = ScaleType.CENTER_INSIDE
        setPadding(context.dp(18), context.dp(18), context.dp(18), context.dp(18))
        setImageResource(R.drawable.ic_launcher_foreground)
        setWillNotDraw(false)
        elevation = context.dp(8).toFloat()
        isClickable = true
        isFocusable = true
    }

    /** Keeps the orb above the IME while retaining its saved relative position. */
    fun setImeBottomInset(inset: Int) {
        if (imeBottomInset == inset) return
        imeBottomInset = inset.coerceAtLeast(0)
        placeFromSavedPosition()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(::placeFromSavedPosition)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            downRawX = event.rawX
            downRawY = event.rawY
            startX = x
            startY = y
            dragging = false
            jellyDragging = false
            lastMotionEventMillis = event.eventTime
            lastMotionRawX = event.rawX
            lastMotionRawY = event.rawY
            dragVelocityX = 0f
            dragVelocityY = 0f
            true
        }

        MotionEvent.ACTION_MOVE -> {
            val deltaX = event.rawX - downRawX
            val deltaY = event.rawY - downRawY
            if (!dragging && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                dragging = true
                onDragStarted?.invoke()
            }
            if (dragging) moveTo(
                (startX + deltaX).roundToInt(),
                (startY + deltaY).roundToInt(),
            ).also { updateJellyFromMotion(event) }
            true
        }

        MotionEvent.ACTION_UP -> {
            if (dragging) {
                persistPosition()
                settleJelly()
            } else performClick()
            true
        }

        MotionEvent.ACTION_CANCEL -> {
            if (dragging) {
                persistPosition()
                settleJelly()
            }
            true
        }

        else -> super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        onClick?.invoke()
        return true
    }

    /** Bounces the glass shell while leaving the Trierarch logo unchanged. */
    fun playShellBounce(launching: Boolean) {
        shellAnimator?.cancel()
        val shellX = if (launching) {
            floatArrayOf(1f, 0.86f, 1.10f, 1.035f, 0.99f, 1f)
        } else {
            floatArrayOf(1f, 1.10f, 0.86f, 0.965f, 1.01f, 1f)
        }
        val shellY = if (launching) {
            floatArrayOf(1f, 1.11f, 0.94f, 0.98f, 1.005f, 1f)
        } else {
            floatArrayOf(1f, 0.94f, 1.11f, 1.02f, 0.995f, 1f)
        }
        shellAnimator = ValueAnimator.ofPropertyValuesHolder(
            PropertyValuesHolder.ofFloat("shellX", *shellX),
            PropertyValuesHolder.ofFloat("shellY", *shellY),
        ).apply {
            duration = BOUNCE_MILLIS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                shellScaleX = animator.getAnimatedValue("shellX") as Float
                shellScaleY = animator.getAnimatedValue("shellY") as Float
                invalidate()
            }
            start()
        }
    }

    /** Re-applies the saved relative position after a host size change. */
    fun refreshPosition() = placeFromSavedPosition()

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radiusX = (width - shellStrokeWidth - shellInset * 2f) / 2f * shellScaleX
        val radiusY = (height - shellStrokeWidth - shellInset * 2f) / 2f * shellScaleY
        drawJellyShell(canvas, centerX + jellyBodyOffsetX, centerY + jellyBodyOffsetY, radiusX, radiusY)
        super.onDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(jellyFrameCallback)
        jellyFrameScheduled = false
        jellyLastFrameNanos = 0L
        jellyOffsets.fill(0f)
        jellyVelocities.fill(0f)
        jellyBodyOffsetX = 0f
        jellyBodyOffsetY = 0f
        jellyBodyVelocityX = 0f
        jellyBodyVelocityY = 0f
        super.onDetachedFromWindow()
    }

    private fun updateJellyFromMotion(event: MotionEvent) {
        val elapsedMillis = (event.eventTime - lastMotionEventMillis).coerceAtLeast(1L)
        val elapsedSeconds = elapsedMillis / 1_000f
        val instantaneousX = (event.rawX - lastMotionRawX) / elapsedSeconds
        val instantaneousY = (event.rawY - lastMotionRawY) / elapsedSeconds
        dragVelocityX = dragVelocityX * VELOCITY_SMOOTHING + instantaneousX * (1f - VELOCITY_SMOOTHING)
        dragVelocityY = dragVelocityY * VELOCITY_SMOOTHING + instantaneousY * (1f - VELOCITY_SMOOTHING)
        lastMotionEventMillis = event.eventTime
        lastMotionRawX = event.rawX
        lastMotionRawY = event.rawY
        jellyDragging = true
        ensureJellyFrame()
    }

    private fun settleJelly() {
        jellyDragging = false
        ensureJellyFrame()
    }

    private fun stepJelly(frameTimeNanos: Long) {
        val previousFrame = jellyLastFrameNanos
        jellyLastFrameNanos = frameTimeNanos
        if (previousFrame == 0L) {
            ensureJellyFrame()
            invalidate()
            return
        }
        val deltaSeconds = ((frameTimeNanos - previousFrame) / NANOS_PER_SECOND).coerceIn(0f, MAX_FRAME_STEP_SECONDS)
        val speed = hypot(dragVelocityX, dragVelocityY)
        val energy = if (jellyDragging) {
            JELLY_DRAG_BASE_ENERGY +
                (1f - JELLY_DRAG_BASE_ENERGY) *
                    ((speed - context.dp(JELLY_MIN_SPEED_DP)) / context.dp(JELLY_MAX_SPEED_DP - JELLY_MIN_SPEED_DP))
                        .coerceIn(0f, 1f)
        } else {
            0f
        }
        val directionX = if (speed > 0f) dragVelocityX / speed else 0f
        val directionY = if (speed > 0f) dragVelocityY / speed else 0f
        val radius = ((width - shellStrokeWidth - shellInset * 2f) / 2f).coerceAtLeast(0f)
        var moving = jellyDragging
        val bodyTargetDistance = radius * JELLY_BODY_LAG_AMOUNT * energy
        jellyBodyVelocityX += (
            (-directionX * bodyTargetDistance - jellyBodyOffsetX) * JELLY_BODY_STIFFNESS -
                jellyBodyVelocityX * JELLY_BODY_DAMPING
            ) * deltaSeconds
        jellyBodyVelocityY += (
            (-directionY * bodyTargetDistance - jellyBodyOffsetY) * JELLY_BODY_STIFFNESS -
                jellyBodyVelocityY * JELLY_BODY_DAMPING
            ) * deltaSeconds
        jellyBodyOffsetX += jellyBodyVelocityX * deltaSeconds
        jellyBodyOffsetY += jellyBodyVelocityY * deltaSeconds
        if (
            abs(jellyBodyOffsetX) > JELLY_REST_EPSILON ||
                abs(jellyBodyOffsetY) > JELLY_REST_EPSILON ||
                abs(jellyBodyVelocityX) > JELLY_REST_EPSILON ||
                abs(jellyBodyVelocityY) > JELLY_REST_EPSILON
        ) moving = true
        repeat(JELLY_POINT_COUNT) { index ->
            val angle = index * FULL_TURN / JELLY_POINT_COUNT
            val axisX = cos(angle.toDouble()).toFloat()
            val axisY = sin(angle.toDouble()).toFloat()
            val alignment = axisX * directionX + axisY * directionY
            val leading = alignment.coerceAtLeast(0f)
            val trailing = (-alignment).coerceAtLeast(0f)
            val sideways = 1f - abs(alignment)
            val target = radius * energy * (
                JELLY_LEADING_PULL * leading * leading -
                    JELLY_TRAILING_COMPRESSION * trailing * trailing -
                    JELLY_SIDE_COMPRESSION * sideways * sideways
                )
            val acceleration = (target - jellyOffsets[index]) * JELLY_STIFFNESS - jellyVelocities[index] * JELLY_DAMPING
            jellyVelocities[index] += acceleration * deltaSeconds
            jellyOffsets[index] += jellyVelocities[index] * deltaSeconds
            if (abs(jellyOffsets[index]) > JELLY_REST_EPSILON || abs(jellyVelocities[index]) > JELLY_REST_EPSILON) moving = true
        }
        if (moving) {
            ensureJellyFrame()
            invalidate()
        } else {
            jellyLastFrameNanos = 0L
            jellyOffsets.fill(0f)
            jellyVelocities.fill(0f)
            jellyBodyOffsetX = 0f
            jellyBodyOffsetY = 0f
            jellyBodyVelocityX = 0f
            jellyBodyVelocityY = 0f
            invalidate()
        }
    }

    private fun ensureJellyFrame() {
        if (jellyFrameScheduled) return
        jellyFrameScheduled = true
        Choreographer.getInstance().postFrameCallback(jellyFrameCallback)
    }

    private fun drawJellyShell(canvas: Canvas, centerX: Float, centerY: Float, radiusX: Float, radiusY: Float) {
        repeat(JELLY_POINT_COUNT) { index ->
            val angle = index * FULL_TURN / JELLY_POINT_COUNT
            val radialOffset = jellyOffsets[index]
            jellyPointsX[index] = centerX + cos(angle.toDouble()).toFloat() * (radiusX + radialOffset)
            jellyPointsY[index] = centerY + sin(angle.toDouble()).toFloat() * (radiusY + radialOffset)
        }
        jellyPath.reset()
        val firstMidpointX = (jellyPointsX.last() + jellyPointsX.first()) / 2f
        val firstMidpointY = (jellyPointsY.last() + jellyPointsY.first()) / 2f
        jellyPath.moveTo(firstMidpointX, firstMidpointY)
        repeat(JELLY_POINT_COUNT) { index ->
            val next = (index + 1) % JELLY_POINT_COUNT
            jellyPath.quadTo(
                jellyPointsX[index],
                jellyPointsY[index],
                (jellyPointsX[index] + jellyPointsX[next]) / 2f,
                (jellyPointsY[index] + jellyPointsY[next]) / 2f,
            )
        }
        jellyPath.close()
        canvas.drawPath(jellyPath, shellFill)
        canvas.drawPath(jellyPath, shellStroke)
    }

    private fun placeFromSavedPosition() {
        val host = parent as? View ?: return
        if (host.width == 0 || usableHeight(host) == 0) return
        moveTo(
            (centerXFraction * host.width - width / 2f).roundToInt(),
            (centerYFraction * usableHeight(host) - height / 2f).roundToInt(),
        )
    }

    private fun moveTo(requestedLeft: Int, requestedTop: Int) {
        val host = parent as? View ?: return
        val maxLeft = (host.width - width).coerceAtLeast(0)
        val maxTop = (usableHeight(host) - height).coerceAtLeast(0)
        x = requestedLeft.coerceIn(0, maxLeft).toFloat()
        y = requestedTop.coerceIn(0, maxTop).toFloat()
        onPositionChanged?.invoke()
    }

    private fun persistPosition() {
        val host = parent as? View ?: return
        if (host.width == 0 || usableHeight(host) == 0) return
        centerXFraction = ((x + width / 2f) / host.width).coerceIn(0f, 1f)
        centerYFraction = ((y + height / 2f) / usableHeight(host)).coerceIn(0f, 1f)
        preferences.edit {
            putFloat(PREF_CENTER_X, centerXFraction)
            putFloat(PREF_CENTER_Y, centerYFraction)
        }
    }

    private fun usableHeight(host: View): Int = (host.height - imeBottomInset).coerceAtLeast(0)

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val BOUNCE_MILLIS = 360L
        const val JELLY_POINT_COUNT = 12
        val FULL_TURN = (Math.PI * 2.0).toFloat()
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val MAX_FRAME_STEP_SECONDS = 1f / 30f
        const val VELOCITY_SMOOTHING = 0.70f
        const val JELLY_MIN_SPEED_DP = 0
        const val JELLY_MAX_SPEED_DP = 450
        const val JELLY_DRAG_BASE_ENERGY = 0.55f
        const val JELLY_BODY_LAG_AMOUNT = 0.32f
        const val JELLY_BODY_STIFFNESS = 22f
        const val JELLY_BODY_DAMPING = 8f
        const val JELLY_LEADING_PULL = 0.60f
        const val JELLY_TRAILING_COMPRESSION = 0.04f
        const val JELLY_SIDE_COMPRESSION = 0.12f
        const val JELLY_STIFFNESS = 32f
        const val JELLY_DAMPING = 10f
        const val JELLY_REST_EPSILON = 0.02f
        val SHELL_FILL = Color.argb(184, 26, 26, 26)
        val SHELL_STROKE = Color.argb(140, 255, 255, 255)
        const val PREF_CENTER_X = "menu_orb_center_x_fraction"
        const val PREF_CENTER_Y = "menu_orb_center_y_fraction"
        const val DEFAULT_CENTER_X = 0.88f
        const val DEFAULT_CENTER_Y = 0.42f
    }
}
