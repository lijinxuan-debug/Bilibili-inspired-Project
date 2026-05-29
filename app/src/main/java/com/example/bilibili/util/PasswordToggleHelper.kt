package com.example.bilibili.util

import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.ImageButton

object PasswordToggleHelper {

    fun bind(toggle: ImageButton, editText: EditText) {
        var visible = false
        // 密码默认隐藏 → 闭眼；明文显示 → 睁眼
        toggle.setImageResource(com.example.bilibili.R.drawable.ic_visibility_off)
        toggle.setOnClickListener {
            visible = !visible
            editText.transformationMethod = if (visible) {
                HideReturnsTransformationMethod.getInstance()
            } else {
                PasswordTransformationMethod.getInstance()
            }
            editText.setSelection(editText.text.length)
            toggle.setImageResource(
                if (visible) com.example.bilibili.R.drawable.ic_visibility
                else com.example.bilibili.R.drawable.ic_visibility_off,
            )
        }
    }
}
