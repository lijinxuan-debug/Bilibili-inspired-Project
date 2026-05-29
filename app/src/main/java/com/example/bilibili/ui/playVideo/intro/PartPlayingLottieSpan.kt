package com.example.bilibili.ui.playVideo.intro

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.example.bilibili.R

/**
 * 将分 P 播放波形 Lottie 作为 [ImageSpan] 嵌进标题，占位与一字相当。
 */
object PartPlayingLottieSpan {

    private const val ICON_PLACEHOLDER = "\uFFFC"
    private var cachedComposition: LottieComposition? = null

    fun preload(context: Context) {
        ensureComposition(context) { }
    }

    fun ensureComposition(context: Context, onReady: (LottieComposition) -> Unit) {
        cachedComposition?.let {
            onReady(it)
            return
        }
        LottieCompositionFactory.fromAsset(context.applicationContext, "audio_wave.json")
            .addListener { composition ->
                cachedComposition = composition
                onReady(composition)
            }
    }

    fun apply(
        textView: TextView,
        title: String,
        composition: LottieComposition,
    ): LottieDrawable {
        val iconSize = textView.textSize.toInt().coerceAtLeast(1)
        val drawable = createDrawable(textView.context, composition, iconSize)
        drawable.callback = textView

        val content = "$ICON_PLACEHOLDER $title"
        val spannable = SpannableString(content)
        val span = CenteredImageSpan(drawable)
        spannable.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = spannable
        drawable.start()
        return drawable
    }

    fun clear(drawable: LottieDrawable?) {
        drawable?.stop()
        drawable?.callback = null
    }

    private fun createDrawable(
        context: Context,
        composition: LottieComposition,
        sizePx: Int,
    ): LottieDrawable {
        val pink = ContextCompat.getColor(context, R.color.bili_pink)
        return LottieDrawable().apply {
            setComposition(composition)
            repeatCount = LottieDrawable.INFINITE
            setBounds(0, 0, sizePx, sizePx)
            addValueCallback(
                KeyPath("Ellipse 1079"),
                LottieProperty.OPACITY,
                LottieValueCallback(0),
            )
            addValueCallback(
                KeyPath("**"),
                LottieProperty.COLOR_FILTER,
                LottieValueCallback(PorterDuffColorFilter(pink, PorterDuff.Mode.SRC_IN)),
            )
        }
    }

  /** 让图标在单行内与文字垂直居中，换行后第二行从文字起始对齐。 */
    private class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable, ALIGN_CENTER) {
        override fun getSize(
            paint: android.graphics.Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fontMetrics: android.graphics.Paint.FontMetricsInt?,
        ): Int {
            val drawable = drawable
            val rect = drawable.bounds
            fontMetrics?.let { fm ->
                val fontHeight = fm.descent - fm.ascent
                val drawableHeight = rect.height()
                val offset = (fontHeight - drawableHeight) / 2
                fm.ascent = (fm.ascent + offset).coerceAtMost(0)
                fm.descent = fm.ascent + drawableHeight
                fm.top = fm.ascent
                fm.bottom = fm.descent
            }
            return rect.right
        }
    }
}
