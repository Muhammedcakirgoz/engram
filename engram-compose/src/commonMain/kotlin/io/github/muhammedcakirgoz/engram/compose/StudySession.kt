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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
 * Items are matched by [Card.id]. When the item list changes during a session (because you saved
 * a review to your database, or the list finished loading), the session keeps its place:
 * new due items are appended, deleted items are dropped, and changed content is picked up.
 *
 * Create it with [rememberStudySessionState]. Persist results in `onReviewed`.
 */
@Stable
public class StudySessionState<T> internal constructor(
    items: List<T>,
    cardOf: (T) -> Card,
    withCard: (T, Card) -> T,
    scheduler: Scheduler,
    private val clock: Clock,
    private val learnAheadLimit: Duration,
    onReviewed: (item: T, result: ReviewResult) -> Unit,
) {
    internal var cardOf: (T) -> Card = cardOf
    internal var withCard: (T, Card) -> T = withCard
    internal var onReviewed: (item: T, result: ReviewResult) -> Unit = onReviewed

    /** Scheduler used for new answers. Changing it does not restart the session. */
    public var scheduler: Scheduler by mutableStateOf(scheduler)
        internal set

    /** Latest version of every item, by card id. */
    private var itemsById: Map<String, T> by mutableStateOf(emptyMap())

    /** Ids waiting to be shown, in order. */
    private val queue = mutableStateListOf<String>()

    /** Ids that have been part of this session. */
    private val seen = mutableSetOf<String>()

    /** Cards answered in this session. They win over the item's own card, which may be stale. */
    private val answered = mutableMapOf<String, Card>()

    private var seenCount by mutableStateOf(0)

    init {
        updateItems(items)
    }

    /** Item being shown, or `null` when the session is finished. */
    public val current: T? by derivedStateOf { queue.firstOrNull()?.let(::itemFor) }

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

    /** Share of this session's cards that are done for now, between 0 and 1. */
    public val progress: Float by derivedStateOf {
        if (seenCount == 0) 1f else (seenCount - queue.size).toFloat() / seenCount
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
        val id = queue.firstOrNull() ?: return
        val item = itemFor(id)
        queue.removeAt(0)
        val now = clock.now()
        val result = scheduler.review(cardOf(item), rating, now)
        answered[id] = result.card
        if (result.card.due <= now + learnAheadLimit) queue.add(id)
        isRevealed = false
        reviewed += 1
        onReviewed(withCard(item, result.card), result)
    }

    /**
     * Brings the session up to date with a new item list without restarting it.
     * [rememberStudySessionState] calls this for you whenever `items` changes.
     */
    public fun updateItems(items: List<T>) {
        val byId = items.associateBy { cardOf(it).id }
        itemsById = byId
        queue.retainAll { it in byId }

        val now = clock.now()
        byId.values
            .filter { cardOf(it).id !in seen && cardOf(it).due <= now + learnAheadLimit }
            .sortedBy { cardOf(it).due }
            .forEach {
                val id = cardOf(it).id
                seen += id
                queue.add(id)
            }
        seenCount = seen.size
    }

    private fun itemFor(id: String): T {
        val item = itemsById.getValue(id)
        val card = answered[id] ?: return item
        return if (cardOf(item) == card) item else withCard(item, card)
    }
}

/**
 * Remembers a [StudySessionState].
 *
 * The session lives as long as [key] stays the same. Passing a new [items] list (for example after
 * each review is saved to your database) updates the session in place instead of restarting it.
 * Change [key] (e.g. to a deck id) to start over.
 *
 * @param items everything that could be studied; only cards due within [learnAheadLimit] are queued.
 *   Each item's [Card.id] must be unique.
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
    key: Any? = null,
    onReviewed: (item: T, result: ReviewResult) -> Unit = { _, _ -> },
): StudySessionState<T> {
    val state = remember(key) {
        StudySessionState(items, cardOf, withCard, scheduler, clock, learnAheadLimit, onReviewed)
    }
    SideEffect {
        state.cardOf = cardOf
        state.withCard = withCard
        state.onReviewed = onReviewed
        state.scheduler = scheduler
    }
    LaunchedEffect(state, items) { state.updateItems(items) }
    return state
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
