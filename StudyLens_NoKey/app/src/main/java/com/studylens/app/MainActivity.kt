package com.studylens.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Note(val title: String, val body: String)

data class State(
    val photos: List<Uri> = emptyList(),
    val subject: String = "General",
    val language: String = "English",
    val notes: List<Note> = emptyList(),
    val busy: Boolean = false,
    val progress: Int = 0,
    val error: String? = null
)

class StudyViewModel : ViewModel() {
    var state by mutableStateOf(State())
        private set

    // Replace this with your deployed StudyLens backend URL.
    private val backendUrl = "https://YOUR-STUDYLENS-BACKEND.example.com/analyze"

    private val http = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    fun subject(v: String) { state = state.copy(subject = v) }
    fun language(v: String) { state = state.copy(language = v) }

    fun addPhotos(v: List<Uri>) {
        state = state.copy(photos = (state.photos + v).distinct(), error = null)
    }

    fun removePhoto(i: Int) {
        state = state.copy(photos = state.photos.toMutableList().also { it.removeAt(i) })
    }

    fun clear() {
        state = state.copy(photos = emptyList(), notes = emptyList(), error = null, progress = 0)
    }

    fun generate(context: Context) {
        if (state.photos.isEmpty()) {
            state = state.copy(error = "Add at least one page first.")
            return
        }
        if (backendUrl.contains("YOUR-STUDYLENS")) {
            state = state.copy(error = "The app still needs your StudyLens backend URL.")
            return
        }
        viewModelScope.launch {
            state = state.copy(busy = true, notes = emptyList(), error = null, progress = 0)
            val all = mutableListOf<Note>()
            state.photos.forEachIndexed { i, uri ->
                try {
                    all += withContext(Dispatchers.IO) {
                        analyze(context, uri, state.subject, state.language)
                    }
                } catch (e: Exception) {
                    state = state.copy(error = "Page ${i + 1}: ${e.message ?: "failed"}")
                }
                state = state.copy(progress = i + 1, notes = all.toList())
            }
            state = state.copy(busy = false)
        }
    }

    private fun analyze(context: Context, uri: Uri, subject: String, language: String): List<Note> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read the image.")
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val image = "data:$mime;base64," +
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

        val body = JSONObject()
            .put("subject", subject)
            .put("language", language)
            .put("image", image)
            .toString()

        val req = Request.Builder()
            .url(backendUrl)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful)
                throw IllegalStateException(
                    try { JSONObject(raw).optString("error") }
                    catch (_: Exception) { "Backend error ${response.code}" }
                )
            val topics = JSONObject(raw).optJSONArray("topics") ?: JSONArray()
            buildList {
                for (i in 0 until topics.length()) {
                    val t = topics.getJSONObject(i)
                    val arr = t.optJSONArray("notes") ?: JSONArray()
                    val b = buildString {
                        for (j in 0 until arr.length()) {
                            if (j > 0) append("\n")
                            append("• ").append(arr.optString(j))
                        }
                    }
                    add(Note(t.optString("title", "Topic"), b))
                }
            }
        }
    }
}

private val Indigo = Color(0xFF5B5CE2)
private val Purple = Color(0xFF8B5CF6)
private val Ink = Color(0xFF171827)
private val Muted = Color(0xFF6F7183)
private val Page = Color(0xFFF7F7FB)

@Composable
fun StudyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Indigo,
            secondary = Purple,
            background = Page,
            surface = Color.White,
            onBackground = Ink,
            onSurface = Ink
        ),
        typography = Typography(),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: StudyViewModel = viewModel()) {
    val s = vm.state
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { vm.addPhotos(it) }

    StudyTheme {
        Scaffold(
            containerColor = Page,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.AutoAwesome, null) },
                        label = { Text("Study") }
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Description, null) },
                        label = { Text("Notes") }
                    )
                }
            }
        ) { pad ->
            if (tab == 1) {
                NotesScreen(s.notes, pad)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(pad),
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("StudyLens", style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold)
                                Text("Study smarter. Revise faster.", color = Muted)
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Indigo.copy(alpha = .10f)
                            ) {
                                Icon(Icons.Default.School, null,
                                    tint = Indigo, modifier = Modifier.padding(12.dp))
                            }
                        }
                    }

                    item {
                        Box(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    Brush.linearGradient(listOf(Indigo, Purple))
                                )
                                .padding(22.dp)
                        ) {
                            Column {
                                Text("Turn pages into notes",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    "Upload your textbook pages and let AI find the important topics, definitions and formulas.",
                                    color = Color.White.copy(alpha = .90f)
                                )
                                Spacer(Modifier.height(18.dp))
                                Button(
                                    onClick = { picker.launch(arrayOf("image/*")) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Indigo
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().height(54.dp)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Choose textbook photos", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        Text("Setup", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                    }

                    item {
                        OutlinedTextField(
                            value = s.subject,
                            onValueChange = vm::subject,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            label = { Text("Subject") },
                            leadingIcon = { Icon(Icons.Default.MenuBook, null) }
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = s.language == "English",
                                onClick = { vm.language("English") },
                                label = { Text("English") },
                                leadingIcon = if (s.language == "English")
                                    { { Icon(Icons.Default.Check, null) } } else null
                            )
                            FilterChip(
                                selected = s.language == "Urdu",
                                onClick = { vm.language("Urdu") },
                                label = { Text("Urdu") },
                                leadingIcon = if (s.language == "Urdu")
                                    { { Icon(Icons.Default.Check, null) } } else null
                            )
                        }
                    }

                    if (s.photos.isNotEmpty()) {
                        item {
                            Card(shape = RoundedCornerShape(22.dp)) {
                                Column(Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("${s.photos.size} pages ready",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold)
                                            Text("No fixed page limit in the app",
                                                color = Muted,
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = vm::clear) { Text("Clear") }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    s.photos.take(8).forEachIndexed { i, _ ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Indigo.copy(alpha = .10f)
                                            ) {
                                                Text("${i + 1}", Modifier.padding(8.dp),
                                                    color = Indigo, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Text("Textbook page ${i + 1}",
                                                Modifier.weight(1f))
                                            IconButton(onClick = { vm.removePhoto(i) }) {
                                                Icon(Icons.Default.Close, null)
                                            }
                                        }
                                    }
                                    if (s.photos.size > 8)
                                        Text("+ ${s.photos.size - 8} more pages",
                                            color = Muted)
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { vm.generate(context) },
                            enabled = s.photos.isNotEmpty() && !s.busy,
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            if (s.busy) {
                                CircularProgressIndicator(Modifier.size(21.dp),
                                    strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(10.dp))
                                Text("Analyzing ${s.progress}/${s.photos.size}…")
                            } else {
                                Icon(Icons.Default.AutoAwesome, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Generate important notes",
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    s.error?.let {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(it, Modifier.padding(15.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }

                    if (s.notes.isNotEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Fresh notes", style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text("${s.notes.size} topics", color = Muted)
                            }
                        }
                        items(s.notes.take(4)) { note ->
                            NoteCard(note)
                        }
                        if (s.notes.size > 4) {
                            item {
                                OutlinedButton(
                                    onClick = { tab = 1 },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) { Text("View all notes") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCard(note: Note) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(19.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Indigo.copy(alpha = .10f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Lightbulb, null, tint = Indigo,
                        modifier = Modifier.padding(9.dp))
                }
                Spacer(Modifier.width(11.dp))
                Text(note.title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(note.body, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight)
        }
    }
}

@Composable
fun NotesScreen(notes: List<Note>, pad: PaddingValues) {
    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Your notes", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold)
            Text("${notes.size} topic${if (notes.size == 1) "" else "s"} generated",
                color = Muted)
        }
        if (notes.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(25.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MenuBook, null, tint = Indigo,
                            modifier = Modifier.size(45.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No notes yet", fontWeight = FontWeight.Bold)
                        Text("Upload textbook pages from the Study tab.")
                    }
                }
            }
        } else {
            items(notes) { NoteCard(it) }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
