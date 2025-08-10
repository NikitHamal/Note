package com.omgodse.notally.ai

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import com.omgodse.notally.R

class ShimmerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var shimmerView: View? = null
    private var isShimmering = false

    enum class ShimmerType {
        TEXT_LINES,
        EXTEND_LINES,
        GENERATING_TEXT
    }

    fun startShimmer(type: ShimmerType) {
        if (isShimmering) {
            stopShimmer()
        }

        val layoutResId = when (type) {
            ShimmerType.TEXT_LINES -> R.layout.shimmer_text_lines
            ShimmerType.EXTEND_LINES -> R.layout.shimmer_extend_lines
            ShimmerType.GENERATING_TEXT -> R.layout.shimmer_generating_text
        }

        shimmerView = LayoutInflater.from(context).inflate(layoutResId, this, false)
        addView(shimmerView)

        // Apply shimmer animation to all child views recursively
        applyShimmerAnimation(shimmerView!!)
        
        visibility = View.VISIBLE
        isShimmering = true
    }

    fun stopShimmer() {
        if (isShimmering) {
            removeAllViews()
            clearAnimation()
            visibility = View.GONE
            isShimmering = false
        }
    }

    private fun applyShimmerAnimation(view: View) {
        val animation = AnimationUtils.loadAnimation(context, R.anim.shimmer_animation)
        view.startAnimation(animation)
        
        // Apply to child views as well
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                // Only apply to Views that have shimmer_background
                if (child.background != null) {
                    child.startAnimation(animation)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopShimmer()
    }
}