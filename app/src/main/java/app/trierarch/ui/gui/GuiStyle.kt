package app.trierarch.ui.gui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import java.util.Date
import java.util.Locale

/** Shared colors, dimensions and small view builders for the GUI. */
internal class GuiStyle(private val context: Context) {
    val secondaryText = 0xFF9CA3AF.toInt()
    val errorText = 0xFFF87171.toInt()
    val dividerColor = 0xFF23262B.toInt()
    val runningText = 0xFF7CE38B.toInt()
    val pendingText = 0xFFFBBF24.toInt()
    val fieldColor = 0xFF111318.toInt()

    fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()

    fun title(text: String): TextView = TextView(context).apply {
        setText(text)
        setTextColor(android.graphics.Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
    }

    fun headerButton(label: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(context).apply {
            minWidth = 0
            text = label
            setOnClickListener { onClick() }
        }

    fun sectionLabel(text: String): TextView = TextView(context).apply {
        setText(text)
        setTextColor(secondaryText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        letterSpacing = 0.08f
        setPadding(0, dp(20), 0, dp(4))
    }

    fun info(label: String, value: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, 0)
        addView(TextView(context).apply {
            setText(label)
            setTextColor(secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            width = dp(88)
        })
        addView(
            TextView(context).apply {
                setText(value)
                setTextColor(android.graphics.Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.MONOSPACE
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
    }

    fun statusText(message: String, color: Int = secondaryText): TextView = TextView(context).apply {
        setText(message)
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, dp(4), 0, 0)
    }

    fun primaryAction(label: String, busy: Boolean, onClick: () -> Unit) =
        MaterialButton(context).apply {
            minWidth = 0
            text = label
            isEnabled = !busy
            setOnClickListener { onClick() }
        }

    fun secondaryAction(label: String, busy: Boolean, onClick: () -> Unit) =
        TextView(context).apply {
            text = label
            setTextColor(errorText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            isEnabled = !busy
            alpha = if (busy) 0.4f else 1f
            setOnClickListener { onClick() }
        }

    fun textAction(label: String, color: Int = secondaryText, onClick: () -> Unit) =
        TextView(context).apply {
            text = label
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    fun divider(): View = View(context).apply { setBackgroundColor(dividerColor) }

    fun megabytes(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)

    fun downloadText(downloaded: Long, total: Long): String {
        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
        return "Downloading… $percent% (${megabytes(downloaded)} / ${megabytes(total)})"
    }

    fun timeText(millis: Long): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date(millis))
}
