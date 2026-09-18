package app.trierarch.ui

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.edit
import app.trierarch.R
import kotlin.math.abs
import kotlin.math.roundToInt

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
    private val sizePx = context.dp(56)
    private val shellInset = context.dp(4).toFloat()
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

    init {
        layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
        contentDescription = "Trierarch menu"
        scaleType = ScaleType.CENTER_INSIDE
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
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
            )
            true
        }

        MotionEvent.ACTION_UP -> {
            if (dragging) persistPosition() else performClick()
            true
        }

        MotionEvent.ACTION_CANCEL -> {
            if (dragging) persistPosition()
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
        val bounds = RectF(centerX - radiusX, centerY - radiusY, centerX + radiusX, centerY + radiusY)
        canvas.drawOval(bounds, shellFill)
        canvas.drawOval(bounds, shellStroke)
        super.onDraw(canvas)
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
        val SHELL_FILL = Color.argb(184, 26, 26, 26)
        val SHELL_STROKE = Color.argb(140, 255, 255, 255)
        const val PREF_CENTER_X = "menu_orb_center_x_fraction"
        const val PREF_CENTER_Y = "menu_orb_center_y_fraction"
        const val DEFAULT_CENTER_X = 0.88f
        const val DEFAULT_CENTER_Y = 0.42f
    }
}
