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
 
package com.HeheJuice.TangentLyrics.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import com.HeheJuice.TangentLyrics.R
import com.HeheJuice.TangentLyrics.data.MusicSession
import com.HeheJuice.TangentLyrics.data.PickedMusic
import com.HeheJuice.TangentLyrics.util.AppFont
import com.HeheJuice.TangentLyrics.util.OptimizeEngine
import com.google.android.material.color.MaterialColors

class HomePageContent(private val activity: AppCompatActivity) {

    private var onPrimaryContainerColor: Int = 0
    private var onSurfaceColor: Int = 0
    private var onSurfaceVariantColor: Int = 0
    private var surfaceBrightColor: Int = 0

    private lateinit var contentHost: FrameLayout
    private var currentMusic: PickedMusic? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "show_lyrics_time" || key == "compact_music_info") {
            if (::contentHost.isInitialized) {
                render(animate = false)
            }
        }
    }

    init {
        activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefListener)
    }

    fun destroy() {
        activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private fun dpToPx(dp: Float): Int = OptimizeEngine.dpToPx(dp)
    private fun dpToPx(dp: Int): Int = OptimizeEngine.dpToPx(dp)

    fun build(): LinearLayout {
        val isDark = (activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        onPrimaryContainerColor = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorOnPrimaryContainer,
            if (isDark) Color.parseColor("#EADDFF") else Color.parseColor("#4F378B"))
        onSurfaceColor = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorOnSurface,
            if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F"))
        onSurfaceVariantColor = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorOnSurfaceVariant,
            Color.parseColor("#49454F"))
        surfaceBrightColor = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorSurfaceBright,
            if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF"))

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.app_name)
            textSize = 32f
            setTextColor(onPrimaryContainerColor)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8f)
                marginStart = dpToPx(8f)
                marginEnd = dpToPx(8f)
            }
        })

        contentHost = FrameLayout(activity).apply {
            clipChildren = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(16f)
            }
        }
        root.addView(contentHost)

        currentMusic = MusicSession.current
        render(animate = false)
        
        AppFont.apply(root, activity)
        return root
    }

    fun setMusic(music: PickedMusic?) {
        val oldUri = currentMusic?.uri
        val newUri = music?.uri

        currentMusic = music
        if (!::contentHost.isInitialized) return

        if (oldUri == newUri || oldUri == null) {
            render(animate = false)
        } else {
            render(animate = true)
        }
    }

    fun refreshStatusIcons() {}

    private fun render(animate: Boolean) {
        val music = currentMusic
        
        val newView = if (music == null) {
            buildEmptyState()
        } else {
            if (shouldUseWideLayout()) buildWideLayout(music) else buildTallLayout(music)
        }

        if (animate && contentHost.childCount > 0) {
            val oldView = contentHost.getChildAt(0)
            val width = activity.resources.displayMetrics.widthPixels.toFloat()
            newView.translationX = width
            contentHost.addView(newView)

            val duration = 350L
            val interpolator = android.view.animation.AnimationUtils.loadInterpolator(activity, android.R.interpolator.fast_out_slow_in)
            
            oldView.animate().translationX(-width).alpha(0.5f).setDuration(duration).setInterpolator(interpolator).withEndAction { contentHost.removeView(oldView) }.start()
            newView.animate().translationX(0f).setDuration(duration).setInterpolator(interpolator).start()
        } else {
            contentHost.removeAllViews()
            contentHost.addView(newView)
        }
        
        AppFont.apply(contentHost, activity)
    }

    private fun shouldUseWideLayout(): Boolean {
        val cfg = activity.resources.configuration
        return cfg.orientation == Configuration.ORIENTATION_LANDSCAPE || cfg.smallestScreenWidthDp >= 600
    }

    private fun buildEmptyState(): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(48f)
                bottomMargin = dpToPx(48f)
            }
            addView(TextView(activity).apply {
                text = activity.getString(R.string.no_music_selected)
                textSize = 18f
                setTextColor(onSurfaceColor)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(activity).apply {
                text = activity.getString(R.string.no_music_hint)
                textSize = 14f
                setTextColor(onSurfaceVariantColor)
                alpha = 0.7f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpToPx(8f) }
            })
        }
    }

    private fun buildCompactMusicHeader(music: PickedMusic): LinearLayout {
        val radius = dpToPx(16f).toFloat()
        
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = OptimizeEngine.roundedBackground(surfaceBrightColor, radius)
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(16f)
            }
            isClickable = true
            isFocusable = true
        }

        val avatarSize = dpToPx(64f)
        row.addView(ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(music.albumArt)
            background = OptimizeEngine.roundedBackground(surfaceBrightColor, dpToPx(12f).toFloat())
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                marginEnd = dpToPx(16f)
            }
        })

        val textCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(activity).apply {
            text = music.title
            textSize = 20f
            setTextColor(onSurfaceColor)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        textCol.addView(TextView(activity).apply {
            text = music.artist
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            alpha = 0.8f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpToPx(2f) }
        })
        row.addView(textCol)

        return row
    }

    private fun buildTallLayout(music: PickedMusic): ScrollView {
        val isCompact = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getBoolean("compact_music_info", false)

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val inner = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        if (isCompact) {
            inner.addView(buildCompactMusicHeader(music))
        } else {
            inner.addView(buildArtwork(music, forceSquare = true))
        }

        if (!music.lyrics.isNullOrBlank()) {
            inner.addView(buildLyricsList(music, isWide = false, isCompact = isCompact))
        }

        scroll.addView(inner)
        return scroll
    }

    private fun buildWideLayout(music: PickedMusic): ScrollView {
        val isCompact = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getBoolean("compact_music_info", false)
            
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }

        if (isCompact) {
            val inner = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            inner.addView(buildCompactMusicHeader(music))
            
            if (!music.lyrics.isNullOrBlank()) {
                inner.addView(buildLyricsList(music, isWide = true, isCompact = true))
            }
            scroll.addView(inner)
            return scroll
        }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val leftCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        leftCol.addView(buildArtwork(music, forceSquare = false))
        row.addView(leftCol)

        val rightCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(20f)
            }
        }

        if (!music.lyrics.isNullOrBlank()) {
            rightCol.addView(buildLyricsList(music, isWide = true, isCompact = false))
        }

        row.addView(rightCol)
        scroll.addView(row)
        return scroll
    }

    private fun buildArtwork(music: PickedMusic, forceSquare: Boolean): View {
        val radiusPx = dpToPx(20f).toFloat()
        
        val container = FrameLayout(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(surfaceBrightColor)
            }
            clipToOutline = true
        }

        container.addView(ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(music.albumArt)
        })

        container.addView(View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(120f)).apply {
                gravity = Gravity.BOTTOM
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT)
            )
        })

        val textOverlay = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(24f)
                marginStart = dpToPx(16f)
                marginEnd = dpToPx(16f)
            }
        }

        textOverlay.addView(TextView(activity).apply {
            text = music.title
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 3f, Color.BLACK)
        })

        // ★ 絕對修復：這裡只顯示歌手，不再呼叫 buildSubtitle
        textOverlay.addView(TextView(activity).apply {
            text = music.artist 
            textSize = 16f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(4f)
            }
        })

        container.addView(textOverlay)

        if (forceSquare) {
            return object : FrameLayout(activity) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    super.onMeasure(widthMeasureSpec, widthMeasureSpec)
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                addView(container, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT))
            }
        } else {
            container.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(320f))
            return container
        }
    }

    private fun buildLyricsList(music: PickedMusic, isWide: Boolean, isCompact: Boolean): LinearLayout {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = if (isWide || isCompact) 0 else dpToPx(24f)
            }
        }

        val rawLyrics = music.lyrics ?: return container
        val lines = rawLyrics.split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return container

        val showTime = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getBoolean("show_lyrics_time", true)

        val outerR = dpToPx(16f).toFloat()
        val innerR = dpToPx(4f).toFloat()
        val total = lines.size

        val timeRegex = Regex("^\\[\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?\\]")

        lines.forEachIndexed { index, line ->
            val topR = if (index == 0) outerR else innerR
            val bottomR = if (index == total - 1) outerR else innerR
            
            val contentBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(topR, topR, topR, topR, bottomR, bottomR, bottomR, bottomR)
                setColor(surfaceBrightColor)
            }
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(topR, topR, topR, topR, bottomR, bottomR, bottomR, bottomR)
                setColor(Color.WHITE)
            }
            val rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurfaceVariantColor, 40))
            val ripple = RippleDrawable(rippleColor, contentBg, mask)

            var fullText = line
            var timeTag = ""
            val match = timeRegex.find(fullText)
            if (match != null) {
                timeTag = match.value
                fullText = fullText.replaceFirst(timeTag, "").trimStart()
            }

            val parts = fullText.split("\u0001")
            val originalText = parts.getOrNull(0) ?: ""
            val translationText = parts.getOrNull(1)
            val hasTranslationForThisRow = !translationText.isNullOrBlank()

            val vPad = if (hasTranslationForThisRow) 12 else 16

            val rowContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = ripple
                isClickable = true
                isFocusable = true
                setPadding(dpToPx(20f), dpToPx(vPad.toFloat()), dpToPx(20f), dpToPx(vPad.toFloat()))
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (index < total - 1) bottomMargin = dpToPx(2f)
                }
            }

            val textRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP or Gravity.CENTER_VERTICAL
            }

            textRow.addView(TextView(activity).apply {
                text = originalText
                textSize = 15f
                setTextColor(onSurfaceColor)
                setLineSpacing(dpToPx(4f).toFloat(), 1f)
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            if (showTime && timeTag.isNotEmpty()) {
                textRow.addView(TextView(activity).apply {
                    text = timeTag
                    textSize = 13f
                    setTextColor(onSurfaceVariantColor)
                    alpha = 0.7f
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dpToPx(12f)
                    }
                })
            }

            rowContainer.addView(textRow)

            if (hasTranslationForThisRow) {
                rowContainer.addView(TextView(activity).apply {
                    text = translationText
                    textSize = 14f
                    setTextColor(onSurfaceVariantColor)
                    alpha = 0.8f
                    setLineSpacing(dpToPx(4f).toFloat(), 1f)
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dpToPx(4f)
                    }
                })
            }

            container.addView(rowContainer)
        }

        return container
    }
}