package io.github.muhammedcakirgoz.engram.fsrs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Settings for [Scheduler].
 *
 * @property parameters the 21 FSRS-6 model weights. Use [Fsrs.DEFAULT_PARAMETERS] or weights
 *   produced by an FSRS optimizer for your users.
 * @property desiredRetention target probability of recalling a card when it comes due (0.7–0.97 is sensible).
 * @property learningSteps short intervals for new cards before they graduate.
 * @property relearningSteps short intervals for forgotten cards before they return to review.
 * @property maximumInterval upper bound for review intervals.
 * @property enableFuzzing spread review dates slightly so cards learned together don't stay clumped.
 */
public data class SchedulerConfig(
    val parameters: List<Double> = Fsrs.DEFAULT_PARAMETERS,
    val desiredRetention: Double = 0.9,
    val learningSteps: List<Duration> = listOf(1.minutes, 10.minutes),
    val relearningSteps: List<Duration> = listOf(10.minutes),
    val maximumInterval: Duration = 36500.days,
    val enableFuzzing: Boolean = true,
) {
    init {
        Fsrs.validate(parameters)
        require(desiredRetention > 0.0 && desiredRetention < 1.0) {
            "desiredRetention must be in (0, 1), was $desiredRetention"
        }
        require(maximumInterval >= 1.days) { "maximumInterval must be at least one day" }
    }
}

/** FSRS-6 constants. */
public object Fsrs {
    /** Default FSRS-6 weights, identical to the reference implementations (py-fsrs, ts-fsrs). */
    public val DEFAULT_PARAMETERS: List<Double> = listOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
        1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
    )

    internal const val STABILITY_MIN: Double = 0.001
    internal const val MIN_DIFFICULTY: Double = 1.0
    internal const val MAX_DIFFICULTY: Double = 10.0

    private val LOWER_BOUNDS = listOf(
        STABILITY_MIN, STABILITY_MIN, STABILITY_MIN, STABILITY_MIN, 1.0, 0.001, 0.001, 0.001, 0.0, 0.0,
        0.001, 0.001, 0.001, 0.001, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1,
    )
    private val UPPER_BOUNDS = listOf(
        100.0, 100.0, 100.0, 100.0, 10.0, 4.0, 4.0, 0.75, 4.5, 0.8,
        3.5, 5.0, 0.25, 0.9, 4.0, 1.0, 6.0, 2.0, 2.0, 0.8, 0.8,
    )

    internal fun validate(parameters: List<Double>) {
        require(parameters.size == LOWER_BOUNDS.size) {
            "Expected ${LOWER_BOUNDS.size} FSRS parameters, got ${parameters.size}"
        }
        val errors = parameters.indices.filter { i ->
            parameters[i] !in LOWER_BOUNDS[i]..UPPER_BOUNDS[i]
        }.map { i -> "parameters[$i] = ${parameters[i]} is out of bounds (${LOWER_BOUNDS[i]}, ${UPPER_BOUNDS[i]})" }
        require(errors.isEmpty()) { errors.joinToString("\n") }
    }
}

/**
 * The FSRS-6 spaced-repetition scheduler.
 *
 * A Kotlin Multiplatform port of the reference implementation
 * [py-fsrs](https://github.com/open-spaced-repetition/py-fsrs) (MIT). It is pure and
 * thread-safe: it never mutates cards and keeps no state besides its configuration.
 *
 * ```
 * val scheduler = Scheduler()
 * var card = Card.new(id = "apple", now = Clock.System.now())
 * card = scheduler.review(card, Rating.Good).card
 * ```
 *
 * @param random source of randomness for interval fuzzing. By default the fuzz is derived from the
 *   card's id and review count, so [preview] and [review] always agree on the interval a button shows.
 */
public class Scheduler(
    public val config: SchedulerConfig = SchedulerConfig(),
    private val clock: Clock = Clock.System,
    private val random: Random? = null,
) {
    private val w = config.parameters
    private val decay = -w[20]
    private val factor = 0.9.pow(1 / decay) - 1

    /**
     * Probability (0–1) that the learner would recall [card] at [now].
     * Returns 0 for cards that were never reviewed.
     */
    public fun retrievability(card: Card, now: Instant = clock.now()): Double {
        val lastReview = card.lastReview ?: return 0.0
        val stability = card.stability ?: return 0.0
        val elapsedDays = max(0L, wholeDaysBetween(lastReview, now))
        return (1 + factor * elapsedDays / stability).pow(decay)
    }

    /** Shows what each rating would do to [card], e.g. to label buttons "1m · 10m · 2d · 8d". */
    public fun preview(card: Card, now: Instant = clock.now()): Map<Rating, ReviewResult> =
        Rating.entries.associateWith { review(card, it, now) }

    /**
     * Reviews [card] with [rating] at [now] and returns the rescheduled card plus a [ReviewLog].
     */
    public fun review(
        card: Card,
        rating: Rating,
        now: Instant = clock.now(),
        durationMillis: Long? = null,
    ): ReviewResult {
        val daysSinceLastReview = card.lastReview?.let { wholeDaysBetween(it, now) }
        val shortTerm = daysSinceLastReview != null && daysSinceLastReview < 1

        var state = if (card.state == CardState.New) CardState.Learning else card.state
        var step = card.step ?: if (state == CardState.Review) null else 0
        var stability = card.stability
        var difficulty = card.difficulty
        var lapses = card.lapses
        val interval: Duration

        when (state) {
            CardState.Learning, CardState.Relearning -> {
                val s = stability
                val d = difficulty
                if (s == null || d == null) {
                    stability = initialStability(rating)
                    difficulty = clampDifficulty(initialDifficulty(rating))
                } else {
                    stability = if (shortTerm) {
                        shortTermStability(s, rating)
                    } else {
                        nextStability(d, s, retrievability(card, now), rating)
                    }
                    difficulty = nextDifficulty(d, rating)
                }

                val steps = if (state == CardState.Learning) config.learningSteps else config.relearningSteps
                val currentStep = step ?: 0
                if (steps.isEmpty() || (currentStep >= steps.size && rating != Rating.Again)) {
                    state = CardState.Review
                    step = null
                    interval = nextInterval(stability)
                } else {
                    when (rating) {
                        Rating.Again -> {
                            step = 0
                            interval = steps[0]
                        }
                        Rating.Hard -> {
                            interval = when {
                                currentStep == 0 && steps.size == 1 -> steps[0] * 1.5
                                currentStep == 0 -> (steps[0] + steps[1]) / 2
                                else -> steps[currentStep]
                            }
                        }
                        Rating.Good -> {
                            if (currentStep + 1 >= steps.size) {
                                state = CardState.Review
                                step = null
                                interval = nextInterval(stability)
                            } else {
                                step = currentStep + 1
                                interval = steps[currentStep + 1]
                            }
                        }
                        Rating.Easy -> {
                            state = CardState.Review
                            step = null
                            interval = nextInterval(stability)
                        }
                    }
                }
            }

            CardState.Review -> {
                val s = checkNotNull(stability) { "Review card ${card.id} has no stability" }
                val d = checkNotNull(difficulty) { "Review card ${card.id} has no difficulty" }
                stability = if (shortTerm) {
                    shortTermStability(s, rating)
                } else {
                    nextStability(d, s, retrievability(card, now), rating)
                }
                difficulty = nextDifficulty(d, rating)

                if (rating == Rating.Again) {
                    lapses += 1
                    if (config.relearningSteps.isEmpty()) {
                        interval = nextInterval(stability)
                    } else {
                        state = CardState.Relearning
                        step = 0
                        interval = config.relearningSteps[0]
                    }
                } else {
                    interval = nextInterval(stability)
                }
            }

            CardState.New -> error("unreachable")
        }

        val finalInterval = if (config.enableFuzzing && state == CardState.Review) fuzz(interval, card) else interval

        val updated = card.copy(
            due = now + finalInterval,
            state = state,
            step = step,
            stability = stability,
            difficulty = difficulty,
            lastReview = now,
            reps = card.reps + 1,
            lapses = lapses,
        )
        return ReviewResult(updated, ReviewLog(card.id, rating, now, durationMillis))
    }

    /**
     * Replays [logs] (in any order) on a fresh copy of [card] with this scheduler's settings.
     * Use it after changing [SchedulerConfig.parameters] so existing cards follow the new model.
     */
    public fun reschedule(card: Card, logs: List<ReviewLog>): Card {
        require(logs.all { it.cardId == card.id }) { "All review logs must belong to card ${card.id}" }
        return logs.sortedBy { it.reviewedAt }
            .fold(Card(id = card.id, due = card.due)) { acc, log -> review(acc, log.rating, log.reviewedAt).card }
    }

    // --- FSRS-6 formulas -------------------------------------------------------------------

    private fun clampDifficulty(d: Double) = d.coerceIn(Fsrs.MIN_DIFFICULTY, Fsrs.MAX_DIFFICULTY)

    private fun clampStability(s: Double) = max(s, Fsrs.STABILITY_MIN)

    private fun initialStability(rating: Rating) = clampStability(w[rating.value - 1])

    private fun initialDifficulty(rating: Rating) = w[4] - exp(w[5] * (rating.value - 1)) + 1

    private fun nextInterval(stability: Double): Duration {
        val raw = stability / factor * (config.desiredRetention.pow(1 / decay) - 1)
        val days = round(raw).toLong().coerceAtLeast(1)
        return min(days, config.maximumInterval.inWholeDays).days
    }

    private fun shortTermStability(stability: Double, rating: Rating): Double {
        var increase = exp(w[17] * (rating.value - 3 + w[18])) * stability.pow(-w[19])
        if (rating != Rating.Again) increase = max(increase, 1.0)
        return clampStability(stability * increase)
    }

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val delta = -(w[6] * (rating.value - 3))
        val damped = difficulty + (10.0 - difficulty) * delta / 9.0
        val meanReverted = w[7] * initialDifficulty(Rating.Easy) + (1 - w[7]) * damped
        return clampDifficulty(meanReverted)
    }

    private fun nextStability(difficulty: Double, stability: Double, retrievability: Double, rating: Rating): Double {
        val next = if (rating == Rating.Again) {
            min(
                w[11] * difficulty.pow(-w[12]) * ((stability + 1).pow(w[13]) - 1) * exp((1 - retrievability) * w[14]),
                stability / exp(w[17] * w[18]),
            )
        } else {
            val hardPenalty = if (rating == Rating.Hard) w[15] else 1.0
            val easyBonus = if (rating == Rating.Easy) w[16] else 1.0
            stability * (
                1 + exp(w[8]) * (11 - difficulty) * stability.pow(-w[9]) *
                    (exp((1 - retrievability) * w[10]) - 1) * hardPenalty * easyBonus
                )
        }
        return clampStability(next)
    }

    private fun fuzz(interval: Duration, card: Card): Duration {
        val days = interval.inWholeDays
        if (days < 3) return interval // reference: no fuzz below 2.5 days

        var delta = 1.0
        for ((start, end, f) in FUZZ_RANGES) {
            delta += f * max(min(days.toDouble(), end) - start, 0.0)
        }
        val maxDays = config.maximumInterval.inWholeDays
        val maxIvl = min(round(days + delta).toLong(), maxDays)
        val minIvl = min(max(2L, round(days - delta).toLong()), maxIvl)
        val rng = random ?: Random(card.id.hashCode() * 31L + card.reps)
        val fuzzed = round(rng.nextDouble() * (maxIvl - minIvl + 1) + minIvl).toLong()
        return min(fuzzed, maxDays).days
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L

        val FUZZ_RANGES = listOf(
            Triple(2.5, 7.0, 0.15),
            Triple(7.0, 20.0, 0.1),
            Triple(20.0, Double.POSITIVE_INFINITY, 0.05),
        )

        /** Whole days between two instants, floored like Python's `timedelta.days`. */
        fun wholeDaysBetween(from: Instant, to: Instant): Long =
            (to - from).inWholeMilliseconds.floorDiv(MILLIS_PER_DAY)
    }
}
