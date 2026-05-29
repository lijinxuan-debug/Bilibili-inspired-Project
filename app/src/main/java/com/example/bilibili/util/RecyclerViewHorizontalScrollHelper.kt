package com.example.bilibili.util

import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewParent
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * 横向 RecyclerView 嵌套在 ViewPager2（或其它会拦截横滑的手势父布局）时，
 * 快速滑动容易被子 View 抢走事件。在判定为横向滑动且列表仍可滚动时禁止父布局拦截。
 */
fun RecyclerView.fixHorizontalScrollConflictWithParent() {
    addOnItemTouchListener(HorizontalParentScrollTouchListener())
}

private class HorizontalParentScrollTouchListener : RecyclerView.SimpleOnItemTouchListener() {

    private var initialX = 0f
    private var initialY = 0f
    private var touchSlop = 0
    private var disallowParent = false

    override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
        if (touchSlop == 0) {
            touchSlop = ViewConfiguration.get(rv.context).scaledTouchSlop
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y
                disallowParent = rv.canScrollHorizontally(-1) || rv.canScrollHorizontally(1)
                requestParentDisallowIntercept(rv, disallowParent)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - initialX
                val dy = event.y - initialY
                if (abs(dx) < touchSlop && abs(dy) < touchSlop) {
                    return false
                }
                if (abs(dx) > abs(dy)) {
                    val scrollingToStart = dx > 0
                    val canScroll = if (scrollingToStart) {
                        rv.canScrollHorizontally(-1)
                    } else {
                        rv.canScrollHorizontally(1)
                    }
                    disallowParent = canScroll
                    requestParentDisallowIntercept(rv, canScroll)
                } else {
                    disallowParent = false
                    requestParentDisallowIntercept(rv, false)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (disallowParent) {
                    requestParentDisallowIntercept(rv, false)
                }
                disallowParent = false
            }
        }
        return false
    }

    private fun requestParentDisallowIntercept(child: RecyclerView, disallow: Boolean) {
        var parent: ViewParent? = child.parent
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow)
            parent = parent.parent
        }
    }
}
