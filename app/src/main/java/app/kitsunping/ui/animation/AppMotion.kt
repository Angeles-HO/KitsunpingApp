package app.kitsunping.ui.animation

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** Centralized motion specs used across the app for consistent transitions. */
object AppMotion {
    const val TAB_ENTER_MS = 340
    const val TAB_EXIT_MS = 260
    const val TAB_FADE_IN_MS = 260
    const val TAB_FADE_OUT_MS = 220

    const val VISIBILITY_ENTER_MS = 340
    const val VISIBILITY_EXIT_MS = 260
    const val VISIBILITY_FADE_IN_MS = 260
    const val VISIBILITY_FADE_OUT_MS = 220

    const val CONTENT_SIZE_MS = 280

    private fun duration(ms: Int, reducedMotionEnabled: Boolean): Int {
        return if (reducedMotionEnabled) 1 else ms
    }

    fun tabEnterOffsetX(fullWidth: Int, reducedMotionEnabled: Boolean, forward: Boolean): Int {
        if (reducedMotionEnabled) return 0
        return if (forward) fullWidth / 5 else -fullWidth / 5
    }

    fun tabExitOffsetX(fullWidth: Int, reducedMotionEnabled: Boolean, forward: Boolean): Int {
        if (reducedMotionEnabled) return 0
        return if (forward) -fullWidth / 8 else fullWidth / 8
    }

    fun tabSlideInSpec(reducedMotionEnabled: Boolean = false) = tween<IntOffset>(
        durationMillis = duration(TAB_ENTER_MS, reducedMotionEnabled),
        easing = FastOutSlowInEasing
    )

    fun tabSlideOutSpec(reducedMotionEnabled: Boolean = false) = tween<IntOffset>(
        durationMillis = duration(TAB_EXIT_MS, reducedMotionEnabled),
        easing = FastOutLinearInEasing
    )

    fun tabFadeInSpec(reducedMotionEnabled: Boolean = false) = tween<Float>(
        durationMillis = duration(TAB_FADE_IN_MS, reducedMotionEnabled),
        easing = LinearOutSlowInEasing
    )

    fun tabFadeOutSpec(reducedMotionEnabled: Boolean = false) = tween<Float>(
        durationMillis = duration(TAB_FADE_OUT_MS, reducedMotionEnabled),
        easing = FastOutLinearInEasing
    )

    fun visibilityExpandSpec(reducedMotionEnabled: Boolean = false) = tween<IntSize>(
        durationMillis = duration(VISIBILITY_ENTER_MS, reducedMotionEnabled),
        easing = FastOutSlowInEasing
    )

    fun visibilityShrinkSpec(reducedMotionEnabled: Boolean = false) = tween<IntSize>(
        durationMillis = duration(VISIBILITY_EXIT_MS, reducedMotionEnabled),
        easing = FastOutLinearInEasing
    )

    fun visibilityFadeInSpec(reducedMotionEnabled: Boolean = false) = tween<Float>(
        durationMillis = duration(VISIBILITY_FADE_IN_MS, reducedMotionEnabled),
        easing = LinearOutSlowInEasing
    )

    fun visibilityFadeOutSpec(reducedMotionEnabled: Boolean = false) = tween<Float>(
        durationMillis = duration(VISIBILITY_FADE_OUT_MS, reducedMotionEnabled),
        easing = FastOutLinearInEasing
    )

    fun contentSizeSpec(reducedMotionEnabled: Boolean = false) = tween<IntSize>(
        durationMillis = duration(CONTENT_SIZE_MS, reducedMotionEnabled),
        easing = FastOutSlowInEasing
    )
}
