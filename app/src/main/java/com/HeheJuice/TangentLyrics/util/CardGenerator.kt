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
import androidx.core.graphics.ColorUtils
import com.HeheJuice.TangentLyrics.data.PickedMusic

object CardGenerator {

    fun generateCard(context: Context, music: PickedMusic): Bitmap {
        val width = 2160
        val margin = 160f
        val imgSize = width - (margin * 2)
        
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val showAppIcon = prefs.getBoolean("show_app_icon", false)

        // ★ 準備字體與畫筆，以便預先計算文字高度
        val customTypeface = loadTypeface(context, bold = true)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 152f
            typeface = customTypeface
            textAlign = Paint.Align.LEFT // ★ 改為左對齊
        }
        
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            textSize = 96f
            typeface = customTypeface
            textAlign = Paint.Align.LEFT // ★ 改為左對齊
        }

        // 計算文字高度
        val titleFm = titlePaint.fontMetrics
        val artistFm = artistPaint.fontMetrics
        val titleHeight = titleFm.descent - titleFm.ascent
        val artistHeight = artistFm.descent - artistFm.ascent
        
        val textTopMargin = 120f // 圖片與文字的間距
        val textGap = 40f        // 標題與歌手的間距
        val bottomMargin = margin // 底部留白
        
        // ★ 動態計算卡片高度：上方留白 + 圖片 + 文字間距 + 標題 + 歌手間距 + 歌手 + 底部留白 + (若有標籤則加高)
        val iconSpace = if (showAppIcon) 200f else 0f
        val height = margin + imgSize + textTopMargin + titleHeight + textGap + artistHeight + bottomMargin + iconSpace

        val bitmap = Bitmap.createBitmap(width, height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rectF = RectF(0f, 0f, width.toFloat(), height)

        val cardRadius = 120f
        val cardPath = Path().apply { addRoundRect(rectF, cardRadius, cardRadius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(cardPath)

        // 繪製背景漸層
        val art = music.albumArt
        val palette = extractPalette(art)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height,
            intArrayOf(palette[0], palette[1], palette[2]),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(rectF, bgPaint)

        // 繪製專輯封面
        val imgRect = RectF(margin, margin, margin + imgSize, margin + imgSize)
        val imgPath = Path().apply { addRoundRect(imgRect, 96f, 96f, Path.Direction.CW) }
        
        canvas.save()
        canvas.clipPath(imgPath)
        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (art != null) {
            val srcRect = getCenterCropRect(art, imgSize.toInt(), imgSize.toInt())
            canvas.drawBitmap(art, srcRect, imgRect, imgPaint)
        } else {
            imgPaint.color = Color.WHITE
            canvas.drawRect(imgRect, imgPaint)
        }
        canvas.restore()

        // ★ 計算文字 Y 軸位置 (從圖片底部往下推算)
        val textStartY = imgRect.bottom + textTopMargin
        val titleBaseline = textStartY - titleFm.ascent
        val artistBaseline = titleBaseline + titleFm.descent + textGap - artistFm.ascent

        // ★ 繪製文字 (X 軸與圖片左邊緣 margin 對齊)
        val titleText = ellipsizeText(music.title, titlePaint, (width - margin * 2))
        canvas.drawText(titleText, margin, titleBaseline, titlePaint)

        val artistText = ellipsizeText(music.artist, artistPaint, (width - margin * 2))
        canvas.drawText(artistText, margin, artistBaseline, artistPaint)

        // ★ 繪製左下角 App Icon 和名字 (因為高度變了，所以用 height - margin 計算)
        if (showAppIcon) {
            drawAppIcon(context, canvas, margin, height - margin)
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

    private fun loadTypeface(context: Context, bold: Boolean): Typeface {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val weight = if (bold) 700 else 400
                Typeface.Builder(context.assets, "GoogleSansFlex.ttf")
                    .setWeight(weight)
                    .setFontVariationSettings("'wght' $weight, 'ROND' 100")
                    .build()
            } else {
                Typeface.createFromAsset(context.assets, "GoogleSansFlex.ttf")
            }
        } catch (_: Exception) {
            if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
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

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var ellipsized = text
        while (ellipsized.isNotEmpty() && paint.measureText("$ellipsized...") > maxWidth) {
            ellipsized = ellipsized.substring(0, ellipsized.length - 1)
        }
        return "$ellipsized..."
    }
}