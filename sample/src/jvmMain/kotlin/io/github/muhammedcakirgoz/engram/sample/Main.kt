package io.github.muhammedcakirgoz.engram.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.muhammedcakirgoz.engram.compose.StudySession
import io.github.muhammedcakirgoz.engram.compose.rememberStudySessionState
import io.github.muhammedcakirgoz.engram.fsrs.Card
import kotlin.time.Clock

data class Word(val english: String, val turkish: String, val example: String, val card: Card)

private val now = Clock.System.now()

private val words = listOf(
    Word("apple", "elma", "She ate an apple.", Card.new("apple", now)),
    Word("journey", "yolculuk", "The journey took three days.", Card.new("journey", now)),
    Word("brave", "cesur", "It was a brave decision.", Card.new("brave", now)),
    Word("borrow", "ödünç almak", "Can I borrow your pen?", Card.new("borrow", now)),
    Word("improve", "geliştirmek", "Practice will improve your English.", Card.new("improve", now)),
)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Engram sample",
        state = rememberWindowState(size = DpSize(420.dp, 720.dp)),
    ) {
        MaterialTheme {
            Surface(Modifier.fillMaxSize()) {
                // In a real app this would be your database.
                val saved = remember { mutableStateMapOf<String, Card>() }
                val session = rememberStudySessionState(
                    items = words,
                    cardOf = { it.card },
                    withCard = { word, card -> word.copy(card = card) },
                    onReviewed = { word, result -> saved[word.english] = result.card },
                )
                StudySession(
                    state = session,
                    modifier = Modifier.padding(24.dp),
                    front = { word ->
                        Text(word.english, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    },
                    back = { word ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(word.turkish, style = MaterialTheme.typography.headlineMedium)
                            Text(word.example, style = MaterialTheme.typography.bodyLarge)
                        }
                    },
                    onFinished = {
                        Text("Bugünlük bitti! ${saved.size} kelime kaydedildi.", style = MaterialTheme.typography.titleLarge)
                    },
                )
            }
        }
    }
}
