package app.olauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Reports a horizontal swipe to the left, even when it starts on a child
 * such as a widget. Vertical drags are left to the children.
 */
class SwipeLeftLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var onSwipeLeft: () -> Unit = {}

    /** Right swipes are only taken from the children when this is set. */
    var onSwipeRight: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeDistance = 64 * resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = downX - event.x
                val horizontal = abs(dx) > touchSlop * 2 && abs(dx) > abs(event.y - downY) * 2
                return horizontal && (dx > 0 || onSwipeRight != null)
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }

            MotionEvent.ACTION_UP -> when {
                downX - event.x > swipeDistance -> onSwipeLeft()
                event.x - downX > swipeDistance -> onSwipeRight?.invoke()
            }
        }
        return true
    }
}
