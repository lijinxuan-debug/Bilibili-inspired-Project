package com.example.bilibili.util

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.TextViewCompat
import com.example.bilibili.R

object TextSelectHandleHelper {

    private val applied = mutableSetOf<Int>()

    fun applyPinkHandles(editText: EditText) {
        val token = System.identityHashCode(editText)
        if (!applied.add(token)) return

        val apply = Runnable { applyPinkHandlesInternal(editText) }
        if (editText.viewTreeObserver.isAlive) {
            editText.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    editText.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    apply.run()
                }
            })
        }
        apply.run()
    }

    fun applyPinkHandlesIn(activity: Activity) {
        val root = activity.window?.decorView ?: return
        applyPinkHandlesIn(root)
    }

    fun applyPinkHandlesIn(root: View) {
        root.post {
            forEachEditText(root) { applyPinkHandles(it) }
        }
    }

    private fun forEachEditText(view: View, block: (EditText) -> Unit) {
        if (view is EditText) block(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                forEachEditText(view.getChildAt(i), block)
            }
        }
    }

    private fun applyPinkHandlesInternal(editText: EditText) {
        try {
            val editorField = TextViewCompat::class.java.getDeclaredField("mEditor")
            editorField.isAccessible = true
            val editor = editorField.get(editText) ?: return

            val editorClass = editor.javaClass
            val context = editText.context
            val pink = context.getColor(R.color.bili_pink)

            bindHandle(
                editor,
                editorClass,
                "mSelectHandleLeft",
                loadHandle(context, R.drawable.text_cursor_handle_left, pink),
            )
            bindHandle(
                editor,
                editorClass,
                "mSelectHandleRight",
                loadHandle(context, R.drawable.text_cursor_handle_right, pink),
            )
            bindHandle(
                editor,
                editorClass,
                "mSelectHandleCenter",
                loadHandle(context, R.drawable.text_cursor_handle_middle, pink),
            )
        } catch (_: Exception) {
            // 反射失败时依赖 BilibiliEditTextStyle 中的主题 drawable
        }
    }

    private fun bindHandle(editor: Any, editorClass: Class<*>, fieldName: String, drawable: Drawable?) {
        if (drawable == null) return
        val field = editorClass.getDeclaredField(fieldName).apply { isAccessible = true }
        when (val handle = field.get(editor)) {
            is ImageView -> {
                handle.setImageDrawable(drawable.constantState?.newDrawable()?.mutate() ?: drawable)
                handle.scaleType = ImageView.ScaleType.CENTER
            }
            is Drawable -> field.set(editor, drawable)
            null -> field.set(editor, drawable)
        }
    }

    private fun loadHandle(
        context: android.content.Context,
        resId: Int,
        tint: Int,
    ): Drawable? = AppCompatResources.getDrawable(context, resId)?.mutate()?.also {
        DrawableCompat.setTint(it, tint)
    }
}
