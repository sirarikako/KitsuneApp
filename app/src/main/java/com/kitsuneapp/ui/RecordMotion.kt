package com.kitsuneapp.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
internal data class RecordMotion(val scope: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

internal val LocalRecordMotion = staticCompositionLocalOf<RecordMotion?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun recordBounds(modifier: Modifier, id: String?): Modifier {
    val animation = LocalRecordMotion.current
    if (id == null || animation == null) return modifier
    return with(animation.scope) {
        modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(id),
            animatedVisibilityScope = animation.visibility,
            boundsTransform = { _, _ -> tween(280, easing = motion) },
        )
    }
}
