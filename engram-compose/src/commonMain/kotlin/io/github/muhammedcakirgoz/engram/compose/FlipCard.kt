package io.github.muhammedcakirgoz.engram.compose

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Which way a [FlipCard] turns over. */
public enum class FlipAxis { Horizontal, Vertical }

/**
 * A card that turns over in 3D between a [front] and a [back] face.
 *
 * The flip state is hoisted: pass [flipped] and change it in [onClick].
 *
 * ```
 * var flipped by remember { mutableStateOf(false) }
 * FlipCard(
 *     flipped = flipped,
 *     onClick = { flipped = !flipped },
 *     front = { Text("apple") },
 *     back = { Text("elma") },
 * )
 * ```
 */
@Composable
public fun FlipCard(
    flipped: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    axis: FlipAxis = FlipAxis.Horizontal,
    durationMillis: Int = 450,
    shape: Shape = RoundedCornerShape(24.dp),
    front: @Composable BoxScope.() -> Unit,
    back: @Composable BoxScope.() -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        label = "flip",
    )
    val showingBack = rotation > 90f
    val interaction = remember { MutableInteractionSource() }

    Card(
        modifier = modifier
            .graphicsLayer {
                if (axis == FlipAxis.Horizontal) rotationY = rotation else rotationX = rotation
                cameraDistance = 12f * density
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics {
                stateDescription = if (flipped) "Back side" else "Front side"
                if (onClick != null) onClick(label = "Flip card") { onClick(); true }
            },
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Un-mirror the back face so its content reads correctly.
                    if (showingBack) {
                        if (axis == FlipAxis.Horizontal) rotationY = 180f else rotationX = 180f
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (showingBack) back() else front()
        }
    }
}
