package io.github.muhammedcakirgoz.engram.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.muhammedcakirgoz.engram.fsrs.Card
import io.github.muhammedcakirgoz.engram.fsrs.Rating
import io.github.muhammedcakirgoz.engram.fsrs.ReviewResult
import io.github.muhammedcakirgoz.engram.fsrs.Scheduler
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * State of a review session over items of your own type [T] (a word, a question, …).
 *
 * The session shows due items one by one, schedules each answer with FSRS, and puts cards whose
 * next step is soon (e.g. a 10-minute learning step) back at the end of the queue.
 *
 * Create it with [rememberStudySessionState]. Persist results in `onReviewed`.
 */
@Stable
public class StudySessionState<T> internal constructor(
    items: List<T>,
    private val cardOf: (T) -> Card,
    private val withCard: (T, Card) -> T,
    public val scheduler: Scheduler,
    private val clock: Clock,
    private val learnAheadLimit: Duration,
    private val onReviewed: (item: T, result: ReviewResult) -> Unit,
) {
    private val queue = mutableStateListOf<T>().apply {
        val now = clock.now()
        addAll(items.filter { cardOf(it).due <= now + learnAheadLimit }.sortedBy { cardOf(it).due })
    }
    private val initialSize = queue.size

    /** Item being shown, or `null` when the session is finished. */
    public val current: T? get() = queue.firstOrNull()

    /** Whether the answer side is showing. */
    public var isRevealed: Boolean by mutableStateOf(false)
        private set

    /** Number of reviews done in this session (a card can be reviewed more than once). */
    public var reviewed: Int by mutableStateOf(0)
        private set

    /** Cards still in the queue. */
    public val remaining: Int get() = queue.size

    /** `true` when there is nothing left to review. */
    public val isFinished: Boolean get() = queue.isEmpty()

    /** Rough completion between 0 and 1, for a progress bar. */
    public val progress: Float by derivedStateOf {
        if (initialSize == 0) 1f else (1f - queue.size.toFloat() / maxOf(initialSize, queue.size)).coerceIn(0f, 1f)
    }

    /** When each rating would bring the current card back. `null` if finished. */
    public val intervals: Map<Rating, Duration>? by derivedStateOf {
        val item = current ?: return@derivedStateOf null
        val now = clock.now()
        scheduler.preview(cardOf(item), now).mapValues { (_, r) -> r.card.due - now }
    }

    /** Shows the answer side. */
    public fun reveal() {
        isRevealed = true
    }

    /** Toggles between question and answer. */
    public fun toggle() {
        isRevealed = !isRevealed
    }

    /** Rates the current card and moves on to the next one. */
    public fun rate(rating: Rating) {
        val item = queue.removeFirstOrNull() ?: return
        val now = clock.now()
        val result = scheduler.review(cardOf(item), rating, now)
        val updated = withCard(item, result.card)
        if (result.card.due <= now + learnAheadLimit) queue.add(updated)
        isRevealed = false
        reviewed += 1
        onReviewed(updated, result)
    }
}

/**
 * Remembers a [StudySessionState].
 *
 * @param items everything that could be studied; only cards due within [learnAheadLimit] are queued.
 * @param cardOf reads the FSRS [Card] stored on your item.
 * @param withCard returns your item with an updated [Card] (e.g. `item.copy(card = card)`).
 * @param onReviewed called after every answer — save `result.card` (and optionally `result.log`) here.
 */
@Composable
public fun <T> rememberStudySessionState(
    items: List<T>,
    cardOf: (T) -> Card,
    withCard: (T, Card) -> T,
    scheduler: Scheduler = remember { Scheduler() },
    clock: Clock = Clock.System,
    learnAheadLimit: Duration = 20.minutes,
    onReviewed: (item: T, result: ReviewResult) -> Unit = { _, _ -> },
): StudySessionState<T> = remember(items, scheduler) {
    StudySessionState(items, cardOf, withCard, scheduler, clock, learnAheadLimit, onReviewed)
}

/**
 * A ready-made study screen: progress bar, a flip + swipe card and the rating buttons.
 * Bring your own faces through [front] and [back]; everything else is handled.
 *
 * ```
 * StudySession(
 *     state = session,
 *     front = { word -> Text(word.english, style = MaterialTheme.typography.displaySmall) },
 *     back = { word -> Text(word.turkish) },
 *     onFinished = { Text("All done for today!") },
 * )
 * ```
 */
@Composable
public fun <T> StudySession(
    state: StudySessionState<T>,
    front: @Composable BoxScope.(T) -> Unit,
    back: @Composable BoxScope.(T) -> Unit,
    modifier: Modifier = Modifier,
    swipeRatings: SwipeRatings = SwipeRatings(),
    ratingStyle: RatingStyle = RatingStyle(),
    showIntervals: Boolean = true,
    onFinished: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())

        val item = state.current
        if (item == null) {
            onFinished()
            return@Column
        }

        AnimatedContent(
            targetState = state.reviewed,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "card",
        ) { _ ->
            val shown = state.current ?: return@AnimatedContent
            SwipeableCard(
                onSwiped = state::rate,
                enabled = state.isRevealed,
                ratings = swipeRatings,
            ) {
                FlipCard(
                    flipped = state.isRevealed,
                    onClick = state::toggle,
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    front = { front(shown) },
                    back = { back(shown) },
                )
            }
        }

        RatingBar(
            onRate = state::rate,
            enabled = state.isRevealed,
            intervals = if (showIntervals) state.intervals else null,
            style = ratingStyle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
