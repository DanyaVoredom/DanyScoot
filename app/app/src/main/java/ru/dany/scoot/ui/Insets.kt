package ru.dany.scoot.ui

import android.graphics.Rect
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

fun View.applyEdgeToEdgeInsets(hideOnIme: View? = null) {
    var pending = false

    fun scheduleScroll() {
        if (pending) return
        pending = true
        post {
            pending = false
            scrollFocusedIntoView()
        }
    }

    fun apply(insets: WindowInsetsCompat) {
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
        setPadding(
            cutout.left,
            max(bars.top, cutout.top),
            cutout.right,
            if (imeVisible) max(bars.bottom, ime.bottom) else bars.bottom,
        )
        hideOnIme?.visibility = if (imeVisible) View.GONE else View.VISIBLE
        if (imeVisible) scheduleScroll()
    }

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        apply(insets)
        insets
    }

    ViewCompat.setWindowInsetsAnimationCallback(
        this,
        object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>,
            ): WindowInsetsCompat {
                apply(insets)
                return insets
            }
        },
    )
}

fun View.scrollFocusedIntoView() {
    val focused = findFocus() ?: return
    if (!focused.isShown) return
    val rect = Rect()
    focused.getDrawingRect(rect)
    focused.requestRectangleOnScreen(rect, false)
}