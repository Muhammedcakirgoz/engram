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
import kotlin.test.Test
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
}
