package io.github.muhammedcakirgoz.engram.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import io.github.muhammedcakirgoz.engram.fsrs.Rating
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Which rating each swipe direction gives. `null` disables that direction. */
public data class SwipeRatings(
    val left: Rating? = Rating.Again,
    val right: Rating? = Rating.Good,
    val up: Rating? = Rating.Easy,
    val down: Rating? = null,
)

/**
 * Wraps any card so it can be swiped away Tinder-style to rate it.
 * Swipe right → [SwipeRatings.right] (Good), left → Again, up → Easy by default.
 *
 * @param enabled set to `false` until the answer is revealed, so learners can't rate blindly.
 * @param threshold fraction of the card's size the user must drag before the swipe counts.
 * @param onSwipeProgress called while dragging with the rating the release would give (or `null`),
 *   handy for tinting an overlay.
 */
@Composable
public fun SwipeableCard(
    onSwiped: (Rating) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    ratings: SwipeRatings = SwipeRatings(),
    threshold: Float = 0.3f,
    onSwipeProgress: (Rating?) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val size = remember { arrayOf(IntSize.Zero) }

    fun ratingFor(o: Offset): Rating? {
        val (w, h) = size[0].width.toFloat() to size[0].height.toFloat()
        if (w == 0f || h == 0f) return null
        return if (abs(o.x) / w >= abs(o.y) / h) {
            when {
                o.x > w * threshold -> ratings.right
                o.x < -w * threshold -> ratings.left
                else -> null
            }
        } else {
            when {
                o.y < -h * threshold -> ratings.up
                o.y > h * threshold -> ratings.down
                else -> null
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { size[0] = it }
            .graphicsLayer {
                translationX = offset.value.x
                translationY = offset.value.y
                rotationZ = if (size[0].width > 0) offset.value.x / size[0].width * 15f else 0f
            }
            .pointerInput(enabled, ratings, threshold) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        scope.launch {
                            offset.snapTo(offset.value + drag)
                            onSwipeProgress(ratingFor(offset.value))
                        }
                    },
                    onDragEnd = {
                        val rating = ratingFor(offset.value)
                        scope.launch {
                            if (rating != null) {
                                val fling = offset.value * 3f
                                offset.animateTo(fling, tween(200))
                                onSwiped(rating)
                                offset.snapTo(Offset.Zero)
                            } else {
                                offset.animateTo(Offset.Zero, spring())
                            }
                            onSwipeProgress(null)
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offset.animateTo(Offset.Zero, spring())
                            onSwipeProgress(null)
                        }
                    },
                )
            },
        content = content,
    )
}
