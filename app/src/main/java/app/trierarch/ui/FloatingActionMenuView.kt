package app.trierarch.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import app.trierarch.R
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * A draggable in-app action menu.
 */
class FloatingActionMenuView(
    context: Context,
    preferences: SharedPreferences,
    private val onReturnToShell: () -> Unit,
) : FrameLayout(context) {
    private val orbSize = context.dp(48)
    private val fanRadius = context.dp(108).toFloat()
    private val satellites = listOf(
        createSatellite(
            icon = R.drawable.ic_floating_action_terminal,
            description = "Return to Trierarch shell",
            onClick = ::returnToShell,
        ),
    )
    private val mainOrb = FloatingMenuOrbView(
        context = context,
        preferences = preferences,
        onClick = ::toggle,
        onDragStarted = { collapse(withBounce = false) },
        onPositionChanged = { if (expanded) placeSatellites(animate = false) },
    )

    private var expanded = false
    private var displayActive = true
    private var imeBottomInset = 0

    init {
        clipChildren = false
        clipToPadding = false
        addView(mainOrb)
        satellites.forEach(::addView)
    }

    fun setImeBottomInset(inset: Int) {
        imeBottomInset = inset.coerceAtLeast(0)
        mainOrb.setImeBottomInset(imeBottomInset)
        if (expanded) post { placeSatellites(animate = false) }
    }

    /** Hides menu controls when the current surface has no available actions. */
    fun setDisplayActive(active: Boolean) {
        if (displayActive == active) return
        displayActive = active
        if (active) {
            visibility = View.VISIBLE
            return
        }
        expanded = false
        mainOrb.cancelShellMotion()
        satellites.forEach { satellite ->
            satellite.animate().cancel()
            satellite.visibility = View.INVISIBLE
            satellite.alpha = 0f
            satellite.scaleX = COLLAPSED_SCALE
            satellite.scaleY = COLLAPSED_SCALE
        }
        visibility = View.GONE
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        post {
            mainOrb.refreshPosition()
            if (expanded) placeSatellites(animate = false)
        }
    }

    private fun toggle() {
        if (expanded) collapse() else expand()
    }

    private fun expand() {
        if (expanded) return
        expanded = true
        mainOrb.playShellBounce(launching = true)
        val targets = satelliteTargets()
        val origin = satelliteOrigin()
        satellites.zip(targets).forEachIndexed { index, (satellite, target) ->
            satellite.animate().cancel()
            satellite.x = origin.x
            satellite.y = origin.y
            satellite.alpha = 0f
            satellite.scaleX = COLLAPSED_SCALE
            satellite.scaleY = COLLAPSED_SCALE
            satellite.visibility = View.VISIBLE
            satellite.animate()
                .x(target.x)
                .y(target.y)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(BOUNCE_LAUNCH_DELAY + index * STAGGER_MILLIS)
                .setDuration(EXPAND_MILLIS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun collapse(withBounce: Boolean = true) {
        if (!expanded) return
        expanded = false
        if (withBounce) mainOrb.playShellBounce(launching = false)
        val launchDelay = if (withBounce) BOUNCE_LAUNCH_DELAY else 0L
        val origin = satelliteOrigin()
        satellites.forEachIndexed { index, satellite ->
            satellite.animate().cancel()
            satellite.animate()
                .x(origin.x)
                .y(origin.y)
                .alpha(0f)
                .scaleX(COLLAPSED_SCALE)
                .scaleY(COLLAPSED_SCALE)
                .setStartDelay(launchDelay + (satellites.lastIndex - index) * COLLAPSE_STAGGER_MILLIS)
                .setDuration(COLLAPSE_MILLIS)
                .withEndAction { if (!expanded) satellite.visibility = View.INVISIBLE }
                .start()
        }
    }

    private fun returnToShell() {
        collapse()
        onReturnToShell()
    }

    private fun placeSatellites(animate: Boolean) {
        if (!expanded) return
        satellites.zip(satelliteTargets()).forEach { (satellite, target) ->
            satellite.animate().cancel()
            if (animate) satellite.animate().x(target.x).y(target.y).setDuration(REPOSITION_MILLIS).start()
            else {
                satellite.x = target.x
                satellite.y = target.y
            }
        }
    }

    /** Chooses a satellite position toward the largest safe interior area. */
    private fun satelliteTargets(): List<PointF> {
        if (width == 0 || height == 0) return satellites.map { PointF(mainOrb.x, mainOrb.y) }
        val usableHeight = (height - imeBottomInset).coerceAtLeast(orbSize)
        val centerX = mainOrb.x + mainOrb.width / 2f
        val centerY = mainOrb.y + mainOrb.height / 2f
        val preferred = angleOf(usableHeight / 2f - centerY, width / 2f - centerX)
        val fullTurn = (PI * 2.0).toFloat()
        val candidateAxes = (0 until 8)
            .map { index -> -PI.toFloat() + index * (fullTurn / 8f) }
            .sortedBy { angularDistance(it, preferred) }

        return candidateAxes
            .map { axis -> pointFor(centerX, centerY, axis) }
            .firstOrNull(::fitsUsableArea)
            ?.let(::listOf)
            ?: listOf(pointFor(centerX, centerY, preferred).clampToUsableArea())
    }

    private fun pointFor(centerX: Float, centerY: Float, angle: Float): PointF = PointF(
        centerX + fanRadius * cos(angle.toDouble()).toFloat() - orbSize / 2f,
        centerY + fanRadius * sin(angle.toDouble()).toFloat() - orbSize / 2f,
    )

    private fun satelliteOrigin(): PointF = PointF(
        mainOrb.x + (mainOrb.width - orbSize) / 2f,
        mainOrb.y + (mainOrb.height - orbSize) / 2f,
    )

    private fun fitsUsableArea(point: PointF): Boolean =
        point.x >= 0f && point.y >= 0f && point.x + orbSize <= width && point.y + orbSize <= height - imeBottomInset

    private fun PointF.clampToUsableArea(): PointF = PointF(
        x.coerceIn(0f, (width - orbSize).coerceAtLeast(0).toFloat()),
        y.coerceIn(0f, (height - imeBottomInset - orbSize).coerceAtLeast(0).toFloat()),
    )

    private fun angularDistance(first: Float, second: Float): Float {
        val difference = kotlin.math.abs(first - second)
        return min(difference, (PI * 2).toFloat() - difference)
    }

    private fun angleOf(y: Float, x: Float): Float = atan2(y.toDouble(), x.toDouble()).toFloat()

    private fun createSatellite(
        icon: Int,
        description: String,
        onClick: () -> Unit,
    ): AppCompatImageView = AppCompatImageView(context).apply {
        layoutParams = LayoutParams(orbSize, orbSize)
        contentDescription = description
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
        setImageResource(icon)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(170, 38, 42, 54))
            setStroke(context.dp(1), Color.argb(155, 218, 232, 255))
        }
        elevation = context.dp(6).toFloat()
        visibility = View.INVISIBLE
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val COLLAPSED_SCALE = 0.52f
        const val EXPAND_MILLIS = 180L
        const val COLLAPSE_MILLIS = 140L
        const val REPOSITION_MILLIS = 120L
        const val STAGGER_MILLIS = 28L
        const val COLLAPSE_STAGGER_MILLIS = 16L
        const val BOUNCE_LAUNCH_DELAY = 54L
    }
}
