package io.github.muhammedcakirgoz.engram.fsrs

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Expected values were generated with the reference implementation py-fsrs 6.3.2
 * (`Scheduler(enable_fuzzing=False)`), so these tests pin us to the official algorithm.
 */
class SchedulerTest {
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private val scheduler = Scheduler(SchedulerConfig(enableFuzzing = false))

    private data class Expected(
        val rating: Int,
        val state: CardState,
        val step: Int?,
        val stability: Double,
        val difficulty: Double,
        val dueMinutes: Long,
    )

    private fun replay(vararg expected: Expected) {
        var card = Card.new("c", t0)
        var now = t0
        expected.forEachIndexed { i, e ->
            card = scheduler.review(card, Rating.entries[e.rating - 1], now).card
            val where = "review #${i + 1}"
            assertEquals(e.state, card.state, "$where state")
            assertEquals(e.step, card.step, "$where step")
            assertClose(e.stability, card.stability!!, "$where stability")
            assertClose(e.difficulty, card.difficulty!!, "$where difficulty")
            assertEquals(e.dueMinutes, (card.due - now).inWholeMinutes, "$where interval")
            now = card.due
        }
    }

    private fun assertClose(expected: Double, actual: Double, message: String) =
        assertTrue(abs(expected - actual) < 1e-9, "$message: expected $expected, was $actual")

    @Test
    fun goodPathMatchesReference() = replay(
        Expected(3, CardState.Learning, 1, 2.3065, 2.118103970459016, 10),
        Expected(3, CardState.Review, null, 2.3065, 2.111214235785395, 2880),
        Expected(3, CardState.Review, null, 10.971048263078135, 2.1043313908464483, 15840),
        Expected(3, CardState.Review, null, 46.316858440073425, 2.0974554287524403, 66240),
        Expected(3, CardState.Review, null, 162.99981577472244, 2.0905863426205262, 234720),
        Expected(3, CardState.Review, null, 497.8765551245907, 2.083724125574744, 717120),
    )

    @Test
    fun mixedRatingsMatchReference() = replay(
        Expected(1, CardState.Learning, 0, 0.212, 6.4133, 1),
        Expected(3, CardState.Learning, 1, 0.24668918777567272, 6.402115069296838, 10),
        Expected(3, CardState.Review, null, 0.2842063592758949, 6.3909413235243795, 1440),
        Expected(2, CardState.Review, null, 1.407544511593786, 7.589367037717998, 1440),
        Expected(1, CardState.Relearning, 0, 0.3872414138980914, 9.192869706898888, 10),
        Expected(3, CardState.Review, null, 0.4330915882384878, 9.178905206488826, 1440),
        Expected(4, CardState.Review, null, 2.0002464127497483, 8.889761958811608, 2880),
        Expected(3, CardState.Review, null, 4.026132226461084, 8.876100566149633, 5760),
    )

    @Test
    fun easyFirstMatchesReference() = replay(
        Expected(4, CardState.Review, null, 8.2956, 1.0, 11520),
        Expected(4, CardState.Review, null, 65.62422616189994, 1.0, 95040),
        Expected(4, CardState.Review, null, 396.7750233497581, 1.0, 571680),
    )

    @Test
    fun hardAndLapsesMatchReference() = replay(
        Expected(2, CardState.Learning, 0, 1.2931, 5.112170705601056, 5),
        Expected(2, CardState.Learning, 0, 1.2931, 6.7404595108297, 5),
        Expected(3, CardState.Learning, 1, 1.3358997622047517, 6.728947420615708, 10),
        Expected(3, CardState.Review, null, 1.3771622371865915, 6.717446842491931, 1440),
        Expected(1, CardState.Relearning, 0, 0.38465976209442126, 8.906275467785408, 10),
        Expected(1, CardState.Relearning, 0, 0.14543078517524322, 9.62572855288472, 10),
        Expected(3, CardState.Review, null, 0.17347652536551433, 9.611331193628672, 1440),
        Expected(3, CardState.Review, null, 0.642133533564525, 9.596948231731881, 1440),
    )

    @Test
    fun retrievabilityMatchesReference() {
        var card = Card.new("c", t0)
        card = scheduler.review(card, Rating.Good, t0).card
        card = scheduler.review(card, Rating.Good, card.due).card
        assertClose(0.7743669167614039, scheduler.retrievability(card, card.lastReview!! + 10.days), "R")
        assertEquals(0.0, scheduler.retrievability(Card.new("n", t0), t0))
    }

    @Test
    fun countsRepsAndLapses() {
        var card = Card.new("c", t0)
        for (r in listOf(Rating.Good, Rating.Good, Rating.Again, Rating.Good)) {
            card = scheduler.review(card, r, card.due).card
        }
        assertEquals(4, card.reps)
        assertEquals(1, card.lapses)
    }

    @Test
    fun previewReturnsAllFourOutcomes() {
        val preview = scheduler.preview(Card.new("c", t0), t0)
        assertEquals(Rating.entries.toSet(), preview.keys)
        assertEquals(1.minutes, preview.getValue(Rating.Again).card.due - t0)
        assertEquals(5.5.minutes, preview.getValue(Rating.Hard).card.due - t0)
        assertEquals(10.minutes, preview.getValue(Rating.Good).card.due - t0)
        assertEquals(8.days, preview.getValue(Rating.Easy).card.due - t0)
    }

    @Test
    fun fuzzStaysNearUnfuzzedInterval() {
        val fuzzy = Scheduler(SchedulerConfig(enableFuzzing = true), random = Random(42))
        var plain = Card.new("c", t0)
        repeat(4) { plain = scheduler.review(plain, Rating.Good, plain.due).card }
        val base = scheduler.review(plain, Rating.Good, plain.due).card.due - plain.due
        repeat(50) {
            val fuzzed = fuzzy.review(plain, Rating.Good, plain.due).card.due - plain.due
            assertTrue(abs(fuzzed.inWholeDays - base.inWholeDays) <= base.inWholeDays * 0.1 + 2, "fuzzed=$fuzzed base=$base")
        }
    }

    @Test
    fun defaultFuzzIsDeterministicSoPreviewMatchesReview() {
        val fuzzy = Scheduler(SchedulerConfig(enableFuzzing = true))
        var card = Card.new("c", t0)
        repeat(4) { card = fuzzy.review(card, Rating.Good, card.due).card }
        val now = card.due + 3.days
        val preview = fuzzy.preview(card, now)
        for (rating in Rating.entries) {
            assertEquals(preview.getValue(rating).card, fuzzy.review(card, rating, now).card, "$rating")
        }
    }

    @Test
    fun rescheduleReplaysLogs() {
        var card = Card.new("c", t0)
        val logs = mutableListOf<ReviewLog>()
        for (r in listOf(Rating.Good, Rating.Good, Rating.Hard)) {
            val result = scheduler.review(card, r, card.due)
            card = result.card
            logs += result.log
        }
        assertEquals(card, scheduler.reschedule(Card.new("c", t0), logs.shuffled(Random(1))))
    }

    @Test
    fun rejectsInvalidParameters() {
        assertFailsWith<IllegalArgumentException> { SchedulerConfig(parameters = listOf(1.0)) }
        assertFailsWith<IllegalArgumentException> { SchedulerConfig(desiredRetention = 1.5) }
    }
}
