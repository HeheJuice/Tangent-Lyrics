/*
 * Tangent Lyrics - Turn your local music files into shareable lyric cards.
 * Copyright (C) 2026 HeheJuice
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
 
package com.HeheJuice.TangentLyrics.util

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import com.HeheJuice.TangentLyrics.data.PickedMusic

object LyricsCardGenerator {

    fun generateCard(context: Context, music: PickedMusic, selectedLyrics: String): Bitmap {
        val timeRegex = Regex("\\[\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?\\]")
        val cleanLyrics = selectedLyrics.split("\n").joinToString("\n") {
            it.replace(timeRegex, "").trimStart()
        }

        val palette = extractPalette(music.albumArt)
        val c1 = palette[0]
        val c2 = palette[1]
        val c3 = palette[2]

        val textColor = Color.WHITE
        val subTextColor = Color.parseColor("#B3B3B3")

        val margin = 160f
        val imgSize = 320f
        val gapBetweenImgAndText = 80f
        val headerHeight = margin + imgSize

        val customTypefaceBold = loadTypeface(context)

        val lyricsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 128f 
            typeface = customTypefaceBold
        }
        val lines = cleanLyrics.split("\n")
        var maxLineWidth = 0f
        lines.forEach { line ->
            val w = lyricsPaint.measureText(line)
            if (w > maxLineWidth) maxLineWidth = w
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 84f
            typeface = customTypefaceBold
        }
        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = 64f
            typeface = customTypefaceBold
        }
        val titleWidth = titlePaint.measureText(music.title)
        val artistWidth = subTextPaint.measureText("Song · ${music.artist}")
        val maxHeaderTextWidth = maxOf(titleWidth, artistWidth)
        val headerWidth = imgSize + gapBetweenImgAndText + maxHeaderTextWidth

        val minWidth = 1600f 
        val maxWidth = 2160f
        val safeExtraWidth = 120f 
        
        val contentWidth = maxOf(maxLineWidth + safeExtraWidth, headerWidth)
        val finalWidth = (contentWidth + margin * 2).coerceIn(minWidth, maxWidth).toInt()

        val staticLayout = StaticLayout.Builder
            .obtain(cleanLyrics, 0, cleanLyrics.length, lyricsPaint, (finalWidth - margin * 2).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(48f, 1f)
            .setIncludePad(false)
            .build()

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val showIcon = prefs.getBoolean("show_app_icon", false)
        val bottomExtra = if (showIcon) 160f else 0f

        val gap = 100f 
        val contentHeight = headerHeight + gap + staticLayout.height + margin + bottomExtra
        val finalHeight = contentHeight.toInt()

        val bitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rectF = RectF(0f, 0f, finalWidth.toFloat(), finalHeight.toFloat())

        val cardRadius = 120f
        val cardPath = Path().apply { addRoundRect(rectF, cardRadius, cardRadius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(cardPath)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.shader = LinearGradient(
            0f, 0f, finalWidth.toFloat(), finalHeight.toFloat(),
            intArrayOf(c1, c2, c3),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(rectF, bgPaint)

        val imgRect = RectF(margin, margin, margin + imgSize, margin + imgSize)
        val art = music.albumArt
        if (art != null) {
            val imgPath = Path().apply { addRoundRect(imgRect, 80f, 80f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(imgPath)
            val srcRect = getCenterCropRect(art, imgSize.toInt(), imgSize.toInt())
            canvas.drawBitmap(art, srcRect, imgRect, Paint(Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        }

        val textLeft = imgRect.right + gapBetweenImgAndText
        
        val textMaxWidth = finalWidth - textLeft - margin
        val titleText = ellipsizeText(music.title, titlePaint, textMaxWidth)
        val subText = ellipsizeText("Song · ${music.artist}", subTextPaint, textMaxWidth)

        // ★ 計算完美垂直居中對齊的基準線
        val titleFm = titlePaint.fontMetrics
        val subTextFm = subTextPaint.fontMetrics
        
        val textGap = 30f // 標題與副標題之間的間距
        val titleHeight = titleFm.descent - titleFm.ascent
        val subTextHeight = subTextFm.descent - subTextFm.ascent
        val totalTextHeight = titleHeight + textGap + subTextHeight

        // 取得圖片垂直中心點
        val textCenterY = imgRect.centerY()
        
        // 計算整個文字區塊的頂部 Y 座標
        val textBlockTop = textCenterY - (totalTextHeight / 2f)

        // 算出標題和副標題的 Baseline
        val titleBaseline = textBlockTop - titleFm.ascent
        val subTextBaseline = titleBaseline + titleFm.descent + textGap - subTextFm.ascent

        // 畫出文字
        canvas.drawText(titleText, textLeft, titleBaseline, titlePaint)
        canvas.drawText(subText, textLeft, subTextBaseline, subTextPaint)

        val lyricsStartY = headerHeight + gap
        canvas.save()
        canvas.translate(margin, lyricsStartY)
        staticLayout.draw(canvas)
        canvas.restore()

        if (showIcon) {
            drawAppIcon(context, canvas, margin, finalHeight - margin)
        }

        canvas.restore()
        return bitmap
    }

    private fun drawAppIcon(context: Context, canvas: Canvas, x: Float, bottomY: Float) {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val size = 96f
            val bmp = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            drawable.setBounds(0, 0, size.toInt(), size.toInt())
            drawable.draw(c)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 64f
                this.typeface = AppFont.music(context)
                textAlign = Paint.Align.LEFT
            }
            
            val iconLeft = x
            val iconTop = bottomY - size
            val textY = bottomY - (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)

            canvas.drawBitmap(bmp, iconLeft, iconTop, null)
            canvas.drawText("Tangent Lyrics", iconLeft + size + 32f, textY, paint)
        } catch (_: Exception) { }
    }

    private fun loadTypeface(context: Context): Typeface {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Typeface.Builder(context.assets, "GoogleSansFlex.ttf")
                    .setWeight(700)
                    .setFontVariationSettings("'wght' 700, 'ROND' 100")
                    .build()
            } else {
                Typeface.createFromAsset(context.assets, "GoogleSansFlex.ttf")
            }
        } catch (_: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var ellipsized = text
        while (ellipsized.isNotEmpty() && paint.measureText("$ellipsized...") > maxWidth) {
            ellipsized = ellipsized.substring(0, ellipsized.length - 1)
        }
        return "$ellipsized..."
    }

    private fun extractPalette(bitmap: Bitmap?): IntArray {
        if (bitmap == null) return intArrayOf(Color.parseColor("#222222"), Color.parseColor("#333333"), Color.parseColor("#111111"))
        try {
            val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, false)
            val colors = mutableListOf<Int>()
            for (x in 0 until 8) {
                for (y in 0 until 8) {
                    val c = scaled.getPixel(x, y)
                    if (Color.alpha(c) > 200) colors.add(c)
                }
            }
            if (colors.isEmpty()) return intArrayOf(Color.DKGRAY, Color.BLACK, Color.LTGRAY)
            colors.sortBy { ColorUtils.calculateLuminance(it) }
            return intArrayOf(
                adjustColorForBg(colors[colors.size / 5]),
                adjustColorForBg(colors[colors.size / 2]),
                adjustColorForBg(colors[colors.size * 4 / 5])
            )
        } catch (_: Exception) {
            return intArrayOf(Color.parseColor("#222222"), Color.parseColor("#333333"), Color.parseColor("#111111"))
        }
    }

    private fun adjustColorForBg(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = hsl[2].coerceIn(0.15f, 0.35f)
        hsl[1] = hsl[1].coerceIn(0.4f, 0.8f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun getCenterCropRect(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Rect {
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height
        val srcAspect = srcWidth.toFloat() / srcHeight
        val targetAspect = targetWidth.toFloat() / targetHeight
        return if (srcAspect > targetAspect) {
            val cropWidth = (srcHeight * targetAspect).toInt()
            val left = (srcWidth - cropWidth) / 2
            Rect(left, 0, left + cropWidth, srcHeight)
        } else {
            val cropHeight = (srcWidth / targetAspect).toInt()
            val top = (srcHeight - cropHeight) / 2
            Rect(0, top, srcWidth, top + cropHeight)
        }
    }
}