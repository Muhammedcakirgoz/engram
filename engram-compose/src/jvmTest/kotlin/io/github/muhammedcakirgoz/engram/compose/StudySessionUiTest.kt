package io.github.muhammedcakirgoz.engram.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.muhammedcakirgoz.engram.fsrs.Card
import androidx.compose.runtime.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Clock

@OptIn(ExperimentalTestApi::class)
class StudySessionUiTest {
    private data class Word(val en: String, val tr: String, val card: Card)

    @Test
    fun revealRateAndFinish() = runComposeUiTest {
        val now = Clock.System.now()
        setContent {
            MaterialTheme {
                val session = rememberStudySessionState(
                    items = listOf(Word("apple", "elma", Card.new("apple", now))),
                    cardOf = { it.card },
                    withCard = { w, c -> w.copy(card = c) },
                )
                StudySession(
                    state = session,
                    front = { Text(it.en) },
                    back = { Text(it.tr) },
                    onFinished = { Text("done") },
                )
            }
        }

        onNodeWithText("apple").assertExists()
        onNodeWithText("Easy", useUnmergedTree = true).assertExists()
        onNode(hasText("Good", substring = true)).assertIsNotEnabled()

        onNodeWithText("apple").performClick()
        waitForIdle()
        onNodeWithText("elma").assertExists()
        onNode(hasText("Easy", substring = true)).assertIsEnabled()
        onNode(hasText("Easy", substring = true) and hasText("d", substring = true)).assertExists()

        onNode(hasText("Easy", substring = true)).performClick()
        waitForIdle()
        onNodeWithText("done").assertExists()
    }

    /**
     * Real apps load items from a database and save every review back to it, so the list passed
     * to [rememberStudySessionState] changes after each answer. The session must keep going.
     */
    @Test
    fun sessionSurvivesItemsUpdatedFromDatabase() = runComposeUiTest {
        val now = Clock.System.now()
        val database = mutableStateOf(
            listOf(
                Word("apple", "elma", Card.new("apple", now)),
                Word("journey", "yolculuk", Card.new("journey", now)),
            ),
        )
        lateinit var session: StudySessionState<Word>
        setContent {
            MaterialTheme {
                session = rememberStudySessionState(
                    items = database.value,
                    cardOf = { it.card },
                    withCard = { w, c -> w.copy(card = c) },
                    onReviewed = { word, _ ->
                        database.value = database.value.map { if (it.en == word.en) word else it }
                    },
                )
                StudySession(state = session, front = { Text(it.en) }, back = { Text(it.tr) })
            }
        }

        val original = session
        onNodeWithText("apple").performClick()
        waitForIdle()
        onNode(hasText("Easy", substring = true)).performClick()
        waitForIdle()

        assertSame(original, session, "session must not be recreated when items change")
        assertEquals(1, session.reviewed)
        assertEquals(0.5f, session.progress)
        onNodeWithText("journey").assertExists()
    }
}
