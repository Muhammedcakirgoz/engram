package io.github.muhammedcakirgoz.engram.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.muhammedcakirgoz.engram.fsrs.Rating
import io.github.muhammedcakirgoz.engram.fsrs.toShortLabel
import kotlin.time.Duration

/** Colors and labels for the four rating buttons. */
@Immutable
public data class RatingStyle(
    val labels: Map<Rating, String> = mapOf(
        Rating.Again to "Again",
        Rating.Hard to "Hard",
        Rating.Good to "Good",
        Rating.Easy to "Easy",
    ),
    val colors: Map<Rating, Color> = mapOf(
        Rating.Again to Color(0xFFE5484D),
        Rating.Hard to Color(0xFFF59E0B),
        Rating.Good to Color(0xFF30A46C),
        Rating.Easy to Color(0xFF3E63DD),
    ),
    val contentColor: Color = Color.White,
)

/**
 * The four FSRS rating buttons. When [intervals] is given, each button also shows when the card
 * would come back (`1m`, `10m`, `2d`, …), like Anki.
 *
 * @param intervals typically `session.intervals`, or computed from `Scheduler.preview`.
 * @param ratings which buttons to show; pass `listOf(Rating.Again, Rating.Good)` for a simpler two-button mode.
 */
@Composable
public fun RatingBar(
    onRate: (Rating) -> Unit,
    modifier: Modifier = Modifier,
    intervals: Map<Rating, Duration>? = null,
    ratings: List<Rating> = Rating.entries,
    style: RatingStyle = RatingStyle(),
    enabled: Boolean = true,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (rating in ratings) {
            val color = style.colors.getValue(rating)
            Button(
                onClick = { onRate(rating) },
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = style.contentColor),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    intervals?.get(rating)?.let {
                        Text(it.toShortLabel(), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(style.labels.getValue(rating), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

