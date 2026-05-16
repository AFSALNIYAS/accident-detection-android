package com.example.accident_detection3.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.example.accident_detection3.R
import com.google.android.material.textfield.TextInputEditText

/**
 * EditText that forces a red cursor color.
 * On API 29+ uses the public textCursorDrawable API.
 * On older APIs falls back to reflection.
 * The parent TextInputLayout should also set
 * app:materialThemeOverlay="@style/ThemeOverlay.App.RedCursor"
 * so Material3 doesn't override the cursor color.
 */
class RedCursorEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.editTextStyle
) : TextInputEditText(context, attrs, defStyleAttr) {

    private val accentColor by lazy { ContextCompat.getColor(context, R.color.accent) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyCursorColor()
        post { applyCursorColor() }
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) {
            applyCursorColor()
            post { applyCursorColor() }
        }
    }

    private fun applyCursorColor() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textCursorDrawable = ColorDrawable(accentColor)
            } else {
                val editorField = android.widget.TextView::class.java.getDeclaredField("mEditor")
                editorField.isAccessible = true
                val editor = editorField.get(this) ?: return
                runCatching {
                    val f = editor.javaClass.getDeclaredField("mCursorDrawable")
                    f.isAccessible = true
                    f.set(editor, arrayOf(ColorDrawable(accentColor), ColorDrawable(accentColor)))
                }
                runCatching {
                    val f = editor.javaClass.getDeclaredField("mDrawableForCursor")
                    f.isAccessible = true
                    f.set(editor, ColorDrawable(accentColor))
                }
            }
        } catch (_: Exception) {}
    }
}
