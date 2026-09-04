package io.kuthorx.github.home_sport

import android.app.Activity
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

internal object SystemBars {
    @Suppress("DEPRECATION")
    fun useOpaqueStatusBar(activity: Activity) {
        val window = activity.window
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = ContextCompat.getColor(activity, R.color.primary_soft)
        window.navigationBarColor = ContextCompat.getColor(activity, R.color.background)

        val enforcedEdgeToEdge = Build.VERSION.SDK_INT >= 35
        WindowCompat.setDecorFitsSystemWindows(window, !enforcedEdgeToEdge)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        if (!enforcedEdgeToEdge) return

        val decor = window.decorView as ViewGroup
        val content = activity.findViewById<View>(android.R.id.content)
        val initialPadding = intArrayOf(
            content.paddingLeft,
            content.paddingTop,
            content.paddingRight,
            content.paddingBottom,
        )
        val statusBarBackground = View(activity).apply {
            setBackgroundColor(ContextCompat.getColor(activity, R.color.primary_soft))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        decor.addView(
            statusBarBackground,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP),
        )
        ViewCompat.setOnApplyWindowInsetsListener(decor) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            statusBarBackground.layoutParams = statusBarBackground.layoutParams.apply {
                height = statusBars.top
            }
            content.setPadding(
                initialPadding[0],
                initialPadding[1] + statusBars.top,
                initialPadding[2],
                initialPadding[3] + navigationBars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(decor)
    }
}
