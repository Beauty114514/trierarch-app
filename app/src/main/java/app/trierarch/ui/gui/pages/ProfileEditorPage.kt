package app.trierarch.ui.gui.pages

import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.trierarch.ui.gui.GuiHost

internal object ProfileEditorPage {
    fun render(host: GuiHost, id: String) {
        val style = host.style
        val field = EditText(host.context).apply {
            setText(host.profilesState.editorText)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setBackgroundColor(style.fieldColor)
            setPadding(style.dp(12), style.dp(12), style.dp(12), style.dp(12))
            gravity = Gravity.TOP or Gravity.START
            minLines = 18
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        host.header.addView(
            style.title("Edit $id"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        host.header.addView(style.headerButton("Save") {
            host.profiles.saveEditor(field.text.toString())
        })
        host.header.addView(style.headerButton("Cancel") { host.profiles.back() })
        host.content.addView(
            field,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        host.content.addView(TextView(host.context).apply {
            setText("Raw TOML. Save checks that the file has not changed since it was opened.")
            setTextColor(style.secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, style.dp(10), 0, 0)
        })
    }
}
