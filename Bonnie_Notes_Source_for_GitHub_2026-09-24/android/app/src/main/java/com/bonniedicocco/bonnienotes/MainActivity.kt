package com.bonniedicocco.bonnienotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Data
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.bonniedicocco.bonnienotes.data.MeetingEntity
import com.bonniedicocco.bonnienotes.network.ApiFactory
import com.bonniedicocco.bonnienotes.network.SegmentDto
import com.bonniedicocco.bonnienotes.network.TaskDto
import com.bonniedicocco.bonnienotes.recording.RecordingService
import com.bonniedicocco.bonnienotes.recording.RecordingState
import com.bonniedicocco.bonnienotes.work.ProcessRecordingWorker
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF17243E)
private val Violet = Color(0xFF6750A4)
private val Soft = Color(0xFFF5F2FA)

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BonnieNotesTheme { BonnieNotesApp(model) } }
    }
}

@Composable
fun BonnieNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(primary = Violet, surface = Color.White, background = Color(0xFFF8F8FC)),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BonnieNotesApp(model: MainViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val meetings by model.meetings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Bonnie Notes", fontWeight = FontWeight.SemiBold, color = Ink) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Mic, null) }, label = { Text("Record") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.History, null) }, label = { Text("History") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = Color(0xFFF8F8FC)) {
            when (tab) {
                0 -> RecordScreen()
                1 -> HistoryScreen(meetings, model.query.collectAsState().value, model::search)
                else -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun RecordScreen() {
    val context = LocalContext.current
    val status by RecordingState.status.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var seconds by remember { mutableLongStateOf(0) }
    var permissionDenied by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) RecordingService.start(context, title) else permissionDenied = true
    }
    LaunchedEffect(status.active, status.startedElapsedMs) {
        while (status.active) {
            seconds = (SystemClock.elapsedRealtime() - status.startedElapsedMs) / 1_000
            delay(1_000)
        }
        if (!status.active) seconds = 0
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text(if (status.active) "Recording now" else "Ready to capture", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
        Text(if (status.active) formatDuration(seconds) else "In person, speakerphone, or an online meeting", color = Color.Gray)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Meeting or call title") }, enabled = !status.active)
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Soft), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                Checkbox(consent, { consent = it }, enabled = !status.active)
                Column(Modifier.padding(top = 10.dp)) {
                    Text("Everyone has agreed to be recorded", fontWeight = FontWeight.SemiBold)
                    Text("Florida generally requires prior consent from every participant.", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        if (status.active) {
            Button(onClick = { RecordingService.stop(context) }, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("Stop and process") }
        } else {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        RecordingService.start(context, title)
                    } else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                },
                enabled = consent,
                modifier = Modifier.fillMaxWidth().height(58.dp)
            ) { Icon(Icons.Default.Mic, null); Spacer(Modifier.size(8.dp)); Text("Start recording") }
        }
        Spacer(Modifier.height(16.dp))
        Text("Tip: for calls or online meetings, use speakerphone and keep the phone close. Android may block call audio on some devices.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        if (permissionDenied) Text("Microphone permission is required to record.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun HistoryScreen(meetings: List<MeetingEntity>, query: String, onQuery: (String) -> Unit) {
    var selected by remember { mutableStateOf<MeetingEntity?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            query, onQuery, Modifier.fillMaxWidth().padding(vertical = 12.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Search transcripts and tasks") }
        )
        if (meetings.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.History, null, Modifier.size(48.dp), tint = Color.LightGray)
                Text(if (query.isBlank()) "Your recordings will appear here" else "No matching recordings", color = Color.Gray)
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(meetings, key = { it.id }) { meeting ->
                Card(onClick = { selected = meeting }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(meeting.title, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(meeting.recordedAt.replace('T', ' ').take(16), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        when (meeting.status) {
                            "READY" -> Icon(Icons.Default.CheckCircle, "Ready", tint = Color(0xFF2E7D32))
                            "FAILED" -> Text("Retry needed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            else -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
    selected?.let { DetailDialog(it) { selected = null } }
}

@Composable
private fun DetailDialog(meeting: MeetingEntity, dismiss: () -> Unit) {
    val context = LocalContext.current
    val taskType = object : TypeToken<List<TaskDto>>() {}.type
    val segmentType = object : TypeToken<List<SegmentDto>>() {}.type
    val tasks: List<TaskDto> = runCatching { ApiFactory.gson.fromJson<List<TaskDto>>(meeting.tasksJson, taskType) }.getOrDefault(emptyList())
    val segments: List<SegmentDto> = runCatching { ApiFactory.gson.fromJson<List<SegmentDto>>(meeting.segmentsJson, segmentType) }.getOrDefault(emptyList())
    AlertDialog(
        onDismissRequest = dismiss,
        confirmButton = {
            Row {
                if (meeting.status == "FAILED") {
                    TextButton(onClick = {
                        val request = OneTimeWorkRequestBuilder<ProcessRecordingWorker>()
                            .setInputData(Data.Builder().putString(ProcessRecordingWorker.KEY_MEETING_ID, meeting.id).build())
                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.MINUTES)
                            .build()
                        WorkManager.getInstance(context).enqueue(request)
                        dismiss()
                    }) { Text("Retry") }
                }
                TextButton(onClick = dismiss) { Text("Close") }
            }
        },
        title = { Text(meeting.title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (meeting.error != null) item { Text(meeting.error, color = MaterialTheme.colorScheme.error) }
                if (tasks.isNotEmpty()) {
                    item { Text("Tasks", fontWeight = FontWeight.Bold) }
                    items(tasks) { task ->
                        Column {
                            Text("• ${task.description}")
                            Text(listOfNotNull(task.owner?.let { "Owner: $it" }, task.dueDate?.let { "Due: $it" }).joinToString("  •  "), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
                item { Text("Transcript", fontWeight = FontWeight.Bold) }
                if (segments.isNotEmpty()) items(segments) { segment ->
                    Text("${segment.speaker}: ${segment.text}")
                } else item { Text(meeting.transcript.ifBlank { "Transcript is still processing." }) }
            }
        }
    )
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    var message by remember { mutableStateOf("Check your private server and Google Drive connection.") }
    var checking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Connections", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Text(message, color = Color.DarkGray)
        Button(enabled = !checking, onClick = {
            checking = true
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                message = try {
                    val status = ApiFactory.api.status()
                    "Server connected. Google Drive: ${if (status.googleDriveConnected) "connected" else "not connected"}."
                } catch (error: Exception) { "Connection failed: ${error.message}" }
                checking = false
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (checking) "Checking…" else "Test connection") }
        OutlinedButton(onClick = {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    val link = ApiFactory.api.googleConnectLink()
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                } catch (error: Exception) { message = "Could not start Google connection: ${error.message}" }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Connect Google Drive") }
        Card(colors = CardDefaults.cardColors(containerColor = Soft)) {
            Text("Security: your OpenAI and Google credentials stay on the backend. The app stores recordings privately in its own app storage.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatDuration(seconds: Long) = "%02d:%02d".format(seconds / 60, seconds % 60)
