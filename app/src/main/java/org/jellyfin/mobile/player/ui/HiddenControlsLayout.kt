package org.jellyfin.mobile.player.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * Root of the legacy Media3 controller layout. It still exists so Media3 keeps driving show/hide and
 * timeouts (and the old helpers keep their views), but it draws nothing and never takes a touch:
 * the Nflix Compose interface is the visible player UI.
 */
class HiddenControlsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {
    init {
        setWillNotDraw(true)
    }

    override fun dispatchDraw(canvas: Canvas) = Unit

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = true

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean = false
}
