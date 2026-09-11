package app.trierarch.config

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import app.trierarch.R

/** Glass profile list; configuration editing is intentionally a later step. */
class ConfigBookOverlay(
    context: Context,
    private val onDismiss: () -> Unit,
    private val onStartInternalShell: () -> Unit,
    private val onStartProot: (ProfileStore.ProotProfile) -> Unit,
    private val onStartChroot: (ProfileStore.ChrootProfile) -> Unit,
    private val onStartDroidspaces: (ProfileStore.DroidspacesProfile) -> Unit,
    private val isRuntimeRunning: () -> Boolean,
    private val activeProfileId: () -> String?,
    private val onStopRuntime: () -> Unit,
    private val onStartX11: (onReady: () -> Unit, onFailure: (String) -> Unit) -> Unit,
    private val onShowTerminal: () -> Unit,
) : FrameLayout(context) {
    private val store = ProfileStore(context.applicationContext)
    private val list = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    init {
        setBackgroundColor(Color.argb(56, 0, 0, 0))
        isClickable = true
        setOnClickListener { dismiss() }
        val panel = FrameLayout(context).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = glassBackground()
            elevation = dp(18).toFloat()
            setOnClickListener { }
            addView(ScrollView(context).apply {
                isVerticalScrollBarEnabled = true
                addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        addView(panel, LayoutParams(initialPanelWidth(), initialPanelHeight()).apply { gravity = Gravity.CENTER })
        reload()
    }

    fun dismiss() {
        (parent as? FrameLayout)?.removeView(this)
        onDismiss()
    }

    private fun reload() {
        list.removeAllViews()
        val files = store.list()
        if (files.isEmpty()) {
            list.addView(label("No profiles", 14f).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(24), dp(12), dp(24))
            })
            return
        }
        files.forEach { file -> list.addView(profileRow(file), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }) }
    }

    private fun profileRow(file: java.io.File): View {
        val id = file.nameWithoutExtension
        val active = isRuntimeRunning() && activeProfileId() == id
        val anotherRuntimeActive = isRuntimeRunning() && !active
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(8), dp(4), dp(4), dp(4))
            background = rowBackground()
            addView(label(id, 14f).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(iconButton(R.drawable.ic_profile_delete, "Delete profile") { deleteProfile(file) }
                .apply { isEnabled = !isRuntimeRunning() }, fixedButtonLayout())
            addView(iconButton(R.drawable.ic_profile_tune, "Edit configuration") {
                toast("Configuration editing is not part of this UI step")
            }.apply { isEnabled = false }, fixedButtonLayout())
            val action = if (active) {
                iconButton(R.drawable.ic_profile_stop, "Stop profile") { stopProfile() }
            } else {
                iconButton(R.drawable.ic_profile_start, "Start profile") { startProfile(file) }
                    .apply { isEnabled = !anotherRuntimeActive }
            }
            addView(action, fixedButtonLayout())
        }
    }

    private fun deleteProfile(file: java.io.File) {
        AlertDialog.Builder(context).setMessage("Delete ${file.nameWithoutExtension}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                runCatching { store.delete(file) }.onSuccess { reload() }
                    .onFailure { toast(it.message ?: "Unable to delete profile") }
            }.show()
    }

    private fun stopProfile() {
        runCatching { onStopRuntime(); onShowTerminal() }
            .onFailure { toast(it.message ?: "Unable to stop profile") }
        reload()
    }

    private fun startProfile(file: java.io.File) {
        runCatching { store.read(file) }.onSuccess { content ->
            runCatching { store.validate(content); store.runtime(content) to store.display(content) }
                .onSuccess { (runtime, display) -> startRuntime(runtime, display, content) }
                .onFailure { toast(it.message ?: "Invalid profile") }
        }.onFailure { toast(it.message ?: "Unable to read profile") }
    }

    private fun startRuntime(runtime: String, display: String, content: String) {
        val launch: (() -> Unit) -> Unit = { action ->
            if (display == ProfileStore.DISPLAY_X11) {
                onStartX11(
                    { runCatching(action).onFailure { error -> onShowTerminal(); toast(error.message ?: "Unable to start profile") } },
                    { message -> onShowTerminal(); toast(message) },
                )
                dismiss()
            } else {
                runCatching { onShowTerminal(); action() }.onSuccess { dismiss() }
                    .onFailure { error -> toast(error.message ?: "Unable to start profile") }
            }
        }
        when (runtime) {
            ProfileStore.RUNTIME_INTERNAL_SHELL -> launch(onStartInternalShell)
            ProfileStore.RUNTIME_PROOT -> runCatching { store.prootProfile(content) }
                .onSuccess { profile -> launch { onStartProot(profile) } }
                .onFailure { toast(it.message ?: "Invalid PRoot configuration") }
            ProfileStore.RUNTIME_CHROOT -> runCatching { store.chrootProfile(content) }
                .onSuccess { profile -> launch { onStartChroot(profile) } }
                .onFailure { toast(it.message ?: "Invalid chroot configuration") }
            ProfileStore.RUNTIME_DROIDSPACES -> runCatching { store.droidspacesProfile(content) }
                .onSuccess { profile -> launch { onStartDroidspaces(profile) } }
                .onFailure { toast(it.message ?: "Invalid DroidSpaces configuration") }
            else -> toast("Runtime '$runtime' cannot be started yet")
        }
    }

    private fun iconButton(@DrawableRes icon: Int, description: String, action: () -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(Color.rgb(236, 239, 244))
            background = iconBackground()
            contentDescription = description
            ViewCompat.setTooltipText(this, description)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { action() }
        }

    private fun fixedButtonLayout() = LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(2) }
    private fun label(text: String, size: Float) = TextView(context).apply {
        this.text = text; setTextColor(Color.rgb(242, 242, 242)); setTextSize(size)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    private fun rowBackground() = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat(); setColor(Color.argb(74, 255, 255, 255))
    }
    private fun iconBackground() = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(Color.argb(45, 255, 255, 255))
    }
    private fun glassBackground() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(Color.argb(220, 32, 32, 36))
        cornerRadius = dp(20).toFloat(); setStroke(dp(1), Color.argb(145, 255, 255, 255))
    }
    private fun initialPanelWidth() = (resources.displayMetrics.widthPixels * .88f).toInt().coerceAtLeast(1)
    private fun initialPanelHeight() = (resources.displayMetrics.heightPixels * .78f).toInt().coerceAtLeast(1)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
