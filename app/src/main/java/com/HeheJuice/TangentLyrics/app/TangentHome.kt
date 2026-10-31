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
 
package com.HeheJuice.TangentLyrics.app

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import com.HeheJuice.TangentLyrics.R
import com.HeheJuice.TangentLyrics.data.MusicSession
import com.HeheJuice.TangentLyrics.data.PickedMusic
import com.HeheJuice.TangentLyrics.ui.HomePageContent
import com.HeheJuice.TangentLyrics.ui.SettingsPageContent
import com.HeheJuice.TangentLyrics.ui.UpdatePageContent
import com.HeheJuice.TangentLyrics.util.AppFont
import com.HeheJuice.TangentLyrics.util.CardGenerator
import com.HeheJuice.TangentLyrics.util.LyricsCardGenerator
import com.HeheJuice.TangentLyrics.util.MonetColorManager
import com.HeheJuice.TangentLyrics.util.MusicLoader
import com.HeheJuice.TangentLyrics.util.OptimizeEngine
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TangentHome : AppCompatActivity() {

    enum class AppTab { HOME, SETTINGS, UPDATER }
    private var activeTab = AppTab.HOME

    private lateinit var slidingPillView: View
    private lateinit var homeTabBtn: TextView
    private lateinit var settingsTabBtn: TextView
    private lateinit var updaterTabBtn: TextView
    private lateinit var homeContainer: FrameLayout
    private lateinit var settingsContainer: FrameLayout
    private lateinit var updaterContainer: FrameLayout
    private lateinit var contentHolder: FrameLayout
    private lateinit var bottomBarLayout: LinearLayout
    private lateinit var tabPillContainer: FrameLayout
    private lateinit var rootFrame: FrameLayout

    private lateinit var plusBtnRef: FrameLayout
    private lateinit var plusBtnIcon: ImageView
    private lateinit var plusIconDrawable: PlusDrawable
    
    private var isUploadState = false
    private var pendingLyrics: String? = null

    private lateinit var homePage: HomePageContent
    private lateinit var settingsPage: SettingsPageContent
    private lateinit var updaterPageContent: UpdatePageContent

    private var primaryContainerColor: Int = 0
    private var onPrimaryContainerColor: Int = 0
    private var surfaceContainerColor: Int = 0
    private var surfaceContainerLowColor: Int = 0
    private var surfaceBrightColor: Int = 0
    private var surfaceContainerHighColor: Int = 0
    private var onSurfaceColor: Int = 0
    private var onSurfaceVariantColor: Int = 0

    private var currentTopInset: Int = 0

    private var updaterTapCount: Int = 0
    private val updaterTapResetHandler = Handler(Looper.getMainLooper())
    private val updaterTapResetRunnable = Runnable { updaterTapCount = 0 }

    private val feedbackTargets = mutableListOf<OptimizeEngine.FeedbackTarget>()

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) handlePickedAudio(uri) }

    private val saveCardLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? -> if (uri != null) saveCardToUri(uri) }

    private fun dpToPx(dp: Float): Int = OptimizeEngine.dpToPx(dp)
    private fun dpToPx(dp: Int): Int = OptimizeEngine.dpToPx(dp)

    private val pressScaleTouchListener get() = OptimizeEngine.pressScaleTouchListener

    private fun getAppVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) { "1.0.0" }

    private fun isWelcomeLaunched(): Boolean {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_WELCOME_LAUNCHED, false)) return false
        return prefs.getString(KEY_WELCOME_VERSION, null) == getAppVersionName()
    }

    private var welcomeLaunchGuard = false

    private fun ensureWelcomeShown() {
        if (welcomeLaunchGuard) return
        if (isWelcomeLaunched()) return
        welcomeLaunchGuard = true
        try {
            startActivity(Intent(this, WelcomeActivity::class.java))
        } catch (_: Exception) {
        } finally {
            Handler(Looper.getMainLooper()).postDelayed({ welcomeLaunchGuard = false }, 500L)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN && updaterTapCount > 0) {
            if (!isTouchOnUpdaterButton(ev)) {
                updaterTapCount = 0
                updaterTapResetHandler.removeCallbacks(updaterTapResetRunnable)
            }
        }
        for (t in feedbackTargets) t.handleEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun isTouchOnUpdaterButton(ev: MotionEvent): Boolean {
        if (!::updaterTabBtn.isInitialized) return false
        if (updaterTabBtn.width <= 0 || updaterTabBtn.height <= 0) return false
        val loc = IntArray(2)
        updaterTabBtn.getLocationOnScreen(loc)
        return ev.rawX >= loc[0] && ev.rawX < loc[0] + updaterTabBtn.width &&
                ev.rawY >= loc[1] && ev.rawY < loc[1] + updaterTabBtn.height
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            val savedTab = savedInstanceState.getString("active_tab")
            activeTab = savedTab?.let { AppTab.valueOf(it) } ?: AppTab.HOME
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        ensureWelcomeShown()
        MonetColorManager.applyToActivity(this)

        // ★ 讀取上次選擇的音樂緩存
        MusicSession.loadFromCache(this)

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark

        primaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, Color.parseColor("#E8DEF8"))
        onPrimaryContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, Color.parseColor("#4F378B"))
        surfaceContainerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer, if (isDark) Color.parseColor("#1C1B1F") else Color.parseColor("#FEF7FF"))
        surfaceContainerLowColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, if (isDark) Color.parseColor("#1D1B20") else Color.parseColor("#F7F2FA"))
        surfaceBrightColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceBright, if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF"))
        surfaceContainerHighColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, if (isDark) Color.parseColor("#2B2930") else Color.parseColor("#ECE6F0"))
        onSurfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F"))
        onSurfaceVariantColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.parseColor("#49454F"))

        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(surfaceContainerColor)
            clipChildren = false; clipToPadding = false
        }

        homeContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            visibility = if (activeTab == AppTab.HOME) View.VISIBLE else View.GONE
        }
        homePage = HomePageContent(this)
        val homeScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(dpToPx(16f), dpToPx(64f), dpToPx(16f), dpToPx(100f))
        }
        homeScroll.addView(homePage.build())
        homeContainer.addView(homeScroll)

        settingsContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            visibility = if (activeTab == AppTab.SETTINGS) View.VISIBLE else View.GONE
        }
        settingsPage = SettingsPageContent(this)
        val settingsScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(dpToPx(16f), dpToPx(64f), dpToPx(16f), dpToPx(100f))
        }
        settingsScroll.addView(settingsPage.build())
        settingsContainer.addView(settingsScroll)

        updaterContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            visibility = if (activeTab == AppTab.UPDATER) View.VISIBLE else View.GONE
        }
        val updaterScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(dpToPx(16f), dpToPx(64f), dpToPx(16f), dpToPx(100f))
        }
        updaterPageContent = UpdatePageContent(this@TangentHome)
        updaterScroll.addView(updaterPageContent.build())
        updaterContainer.addView(updaterScroll)

        contentHolder = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            clipChildren = false; clipToPadding = false
            addView(homeContainer); addView(settingsContainer); addView(updaterContainer)
        }
        rootFrame.addView(contentHolder)

        bottomBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            clipChildren = false; clipToPadding = false
        }

        tabPillContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dpToPx(100f).toFloat(); setColor(surfaceContainerLowColor)
            }
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
            clipChildren = false; clipToPadding = false
        }

        slidingPillView = View(this).apply {
            background = GradientDrawable().apply { setColor(primaryContainerColor); cornerRadius = dpToPx(100f).toFloat() }
            layoutParams = FrameLayout.LayoutParams(0, dpToPx(44f), Gravity.CENTER_VERTICAL)
        }

        val tabButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }

        val homeDrawable = try { ContextCompat.getDrawable(this, R.drawable.lyrics_24px) } catch (_: Exception) { null }
        val settingsDrawable = try { ContextCompat.getDrawable(this, R.drawable.settings_24px) } catch (_: Exception) { null }
        val updaterDrawable = try { ContextCompat.getDrawable(this, R.drawable.info_24px) } catch (_: Exception) { null }

        fun makeTabBtn(drawable: Drawable?, labelRes: Int, tab: AppTab): TextView {
            return TextView(this).apply {
                text = if (activeTab == tab) getString(labelRes) else ""
                textSize = 14f
                setTextColor(if (activeTab == tab) onPrimaryContainerColor else onSurfaceVariantColor)
                gravity = Gravity.CENTER
                setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
                compoundDrawablePadding = if (activeTab == tab) dpToPx(8f) else 0
                setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(44f))
                isClickable = true; isFocusable = true
                setOnTouchListener(pressScaleTouchListener)
                includeFontPadding = false
            }
        }

        homeTabBtn = makeTabBtn(homeDrawable, R.string.nav_home, AppTab.HOME)
        settingsTabBtn = makeTabBtn(settingsDrawable, R.string.nav_settings, AppTab.SETTINGS)
        updaterTabBtn = makeTabBtn(updaterDrawable, R.string.nav_updater, AppTab.UPDATER)

        tabButtonsLayout.addView(homeTabBtn)
        tabButtonsLayout.addView(settingsTabBtn)
        tabButtonsLayout.addView(updaterTabBtn)
        tabPillContainer.addView(slidingPillView)
        tabPillContainer.addView(tabButtonsLayout)

        plusIconDrawable = PlusDrawable(onSurfaceVariantColor, dpToPx(3f).toFloat())
        
        plusBtnRef = FrameLayout(this).apply {
            background = OptimizeEngine.circleBackground(surfaceContainerLowColor)
            clipChildren = false; clipToPadding = false
            isClickable = true; isFocusable = true
            contentDescription = getString(R.string.add_music_content_desc)
            visibility = if (activeTab == AppTab.HOME) View.VISIBLE else View.GONE
            alpha = if (activeTab == AppTab.HOME) 1f else 0f
            isEnabled = activeTab == AppTab.HOME
            layoutParams = LinearLayout.LayoutParams(if (activeTab == AppTab.HOME) dpToPx(52f) else 0, dpToPx(52f)).apply {
                marginStart = if (activeTab == AppTab.HOME) dpToPx(12f) else 0
            }
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@TangentHome)
                if (isUploadState) {
                    showUploadOptionsDialog()
                } else {
                    pickAudioLauncher.launch(arrayOf("audio/*"))
                }
            }
        }

        plusBtnIcon = ImageView(this).apply {
            setImageDrawable(plusIconDrawable)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(14f)
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER
            }
        }
        plusBtnRef.addView(plusBtnIcon)

        bottomBarLayout.addView(tabPillContainer)
        bottomBarLayout.addView(plusBtnRef)
        rootFrame.addView(bottomBarLayout)
        feedbackTargets += OptimizeEngine.FeedbackTarget(bottomBarLayout)

        setContentView(rootFrame)
        AppFont.apply(rootFrame, this)

        val fadePrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (fadePrefs.getBoolean(KEY_PENDING_LANG_FADE, false)) {
            fadePrefs.edit().putBoolean(KEY_PENDING_LANG_FADE, false).apply()
            playRestartFadeInAnimation()
        }

        val tintDrawableColor: (TextView, Int) -> Unit = { tv, color ->
            val d = tv.compoundDrawables[0]?.let {
                val w = DrawableCompat.wrap(it).mutate(); DrawableCompat.setTint(w, color); w
            }
            tv.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null)
        }
        tintDrawableColor(homeTabBtn, if (activeTab == AppTab.HOME) onPrimaryContainerColor else onSurfaceVariantColor)
        tintDrawableColor(settingsTabBtn, if (activeTab == AppTab.SETTINGS) onPrimaryContainerColor else onSurfaceVariantColor)
        tintDrawableColor(updaterTabBtn, if (activeTab == AppTab.UPDATER) onPrimaryContainerColor else onSurfaceVariantColor)

        val tabTransitionInterpolator = android.view.animation.AnimationUtils.loadInterpolator(this, android.R.interpolator.fast_out_extra_slow_in)
        var isTabSwitchAnimating = false

        fun switchTab(targetTab: AppTab) {
            if (activeTab == targetTab || isTabSwitchAnimating) return
            isTabSwitchAnimating = true
            val oldTab = activeTab
            activeTab = targetTab

            TransitionManager.beginDelayedTransition(bottomBarLayout, TransitionSet().apply {
                addTransition(ChangeBounds()); addTransition(Fade())
                duration = 250L; interpolator = OvershootInterpolator(1.0f)
            })

            fun updateTabStyle(btn: TextView, tab: AppTab, isTarget: Boolean) {
                btn.text = if (isTarget) when (tab) {
                    AppTab.HOME -> getString(R.string.nav_home)
                    AppTab.SETTINGS -> getString(R.string.nav_settings)
                    AppTab.UPDATER -> getString(R.string.nav_updater)
                } else ""
                btn.compoundDrawablePadding = if (isTarget) dpToPx(8f) else 0
                btn.setTextColor(if (isTarget) onPrimaryContainerColor else onSurfaceVariantColor)
                tintDrawableColor(btn, if (isTarget) onPrimaryContainerColor else onSurfaceVariantColor)
            }
            updateTabStyle(homeTabBtn, AppTab.HOME, targetTab == AppTab.HOME)
            updateTabStyle(settingsTabBtn, AppTab.SETTINGS, targetTab == AppTab.SETTINGS)
            updateTabStyle(updaterTabBtn, AppTab.UPDATER, targetTab == AppTab.UPDATER)

            val plusLp = plusBtnRef.layoutParams as LinearLayout.LayoutParams
            if (targetTab == AppTab.HOME) {
                plusLp.width = dpToPx(52f); plusLp.marginStart = dpToPx(12f)
                plusBtnRef.alpha = 1f; plusBtnRef.isEnabled = true; plusBtnRef.visibility = View.VISIBLE
            } else {
                plusLp.width = 0; plusLp.marginStart = 0
                plusBtnRef.alpha = 0f; plusBtnRef.isEnabled = false; plusBtnRef.visibility = View.GONE
            }
            plusBtnRef.layoutParams = plusLp

            updatePillPosition()

            val incoming = when (targetTab) { AppTab.HOME -> homeContainer; AppTab.SETTINGS -> settingsContainer; AppTab.UPDATER -> updaterContainer }
            val outgoing = when (oldTab) { AppTab.HOME -> homeContainer; AppTab.SETTINGS -> settingsContainer; AppTab.UPDATER -> updaterContainer }

            val dist = contentHolder.width.takeIf { it > 0 }?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
            val movingRight = targetTab.ordinal > oldTab.ordinal
            val startX = if (movingRight) dist else -dist

            incoming.animate().cancel(); outgoing.animate().cancel()
            incoming.visibility = View.VISIBLE; incoming.alpha = 1f; incoming.translationX = startX
            outgoing.alpha = 1f; outgoing.translationX = 0f

            // ★ 修復低端機動畫丟失問題：強制使用硬體加速圖層 (withLayer)
            contentHolder.post {
                val dur = 450L
                incoming.animate()
                    .translationX(0f)
                    .setDuration(dur)
                    .setInterpolator(tabTransitionInterpolator)
                    .withLayer()
                    .start()
                    
                outgoing.animate()
                    .translationX(-startX)
                    .setDuration(dur)
                    .setInterpolator(tabTransitionInterpolator)
                    .withLayer()
                    .withEndAction {
                        outgoing.visibility = View.GONE; outgoing.translationX = 0f; incoming.translationX = 0f
                        isTabSwitchAnimating = false
                    }
                    .start()
            }
        }

        homeTabBtn.setOnClickListener { OptimizeEngine.vibrateThud(this); switchTab(AppTab.HOME) }
        settingsTabBtn.setOnClickListener { OptimizeEngine.vibrateThud(this); switchTab(AppTab.SETTINGS) }
        updaterTabBtn.setOnClickListener {
            OptimizeEngine.vibrateThud(this)
            switchTab(AppTab.UPDATER)
            updaterTapCount++
            updaterTapResetHandler.removeCallbacks(updaterTapResetRunnable)
            if (updaterTapCount >= UPDATER_TAP_TARGET) updaterTapCount = 0
            else updaterTapResetHandler.postDelayed(updaterTapResetRunnable, UPDATER_TAP_TIMEOUT_MS)
        }

        rootFrame.setOnApplyWindowInsetsListener { _, insets ->
            val statusTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) insets.getInsets(android.view.WindowInsets.Type.statusBars()).top else @Suppress("DEPRECATION") insets.systemWindowInsetTop
            val navBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom else @Suppress("DEPRECATION") insets.systemWindowInsetBottom

            currentTopInset = statusTop
            contentHolder.setPadding(0, currentTopInset, 0, 0)
            (bottomBarLayout.layoutParams as FrameLayout.LayoutParams).bottomMargin = dpToPx(16f) + navBottom
            insets
        }

        rootFrame.post { updatePillPosition() }

        lifecycleScope.launch {
            (application as EngineStart).monetVersion.drop(1).collect {
                if (!isFinishing && !isDestroyed) recreate()
            }
        }

        // ★ 處理透過 Share 傳入的音樂
        handleSharedIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        ensureWelcomeShown()
        // ★ 處理在背景時收到的 Share
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW) {
            val uri: Uri? = if (intent.action == Intent.ACTION_SEND) {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } else {
                intent.data
            }
            
            if (uri != null) {
                // 確保拿到持久化權限
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) { }

                // 如果當前播放的音樂跟分享的不是同一個，就加載它
                if (MusicSession.current?.uri != uri.toString()) {
                    handlePickedAudio(uri)
                }
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        homePage.refreshStatusIcons()
        if (::settingsPage.isInitialized) settingsPage.refreshStatusIcons()

        updateUploadState(MusicSession.current != null, animate = false)

        if (::updaterPageContent.isInitialized) updaterPageContent.refreshIfNeeded()
    }

    override fun onDestroy() {
        updaterTapResetHandler.removeCallbacks(updaterTapResetRunnable)
        feedbackTargets.forEach { it.destroy() }
        feedbackTargets.clear()
        if (::updaterPageContent.isInitialized) updaterPageContent.destroy()
        if (::homePage.isInitialized) homePage.destroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("active_tab", activeTab.name)
    }

    fun animateFadeToBlack(onEnd: () -> Unit) {
        val overlayView = View(this).apply { setBackgroundColor(Color.BLACK); alpha = 0f; isClickable = true }
        rootFrame.addView(overlayView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayView.bringToFront()

        val totalDuration = 500L
        val fadeCurve = FastOutSlowInInterpolator()

        contentHolder.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(totalDuration).setInterpolator(fadeCurve).start()
        bottomBarLayout.animate().translationY(dpToPx(150f).toFloat()).scaleX(0.6f).scaleY(0.6f).setDuration(totalDuration).setInterpolator(android.view.animation.AnticipateInterpolator(4f)).start()
        overlayView.animate().alpha(1f).setStartDelay(200L).setDuration(totalDuration - 200L).setInterpolator(fadeCurve).withEndAction { onEnd() }.start()
    }

    private fun playRestartFadeInAnimation() {
        val enterInterpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        contentHolder.alpha = 0f; contentHolder.scaleX = 0.85f; contentHolder.scaleY = 0.85f
        contentHolder.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(1700L).setInterpolator(enterInterpolator).start()

        bottomBarLayout.translationY = dpToPx(250f).toFloat()
        bottomBarLayout.animate().translationY(0f).setDuration(1200L).setInterpolator(OvershootInterpolator(1.4f)).start()

        if (::plusBtnRef.isInitialized) {
            plusBtnRef.rotation = -360f
            plusBtnRef.animate().rotation(0f).setStartDelay(400L).setDuration(700L).setInterpolator(OvershootInterpolator(1.5f)).start()
        }
    }

    private fun updatePillPosition() {
        if (!::slidingPillView.isInitialized) return
        val specW = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val specH = View.MeasureSpec.makeMeasureSpec(dpToPx(44f), View.MeasureSpec.EXACTLY)
        val tabs = arrayOf(homeTabBtn, settingsTabBtn, updaterTabBtn)
        var targetW = 0; var targetX = 0; var accX = 0
        tabs.forEachIndexed { idx, btn ->
            btn.measure(specW, specH)
            val w = if (btn.visibility == View.GONE) 0 else btn.measuredWidth
            if (idx == activeTab.ordinal) { targetW = w; targetX = accX }
            accX += w
        }
        val lp = slidingPillView.layoutParams as FrameLayout.LayoutParams
        if (lp.width != targetW || lp.leftMargin != targetX) {
            lp.width = targetW; lp.leftMargin = targetX; slidingPillView.layoutParams = lp
        }
    }

    private fun updateUploadState(isUpload: Boolean, animate: Boolean = true) {
        if (isUploadState == isUpload && plusBtnIcon.drawable != null) return
        
        isUploadState = isUpload
        val targetDrawable: Drawable? = if (isUpload) {
            ContextCompat.getDrawable(this, R.drawable.upload_24px)?.mutate()?.apply {
                setTint(onSurfaceVariantColor)
            }
        } else {
            plusIconDrawable
        }

        if (!animate) {
            plusBtnIcon.setImageDrawable(targetDrawable)
            plusBtnIcon.translationX = 0f
            plusBtnIcon.alpha = 1f
            return
        }

        val goingForward = isUpload
        val outTranslation = if (goingForward) -dpToPx(30f).toFloat() else dpToPx(30f).toFloat()
        val inTranslation = if (goingForward) dpToPx(30f).toFloat() else -dpToPx(30f).toFloat()

        val duration = 180L

        plusBtnIcon.animate().cancel()
        plusBtnIcon.animate()
            .translationX(outTranslation)
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                plusBtnIcon.setImageDrawable(targetDrawable)
                plusBtnIcon.translationX = inTranslation
                plusBtnIcon.alpha = 0f
                plusBtnIcon.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(duration)
                    .setInterpolator(OvershootInterpolator(1.5f))
                    .start()
            }
            .start()
    }

    private fun handlePickedAudio(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }

        Toast.makeText(this, getString(R.string.loading_music), Toast.LENGTH_SHORT).show()

        Thread {
            val music = try { MusicLoader.load(this, uri) } catch (e: Exception) { null }
            runOnUiThread {
                if (music == null) {
                    Toast.makeText(this, getString(R.string.failed_load_music), Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                MusicSession.set(this, music)
                if (!isFinishing && !isDestroyed) {
                    homePage.setMusic(music)
                    updateUploadState(isUpload = true, animate = true)
                }
            }
        }.start()
    }

    private fun showSaveFilePicker(lyrics: String?) {
        pendingLyrics = lyrics
        val music = MusicSession.current ?: return
        val safeTitle = music.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(20)
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "tangent_lyrics_${safeTitle}_$dateStr"
        saveCardLauncher.launch(fileName)
    }

    private fun saveCardToUri(uri: Uri) {
        val music = MusicSession.current ?: return
        Toast.makeText(this, getString(R.string.generating_card), Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val bitmap = if (pendingLyrics == null) {
                    CardGenerator.generateCard(this, music)
                } else {
                    LyricsCardGenerator.generateCard(this, music, pendingLyrics!!)
                }
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                runOnUiThread { Toast.makeText(this, getString(R.string.card_saved), Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this, getString(R.string.card_save_failed), Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun showUploadOptionsDialog() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val surfaceHigh = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, if (isDark) Color.parseColor("#2B2930") else Color.parseColor("#ECE6F0"))
        val rowBg = surfaceBrightColor
        val primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, if (isDark) Color.parseColor("#D0BCFF") else Color.parseColor("#6750A4"))
        
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dpToPx(28f).toFloat(); setColor(surfaceHigh)
            }
            clipToOutline = true
        }

        val contentContainer = FrameLayout(this)
        card.addView(contentContainer)

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
        }
        card.addView(bottomBar)

        val rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurfaceVariantColor, 40))
        val duration = 300L
        val curve = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        var currentPage = 0

        fun makeButton(text: String, isPrimary: Boolean, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                this.text = text
                textSize = 14f
                setTextColor(if (isPrimary) onPrimaryContainerColor else primary)
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dpToPx(20f), dpToPx(12f), dpToPx(20f), dpToPx(12f))
                isClickable = true; isFocusable = true
                background = if (isPrimary) {
                    RippleDrawable(rippleColor, GradientDrawable().apply { cornerRadius = dpToPx(100f).toFloat(); setColor(primaryContainerColor) }, GradientDrawable().apply { cornerRadius = dpToPx(100f).toFloat(); setColor(Color.WHITE) })
                } else {
                    RippleDrawable(rippleColor, GradientDrawable().apply { cornerRadius = dpToPx(100f).toFloat(); setColor(Color.TRANSPARENT); setStroke(dpToPx(1f), primary) }, GradientDrawable().apply { cornerRadius = dpToPx(100f).toFloat(); setColor(Color.WHITE) })
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (isPrimary) marginStart = dpToPx(8f)
                }
                setOnClickListener { onClick() }
            }
        }

        fun makeOptionRow(title: String, desc: String, isSelected: Boolean, iconRes: Int?, onClick: () -> Unit): LinearLayout {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true; isFocusable = true
                setPadding(dpToPx(20f), dpToPx(14f), dpToPx(20f), dpToPx(14f))
                setMinimumHeight(dpToPx(72f))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener { onClick() }
            }
            
            if (iconRes != null) {
                row.addView(ImageView(this).apply {
                    setImageDrawable(ContextCompat.getDrawable(this@TangentHome, iconRes))
                    layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f)).apply { marginEnd = dpToPx(16f) }
                })
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = title
                textSize = 16f
            })
            textCol.addView(TextView(this).apply {
                text = desc
                textSize = 14f
                alpha = 0.7f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpToPx(2f) }
            })
            row.addView(textCol)
            return row
        }

        lateinit var showPage1: () -> Unit
        lateinit var showPage2: () -> Unit

        fun animateToPage(targetPage: Int, render: () -> Unit) {
            if (currentPage == targetPage) return
            currentPage = targetPage
            contentContainer.animate().alpha(0f).scaleX(0.95f).scaleY(0.95f).setDuration(duration).setInterpolator(curve).withEndAction {
                render()
                AppFont.apply(card, this)
                contentContainer.alpha = 0f; contentContainer.scaleX = 0.95f; contentContainer.scaleY = 0.95f
                contentContainer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(duration).setInterpolator(curve).start()
            }.start()
        }

        showPage1 = {
            contentContainer.removeAllViews()
            bottomBar.removeAllViews()

            val page1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            page1.addView(TextView(this).apply {
                text = getString(R.string.generate_card_options_title)
                textSize = 22f; setTextColor(onSurfaceColor); setTypeface(typeface, Typeface.BOLD)
                setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(4f))
            })
            page1.addView(TextView(this).apply {
                text = getString(R.string.generate_card_options_desc)
                textSize = 14f; setTextColor(onSurfaceVariantColor)
                setPadding(dpToPx(24f), 0, dpToPx(24f), dpToPx(12f))
            })

            val listColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpToPx(16f), dpToPx(4f), dpToPx(16f), dpToPx(4f)) }
            fun makeRowBg(color: Int, topR: Float, bottomR: Float): GradientDrawable = GradientDrawable().apply { cornerRadii = floatArrayOf(topR, topR, topR, topR, bottomR, bottomR, bottomR, bottomR); setColor(color) }

            var selectedAction = 0 
            val rowRefs = mutableListOf<LinearLayout>()
            
            fun updateSelection() {
                rowRefs.forEachIndexed { idx, row ->
                    val isSelected = idx == selectedAction
                    val bgColor = if (isSelected) primaryContainerColor else rowBg
                    val topR = if (idx == 0) dpToPx(16f).toFloat() else dpToPx(4f).toFloat()
                    val botR = if (idx == rowRefs.size - 1) dpToPx(16f).toFloat() else dpToPx(4f).toFloat()
                    val content = makeRowBg(bgColor, topR, botR)
                    val mask = makeRowBg(Color.WHITE, topR, botR)
                    row.background = RippleDrawable(rippleColor, content, mask)
                    
                    val fg = if (isSelected) onPrimaryContainerColor else onSurfaceColor
                    val textCol = row.getChildAt(if (row.childCount == 2) 1 else 0) as LinearLayout
                    (textCol.getChildAt(0) as TextView).setTextColor(fg)
                    (textCol.getChildAt(1) as TextView).setTextColor(fg)
                    if (row.childCount == 2) (row.getChildAt(0) as ImageView).setColorFilter(fg, android.graphics.PorterDuff.Mode.SRC_ATOP)
                }
            }

            val genRow = makeOptionRow(getString(R.string.generate_card_generate), getString(R.string.generate_card_generate_desc), true, R.drawable.style_24px) {
                selectedAction = 0; updateSelection()
            }
            listColumn.addView(genRow)
            rowRefs.add(genRow)

            val reselRow = makeOptionRow(getString(R.string.generate_card_reselect), getString(R.string.generate_card_reselect_desc), false, R.drawable.music_note_2_24px) {
                selectedAction = 1; updateSelection()
            }
            (reselRow.layoutParams as LinearLayout.LayoutParams).topMargin = dpToPx(2f)
            listColumn.addView(reselRow)
            rowRefs.add(reselRow)

            updateSelection()
            page1.addView(listColumn)
            contentContainer.addView(page1)

            bottomBar.addView(makeButton(getString(R.string.tangent_cancel), false) { dialog.dismiss() })
            bottomBar.addView(makeButton(getString(R.string.action_select), true) {
                if (selectedAction == 1) {
                    dialog.dismiss()
                    pickAudioLauncher.launch(arrayOf("audio/*"))
                } else {
                    animateToPage(1) { showPage2() }
                }
            })
        }

        showPage2 = {
            contentContainer.removeAllViews()
            bottomBar.removeAllViews()

            val page2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            page2.addView(TextView(this).apply {
                text = getString(R.string.generate_card_lyrics_title)
                textSize = 22f; setTextColor(onSurfaceColor); setTypeface(typeface, Typeface.BOLD)
                setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(4f))
            })
            page2.addView(TextView(this).apply {
                text = getString(R.string.generate_card_lyrics_desc)
                textSize = 14f; setTextColor(onSurfaceVariantColor)
                setPadding(dpToPx(24f), 0, dpToPx(24f), dpToPx(12f))
            })

            val listColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpToPx(16f), dpToPx(4f), dpToPx(16f), dpToPx(4f)) }
            fun makeRowBg(color: Int, topR: Float, bottomR: Float): GradientDrawable = GradientDrawable().apply { cornerRadii = floatArrayOf(topR, topR, topR, topR, bottomR, bottomR, bottomR, bottomR); setColor(color) }

            var selectedAction = 0 
            val rowRefs = mutableListOf<LinearLayout>()
            
            fun updateSelection() {
                rowRefs.forEachIndexed { idx, row ->
                    val isSelected = idx == selectedAction
                    val bgColor = if (isSelected) primaryContainerColor else rowBg
                    val topR = if (idx == 0) dpToPx(16f).toFloat() else dpToPx(4f).toFloat()
                    val botR = if (idx == rowRefs.size - 1) dpToPx(16f).toFloat() else dpToPx(4f).toFloat()
                    val content = makeRowBg(bgColor, topR, botR)
                    val mask = makeRowBg(Color.WHITE, topR, botR)
                    row.background = RippleDrawable(rippleColor, content, mask)
                    
                    val fg = if (isSelected) onPrimaryContainerColor else onSurfaceColor
                    val textCol = row.getChildAt(0) as LinearLayout
                    (textCol.getChildAt(0) as TextView).setTextColor(fg)
                    (textCol.getChildAt(1) as TextView).setTextColor(fg)
                }
            }

            val lyricsRow = makeOptionRow(getString(R.string.generate_card_select_lyrics), getString(R.string.generate_card_select_lyrics_desc), true, null) {
                selectedAction = 0; updateSelection()
            }
            listColumn.addView(lyricsRow)
            rowRefs.add(lyricsRow)

            val noLyricsRow = makeOptionRow(getString(R.string.generate_card_no_lyrics), getString(R.string.generate_card_no_lyrics_desc), false, null) {
                selectedAction = 1; updateSelection()
            }
            (noLyricsRow.layoutParams as LinearLayout.LayoutParams).topMargin = dpToPx(2f)
            listColumn.addView(noLyricsRow)
            rowRefs.add(noLyricsRow)

            updateSelection()
            page2.addView(listColumn)
            contentContainer.addView(page2)

            bottomBar.addView(makeButton(getString(R.string.tangent_back), false) { animateToPage(0) { showPage1() } })
            bottomBar.addView(makeButton(getString(R.string.tangent_continue), true) {
                if (selectedAction == 0) {
                    val music = MusicSession.current
                    if (music?.lyrics.isNullOrBlank()) {
                        Toast.makeText(this@TangentHome, getString(R.string.no_lyrics_available), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        showSaveFilePicker(null)
                    } else {
                        dialog.dismiss()
                        showLyricsPickerDialog(music!!)
                    }
                } else {
                    dialog.dismiss()
                    showSaveFilePicker(null)
                }
            })
        }

        showPage1()

        dialog.setContentView(card)
        AppFont.apply(card, this)
        dialog.setCancelable(true); dialog.setCanceledOnTouchOutside(true)

        val dialogWidth = (resources.displayMetrics.widthPixels - dpToPx(48f)).coerceAtMost(dpToPx(420f))
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.5f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        dialog.show()
    }

    private fun showLyricsPickerDialog(music: PickedMusic) {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val surfaceHigh = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, if (isDark) Color.parseColor("#2B2930") else Color.parseColor("#ECE6F0"))
        val rowBg = surfaceBrightColor
        val primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, if (isDark) Color.parseColor("#D0BCFF") else Color.parseColor("#6750A4"))
        
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dpToPx(28f).toFloat(); setColor(surfaceHigh) }
            clipToOutline = true
        }

        card.addView(TextView(this).apply {
            text = getString(R.string.select_lyrics_title)
            textSize = 22f; setTextColor(onSurfaceColor); setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(4f))
        })
        card.addView(TextView(this).apply {
            text = getString(R.string.select_lyrics_desc)
            textSize = 14f; setTextColor(onSurfaceVariantColor)
            setPadding(dpToPx(24f), 0, dpToPx(24f), dpToPx(12f))
        })

        val timeRegex = Regex("\\[\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?\\]")
        val displayLines = mutableListOf<Pair<String, Boolean>>()
        music.lyrics!!.split("\n").filter { it.isNotBlank() }.forEach { line ->
            val cleanLine = line.replace(timeRegex, "").trim()
            val parts = cleanLine.split("\u0001")
            parts.forEachIndexed { index, part ->
                if (part.isNotBlank()) {
                    displayLines.add(Pair(part, index > 0))
                }
            }
        }

        val selectedIndices = mutableListOf<Int>()

        val outerRadiusPx = dpToPx(16f).toFloat()
        val innerRadiusPx = dpToPx(4f).toFloat()
        val listHorizontalPadPx = dpToPx(16f)

        val screenHeightPx = resources.displayMetrics.heightPixels
        val maxDialogHeightPx = (screenHeightPx * 0.85f).toInt()
        val reservedPx = dpToPx(210f)
        val maxListHeightPx = (maxDialogHeightPx - reservedPx).coerceAtLeast(dpToPx(120f))
        val estimatedRowHeight = dpToPx(60f) 
        val listHeightPx = minOf(displayLines.size * estimatedRowHeight + dpToPx(8f), maxListHeightPx)

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, listHeightPx)
        }
        
        val listColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(listHorizontalPadPx, dpToPx(4f), listHorizontalPadPx, dpToPx(4f))
        }
        scroll.addView(listColumn)

        data class RowRef(val container: LinearLayout, val textView: TextView, val check: ImageView, val topR: Float, val botR: Float)
        val rowRefs = mutableListOf<RowRef>()
        val rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurfaceVariantColor, 40))

        fun makeRowBg(color: Int, topR: Float, botR: Float): GradientDrawable =
            GradientDrawable().apply { cornerRadii = floatArrayOf(topR, topR, topR, topR, botR, botR, botR, botR); setColor(color) }

        fun updateSelection() {
            rowRefs.forEachIndexed { idx, ref ->
                val isSelected = selectedIndices.contains(idx)
                val content = makeRowBg(if (isSelected) primaryContainerColor else rowBg, ref.topR, ref.botR)
                val mask = makeRowBg(Color.WHITE, ref.topR, ref.botR)
                ref.container.background = RippleDrawable(rippleColor, content, mask)
                
                val fg = if (isSelected) onPrimaryContainerColor else onSurfaceColor
                ref.textView.setTextColor(fg)
                ref.check.visibility = if (isSelected) View.VISIBLE else View.GONE
            }
        }

        displayLines.forEachIndexed { index, pair ->
            val line = pair.first
            val isTranslation = pair.second

            val topR = if (index == 0) outerRadiusPx else innerRadiusPx
            val botR = if (index == displayLines.size - 1) outerRadiusPx else innerRadiusPx

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true; isFocusable = true
                setPadding(dpToPx(20f), dpToPx(16f), dpToPx(20f), dpToPx(16f))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (index < displayLines.size - 1) bottomMargin = dpToPx(2f)
                }
                setOnClickListener {
                    OptimizeEngine.vibrateTick(this@TangentHome)
                    if (selectedIndices.contains(index)) {
                        selectedIndices.remove(index)
                    } else {
                        // ★ 修改為 8 行上限
                        if (selectedIndices.size >= 8) {
                            selectedIndices.removeAt(0)
                        }
                        selectedIndices.add(index)
                    }
                    selectedIndices.sort()
                    updateSelection()
                }
            }

            if (isTranslation) {
                row.addView(ImageView(this).apply {
                    setImageDrawable(ContextCompat.getDrawable(this@TangentHome, R.drawable.translate_24px))
                    setColorFilter(onSurfaceVariantColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
                    layoutParams = LinearLayout.LayoutParams(dpToPx(16f), dpToPx(16f)).apply {
                        marginEnd = dpToPx(12f)
                    }
                })
            }

            val textView = TextView(this).apply {
                text = line
                textSize = 16f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(textView)

            val check = ImageView(this).apply {
                setImageDrawable(ContextCompat.getDrawable(this@TangentHome, R.drawable.check_24px))
                setColorFilter(onPrimaryContainerColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f)).apply { marginStart = dpToPx(12f) }
            }
            row.addView(check)

            listColumn.addView(row)
            rowRefs.add(RowRef(row, textView, check, topR, botR))
        }
        updateSelection()
        card.addView(scroll)

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
        }

        val cancelBtn = TextView(this).apply {
            text = getString(R.string.tangent_back)
            textSize = 14f
            setTextColor(primary)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(20f), dpToPx(12f), dpToPx(20f), dpToPx(12f))
            isClickable = true; isFocusable = true
            background = RippleDrawable(
                rippleColor,
                GradientDrawable().apply {
                    cornerRadius = dpToPx(100f).toFloat()
                    setColor(Color.TRANSPARENT)
                    setStroke(dpToPx(1f), primary)
                },
                GradientDrawable().apply {
                    cornerRadius = dpToPx(100f).toFloat()
                    setColor(Color.WHITE)
                }
            )
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@TangentHome)
                dialog.dismiss()
                showUploadOptionsDialog()
            }
        }
        
        val selectBtn = TextView(this).apply {
            text = getString(R.string.tangent_generate)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(20f), dpToPx(12f), dpToPx(20f), dpToPx(12f))
            isClickable = true; isFocusable = true
            background = RippleDrawable(
                rippleColor,
                GradientDrawable().apply {
                    cornerRadius = dpToPx(100f).toFloat()
                    setColor(primaryContainerColor)
                },
                GradientDrawable().apply {
                    cornerRadius = dpToPx(100f).toFloat()
                    setColor(Color.WHITE)
                }
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpToPx(8f)
            }
            setOnClickListener {
                OptimizeEngine.vibrateThud(this@TangentHome)
                dialog.dismiss()
                if (selectedIndices.isEmpty()) {
                    showSaveFilePicker(null)
                } else {
                    val finalLyrics = selectedIndices.joinToString("\n") { displayLines[it].first }
                    showSaveFilePicker(finalLyrics)
                }
            }
        }

        bottomBar.addView(cancelBtn)
        bottomBar.addView(selectBtn)
        card.addView(bottomBar)

        dialog.setContentView(card)
        AppFont.apply(card, this)
        dialog.setCancelable(true); dialog.setCanceledOnTouchOutside(true)
        val dialogW = (resources.displayMetrics.widthPixels - dpToPx(48f)).coerceAtMost(dpToPx(420f))
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.5f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(dialogW, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    private inner class PlusDrawable(
        private val colorInt: Int,
        private val strokeWidthPx: Float
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt; style = Paint.Style.STROKE; strokeWidth = strokeWidthPx; strokeCap = Paint.Cap.ROUND
        }
        override fun draw(canvas: Canvas) {
            val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY(); val size = dpToPx(9f).toFloat()
            canvas.drawLine(cx - size, cy, cx + size, cy, paint)
            canvas.drawLine(cx, cy - size, cx, cy + size, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val KEY_PENDING_LANG_FADE = "pending_language_fade_in"
        private const val KEY_WELCOME_LAUNCHED = "welcome_launched"
        private const val KEY_WELCOME_VERSION = "welcome_version"
        private const val UPDATER_TAP_TARGET = 10
        private const val UPDATER_TAP_TIMEOUT_MS = 800L
    }
}