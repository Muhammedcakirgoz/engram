package io.github.muhammedcakirgoz.engram.fsrs

import kotlin.time.Instant

/** How well the learner recalled a card. Values match FSRS / Anki (1–4). */
public enum class Rating(public val value: Int) {
    Again(1),
    Hard(2),
    Good(3),
    Easy(4),
}

/** Where a card is in its life cycle. */
public enum class CardState {
    /** Never reviewed. */
    New,

    /** Going through the short learning steps (e.g. 1m, 10m). */
    Learning,

    /** Graduated; scheduled in whole days. */
    Review,

    /** Was forgotten during review and is going through relearning steps. */
    Relearning,
}

/**
 * Scheduling state of one flashcard. Immutable: [Scheduler.review] returns a new copy.
 *
 * Store these fields in your own database next to the card's content; the library does not
 * care what the card shows.
 *
 * @property id your identifier for the card.
 * @property due when the card should be shown next.
 * @property state current life-cycle state.
 * @property step index of the current learning/relearning step, or `null` in [CardState.Review]/[CardState.New].
 * @property stability FSRS memory stability in days (`null` until first review).
 * @property difficulty FSRS difficulty, 1 (easy) … 10 (hard) (`null` until first review).
 * @property lastReview time of the most recent review, or `null` for a new card.
 * @property reps how many times the card has been reviewed.
 * @property lapses how many times the card was forgotten after graduating.
 */
public data class Card(
    val id: String,
    val due: Instant,
    val state: CardState = CardState.New,
    val step: Int? = null,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val lastReview: Instant? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
) {
    public companion object {
        /** A brand-new card that is due at [now]. */
        public fun new(id: String, now: Instant): Card = Card(id = id, due = now)
    }
}

/** A record of one review, useful for statistics or for re-scheduling with new parameters. */
public data class ReviewLog(
    val cardId: String,
    val rating: Rating,
    val reviewedAt: Instant,
    val durationMillis: Long? = null,
)

/** The result of reviewing a card: the updated card and the log entry for that review. */
public data class ReviewResult(
    val card: Card,
    val log: ReviewLog,
)
