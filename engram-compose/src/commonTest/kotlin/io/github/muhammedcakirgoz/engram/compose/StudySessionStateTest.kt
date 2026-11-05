package io.github.muhammedcakirgoz.engram.compose

import io.github.muhammedcakirgoz.engram.fsrs.Card
import io.github.muhammedcakirgoz.engram.fsrs.Rating
import io.github.muhammedcakirgoz.engram.fsrs.Scheduler
import io.github.muhammedcakirgoz.engram.fsrs.SchedulerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class StudySessionStateTest {
    private class FakeClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private data class Item(val name: String, val card: Card)

    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FakeClock(t0)
    private val scheduler = Scheduler(SchedulerConfig(enableFuzzing = false), clock)

    private fun session(items: List<Item>, saved: MutableList<Item> = mutableListOf()) = StudySessionState(
        items = items,
        cardOf = { it.card },
        withCard = { item, card -> item.copy(card = card) },
        scheduler = scheduler,
        clock = clock,
        learnAheadLimit = 20.minutes,
        onReviewed = { item, _ -> saved += item },
    )

    @Test
    fun queuesOnlyDueCardsInDueOrder() {
        val s = session(
            listOf(
                Item("later", Card.new("later", t0 + 5.days)),
                Item("b", Card.new("b", t0 - 1.minutes)),
                Item("a", Card.new("a", t0 - 2.days)),
            ),
        )
        assertEquals(2, s.remaining)
        assertEquals("a", s.current?.name)
    }

    @Test
    fun shortStepsComeBackAndGraduatedCardsLeave() {
        val saved = mutableListOf<Item>()
        val s = session(listOf(Item("a", Card.new("a", t0)), Item("b", Card.new("b", t0))), saved)

        s.reveal()
        assertTrue(s.isRevealed)
        s.rate(Rating.Good) // a -> learning step 10m: requeued
        assertFalse(s.isRevealed)
        assertEquals("b", s.current?.name)
        assertEquals(2, s.remaining)

        s.rate(Rating.Easy) // b -> review in days: leaves the session
        assertEquals(1, s.remaining)
        s.rate(Rating.Good) // a -> graduates
        assertTrue(s.isFinished)
        assertNull(s.current)
        assertEquals(3, s.reviewed)
        assertEquals(listOf("a", "b", "a"), saved.map { it.name })
    }

    @Test
    fun intervalsPreviewEveryRating() {
        val s = session(listOf(Item("a", Card.new("a", t0))))
        assertEquals(1.minutes, s.intervals?.get(Rating.Again))
        assertEquals(10.minutes, s.intervals?.get(Rating.Good))
        assertEquals(8.days, s.intervals?.get(Rating.Easy))
    }

    @Test
    fun itemsThatLoadLaterJoinTheQueue() {
        val s = session(emptyList())
        assertTrue(s.isFinished)
        s.updateItems(listOf(Item("a", Card.new("a", t0)), Item("b", Card.new("b", t0))))
        assertEquals(2, s.remaining)
        assertEquals("a", s.current?.name)
    }

    @Test
    fun staleListDoesNotUndoAnAnswer() {
        val original = listOf(Item("a", Card.new("a", t0)), Item("b", Card.new("b", t0)))
        val s = session(original)
        s.rate(Rating.Again) // a -> 1m step, requeued
        // The database has not saved yet and emits the old list again.
        s.updateItems(original)
        s.rate(Rating.Good) // b
        val a = s.current!!
        assertEquals("a", a.name)
        assertEquals(1, a.card.reps, "the session must keep the answered card, not the stale one")
        assertEquals(0, a.card.step)
    }

    @Test
    fun updatedContentIsShownAndDeletedItemsLeave() {
        val s = session(listOf(Item("a", Card.new("a", t0)), Item("b", Card.new("b", t0)), Item("c", Card.new("c", t0))))
        s.updateItems(listOf(Item("a2", Card.new("a", t0)), Item("c", Card.new("c", t0))))
        assertEquals(2, s.remaining)
        assertEquals("a2", s.current?.name)
        s.rate(Rating.Easy)
        assertEquals("c", s.current?.name)
    }

    @Test
    fun answeredCardsAreNotQueuedAgainByAnUpdate() {
        val s = session(listOf(Item("a", Card.new("a", t0)), Item("b", Card.new("b", t0))))
        s.rate(Rating.Easy) // a graduates and leaves
        // Even if the list still shows "a" as due, it must not come back.
        s.updateItems(listOf(Item("a", Card.new("a", t0)), Item("b", Card.new("b", t0))))
        assertEquals(1, s.remaining)
        assertEquals(0.5f, s.progress)
    }
}
