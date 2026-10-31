package com.HeheJuice.TangentLyrics.util

import android.content.Context
import android.graphics.*
import androidx.core.graphics.ColorUtils
import com.HeheJuice.TangentLyrics.data.PickedMusic

object CardGenerator {

    fun generateCard(context: Context, music: PickedMusic): Bitmap {
        val width = 2160
        val height = 2840 
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rectF = RectF(0f, 0f, width.toFloat(), height.toFloat())

        val cardRadius = 120f
        val cardPath = Path().apply { addRoundRect(rectF, cardRadius, cardRadius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(cardPath)

        val art = music.albumArt
        val palette = extractPalette(art)
        val c1 = palette[0]
        val c2 = palette[1]
        val c3 = palette[2]

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(c1, c2, c3),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(rectF, bgPaint)

        val textColor = Color.WHITE
        val subTextColor = Color.parseColor("#E0E0E0")

        val margin = 160f
        val imgSize = width - (margin * 2)
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

        val spaceTop = imgRect.bottom
        val spaceBottom = height.toFloat()
        val spaceCenterY = spaceTop + (spaceBottom - spaceTop) / 2f

        val customTypeface = loadTypeface(context, bold = true)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 152f
            typeface = customTypeface
            textAlign = Paint.Align.CENTER
        }
        
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = 96f
            typeface = customTypeface
            textAlign = Paint.Align.CENTER
        }

        val titleFm = titlePaint.fontMetrics
        val artistFm = artistPaint.fontMetrics
        
        val titleHeight = titleFm.descent - titleFm.ascent
        val artistHeight = artistFm.descent - artistFm.ascent
        val textGap = 64f
        
        val totalTextHeight = titleHeight + textGap + artistHeight
        val textBlockTopY = spaceCenterY - (totalTextHeight / 2f)
        
        val titleBaseline = textBlockTopY - titleFm.ascent
        val artistBaseline = titleBaseline + titleFm.descent + textGap - artistFm.ascent

        val titleText = ellipsizeText(music.title, titlePaint, (width - margin * 2))
        canvas.drawText(titleText, width / 2f, titleBaseline, titlePaint)

        val artistText = ellipsizeText(music.artist, artistPaint, (width - margin * 2))
        canvas.drawText(artistText, width / 2f, artistBaseline, artistPaint)

        // ★ 繪製左下角 App Icon 和名字
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("show_app_icon", false)) {
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
                textSize = 64f // 稍微調大一點點以配合極寬字體
                // ★ 修改：使用 MUSIC 的極限字體
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