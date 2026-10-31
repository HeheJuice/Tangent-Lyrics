package com.HeheJuice.TangentLyrics.util

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object AppFont {

    private const val ASSET_NAME = "GoogleSansFlex.ttf"

    // ★ 終極防禦 Tag：只要 View 帶有這個 Tag，AppFont 就會徹底無視它（連同它的子 View）
    const val SKIP_FONT_TAG = "app_font_skip_tag"

    @Volatile private var regularCache: Typeface? = null
    @Volatile private var boldCache: Typeface? = null
    @Volatile private var musicCache: Typeface? = null

    fun regular(context: Context): Typeface {
        regularCache?.let { return it }
        synchronized(AppFont) {
            regularCache?.let { return it }
            val t = load(context, weight = 600, variation = "'wght' 600, 'ROND' 100")
            regularCache = t
            return t
        }
    }

    fun bold(context: Context): Typeface {
        boldCache?.let { return it }
        synchronized(AppFont) {
            boldCache?.let { return it }
            val t = load(context, weight = 800, variation = "'wght' 800, 'ROND' 100")
            boldCache = t
            return t
        }
    }

    // ★ 專為 "MUSIC" 字眼設計的極限字體
    fun music(context: Context): Typeface {
        musicCache?.let { return it }
        synchronized(AppFont) {
            musicCache?.let { return it }
            // Weight 1000 (最粗), Width 151 (最寬), ROND 0 (直角無圓角)
            val t = loadCustom(context, weight = 1000, width = 151, rond = 0)
            musicCache = t
            return t
        }
    }

    private fun load(context: Context, weight: Int, variation: String): Typeface {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Typeface.Builder(context.assets, ASSET_NAME)
                    .setWeight(weight)
                    .setFontVariationSettings(variation)
                    .build()
            } else {
                val base = Typeface.createFromAsset(context.assets, ASSET_NAME)
                Typeface.create(base, if (weight >= 700) Typeface.BOLD else Typeface.NORMAL)
            }
        } catch (_: Exception) {
            if (weight >= 700) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun loadCustom(context: Context, weight: Int, width: Int, rond: Int): Typeface {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Typeface.Builder(context.assets, ASSET_NAME)
                    // 在 OneUI 上，必須同時呼叫 setWeight 和 FontVariationSettings
                    .setWeight(weight)
                    .setFontVariationSettings("'wght' $weight, 'wdth' $width, 'ROND' $rond")
                    .build()
            } else {
                Typeface.createFromAsset(context.assets, ASSET_NAME)
            }
        } catch (_: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }

    fun apply(root: View, context: Context) {
        val reg = regular(context)
        val bld = bold(context)
        val musicTf = music(context) // ★ 提前實例化，確保比對時不為 null
        visit(root, reg, bld, musicTf)
    }

    private fun visit(v: View, reg: Typeface, bld: Typeface, musicTf: Typeface) {
        // ★ 核心修復：只要這個 View 被標記了 SKIP_FONT_TAG，直接 return，連子 View 都不會去動它！
        if (v.tag == SKIP_FONT_TAG) return

        if (v is TextView) {
            val current = v.typeface
            // 如果當前字體就是極限自訂字體，直接跳過不覆蓋
            if (current != null && current === musicTf) return

            val wasBold = current?.isBold == true ||
                    ((current?.style ?: 0) and Typeface.BOLD) != 0
            v.typeface = if (wasBold) bld else reg
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                visit(v.getChildAt(i), reg, bld, musicTf)
            }
        }
    }

    // ★ 允許 Spannable 局部覆蓋 Typeface 的 Span
    class CustomTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) {
            tp.typeface = typeface
        }
        override fun updateMeasureState(tp: TextPaint) {
            tp.typeface = typeface
        }
    }
}