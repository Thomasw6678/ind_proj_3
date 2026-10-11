package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var openAlerts by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openAlerts = intent.getBooleanExtra(FoundItemNotifications.OPEN_ALERTS, false)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MyApplicationApp(openAlerts = openAlerts, onAlertsOpened = { openAlerts = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openAlerts = intent.getBooleanExtra(FoundItemNotifications.OPEN_ALERTS, false)
    }
}

private enum class AppDestination(val label: String, val icon: Int) {
    HOME("Home", R.drawable.ic_home),
    LOST("Lost", R.drawable.ic_lost),
    FOUND("Found", R.drawable.ic_found),
    ALERTS("Alerts", R.drawable.ic_notifications),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyApplicationApp(openAlerts: Boolean = false, onAlertsOpened: () -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember { ReportRepository(context.applicationContext) }
    val loaded = remember { runCatching { repository.load() } }
    if (loaded.isFailure) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                Text("Reports could not be loaded", style = MaterialTheme.typography.headlineSmall)
                Text("Please close and reopen the app. Your saved reports have not been overwritten.")
            }
        }
        return
    }
    var book by remember { mutableStateOf(loaded.getOrThrow()) }
    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    var selectedLostId by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(FoundItemNotifications.allowed(context)) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = FoundItemNotifications.allowed(context)
        if (!notificationsAllowed) scope.launch {
            snackbar.showSnackbar("Android notifications are off. Item updates will still appear in Alerts.")
        }
    }
    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = FoundItemNotifications.allowed(context)
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(openAlerts) {
        if (openAlerts) {
            destination = AppDestination.ALERTS
            onAlertsOpened()
        }
    }

    fun save(next: ReportBook, message: String, navigateHome: Boolean) {
        if (saving) return
        saving = true
        scope.launch {
            var feedback = message
            try {
                withContext(Dispatchers.IO) { repository.save(next) }
                val newAlerts = next.alerts.filter { alert -> book.alerts.none { it.id == alert.id } }
                book = next
                if (navigateHome) {
                    selectedLostId = null
                    destination = AppDestination.HOME
                }
                newAlerts.forEach { FoundItemNotifications.send(context, it, next) }
            } catch (error: Exception) {
                feedback = error.message ?: "Could not save. Please try again."
            } finally {
                saving = false
            }
            snackbar.showSnackbar(feedback)
        }
    }

    NavigationSuiteScaffold(navigationSuiteItems = {
        AppDestination.entries.forEach { page ->
            item(
                icon = {
                    BadgedBox(badge = {
                        val unread = book.alerts.count { !it.read }
                        if (page == AppDestination.ALERTS && unread > 0) Badge { Text(unread.toString()) }
                    }) {
                        Icon(painterResource(page.icon), page.label, Modifier.size(24.dp))
                    }
                },
                label = { Text(page.label) },
                selected = destination == page,
                onClick = {
                    if (!saving) {
                        selectedLostId = null
                        destination = page
                    }
                },
            )
        }
    }) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(when (destination) {
                AppDestination.HOME -> "Lost & Found"
                AppDestination.LOST -> "Report a lost item"
                AppDestination.FOUND -> "Report a found item"
                AppDestination.ALERTS -> "Notifications"
            }) }) },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            val contentModifier = Modifier.fillMaxSize().padding(padding)
            when (destination) {
                AppDestination.HOME -> HomeScreen(
                    book, contentModifier, saving,
                    onLost = { destination = AppDestination.LOST },
                    onFound = { destination = AppDestination.FOUND },
                    onMatch = { id -> selectedLostId = id; destination = AppDestination.FOUND },
                )
                AppDestination.LOST, AppDestination.FOUND -> key(destination, selectedLostId) {
                    ReportForm(
                        kind = if (destination == AppDestination.LOST) ReportKind.LOST else ReportKind.FOUND,
                        book = book,
                        initialLostId = selectedLostId,
                        saving = saving,
                        modifier = contentModifier,
                        onSubmit = { kind, description, location, photo, lostId ->
                            val next = if (kind == ReportKind.LOST) book.addLost(description, location, photo)
                                else book.addFound(description, location, photo, lostId)
                            save(next, if (kind == ReportKind.LOST) "Lost item report saved."
                                else if (lostId != null) "Found item reported. An item-found alert was added."
                                else "Found item report saved.", true)
                        },
                    )
                }
                AppDestination.ALERTS -> AlertsScreen(
                    book = book,
                    notificationsAllowed = notificationsAllowed,
                    saving = saving,
                    modifier = contentModifier,
                    onEnable = {
                        if (Build.VERSION.SDK_INT >= 33 && !permissionRequested &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionRequested = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val settingsIntent = if (Build.VERSION.SDK_INT >= 26) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            } else {
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            }
                            context.startActivity(settingsIntent)
                        }
                    },
                    onRead = { save(book.markRead(it), "Notification marked as read.", false) },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    book: ReportBook, modifier: Modifier, saving: Boolean,
    onLost: () -> Unit, onFound: () -> Unit, onMatch: (String) -> Unit,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Help an item find its way home", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Report what you lost or share what you found.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLost, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Report a lost item") }
            OutlinedButton(onClick = onFound, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Report a found item") }
        }
        item {
            val open = book.reports.count { it.kind == ReportKind.LOST && it.foundReportId == null }
            val found = book.reports.count { it.kind == ReportKind.FOUND }
            Text("$open open lost reports  ·  $found found reports", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(16.dp))
            Text("Recent reports", style = MaterialTheme.typography.titleLarge)
        }
        if (book.reports.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("No reports yet", style = MaterialTheme.typography.titleMedium)
                    Text("Add your first lost or found item using the buttons above.")
                }
            }
        }
        items(book.reports, key = { it.id }) { report ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(when {
                        report.kind == ReportKind.FOUND -> "Found item"
                        report.foundReportId != null -> "Lost item · reported found"
                        else -> "Lost item · still missing"
                    }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    if (report.photoPath != null) ItemPhoto(report.photoPath)
                    Text(report.description.ifBlank { "Found item" }, style = MaterialTheme.typography.titleMedium)
                    Text("${if (report.kind == ReportKind.LOST) "Last seen" else "Found at"}: ${report.location}")
                    Text(report.createdAt.displayDate(), style = MaterialTheme.typography.bodySmall)
                    if (report.kind == ReportKind.LOST && report.foundReportId == null) {
                        OutlinedButton(onClick = { onMatch(report.id) }, enabled = !saving) { Text("I found this item") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportForm(
    kind: ReportKind, book: ReportBook, initialLostId: String?, saving: Boolean, modifier: Modifier,
    onSubmit: (ReportKind, String, String, String?, String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var description by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var lostId by rememberSaveable { mutableStateOf(initialLostId) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var choosingMatch by rememberSaveable { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val lost = kind == ReportKind.LOST
    val openReports = book.reports.filter { it.kind == ReportKind.LOST && it.foundReportId == null }
    val selectedReport = openReports.firstOrNull { it.id == lostId }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            importing = true
            error = null
            scope.launch {
                try {
                    photo = withContext(Dispatchers.IO) { importPhoto(context, uri) }
                } catch (failure: Exception) {
                    error = failure.message ?: "Could not add this photo. Please choose another image."
                } finally {
                    importing = false
                }
            }
        }
    }
    val busy = saving || importing
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (lost) "What did you lose?" else "What did you find?", style = MaterialTheme.typography.headlineSmall)
        Text(if (lost) "A clear description and last known location help identify your item."
            else "Add a clear photo and the location where you found the item.")
        Text(if (lost) "Photo (optional)" else "Photo (required)", style = MaterialTheme.typography.titleMedium)
        if (photo != null) ItemPhoto(photo!!)
        OutlinedButton(
            onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !busy, modifier = Modifier.fillMaxWidth(),
        ) { Text(if (importing) "Adding photo…" else if (photo == null) "Choose a photo" else "Replace photo") }
        if (photo != null && !busy) TextButton(onClick = { photo = null }) { Text("Remove photo") }
        if (!lost && attempted && photo == null) Text("A photo is required for a found item.", color = MaterialTheme.colorScheme.error)
        Text("Choose an image up to 10 MB.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text(if (lost) "Description (required)" else "Description (optional)") },
            placeholder = { Text("Color, type, brand, and distinctive details") },
            isError = lost && attempted && description.isBlank(),
            supportingText = { if (lost && attempted && description.isBlank()) Text("Describe the item you lost.") },
            minLines = 3, enabled = !busy, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = location, onValueChange = { location = it },
            label = { Text(if (lost) "Last known location (required)" else "Found location (required)") },
            placeholder = { Text("For example, library, second floor") },
            isError = attempted && location.isBlank(),
            supportingText = { if (attempted && location.isBlank()) Text("Enter the item's location.") },
            minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth(),
        )
        if (!lost) {
            Text("Does this match a lost report?", style = MaterialTheme.typography.titleMedium)
            Text("Select a report only if you recognize the item. This adds an item-found alert.")
            OutlinedButton(onClick = { choosingMatch = true }, enabled = !busy && openReports.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(selectedReport?.description ?: "No matching report selected")
            }
            if (openReports.isEmpty()) Text("No open lost reports. You can still report this found item.", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                attempted = true
                error = null
                if (location.isNotBlank() && (!lost || description.isNotBlank()) && (lost || photo != null)) {
                    try { onSubmit(kind, description, location, photo, lostId) }
                    catch (failure: IllegalArgumentException) { error = failure.message }
                }
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth(),
        ) { Text(if (saving) "Saving…" else if (lost) "Submit lost report" else "Submit found report") }
    }
    if (choosingMatch) AlertDialog(
        onDismissRequest = { choosingMatch = false },
        title = { Text("Select a lost report") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { TextButton(onClick = { lostId = null; choosingMatch = false }) { Text("No matching report") } }
                items(openReports, key = { it.id }) { report ->
                    OutlinedButton(onClick = { lostId = report.id; choosingMatch = false }, modifier = Modifier.fillMaxWidth()) {
                        Column { Text(report.description); Text("Last seen: ${report.location}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosingMatch = false }) { Text("Cancel") } },
    )
}

@Composable
private fun AlertsScreen(
    book: ReportBook, notificationsAllowed: Boolean, saving: Boolean, modifier: Modifier,
    onEnable: () -> Unit, onRead: (String) -> Unit,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Item-found updates", style = MaterialTheme.typography.headlineSmall)
            Text("An alert appears when a found item is linked to a lost report.")
            Spacer(Modifier.height(12.dp))
            if (notificationsAllowed) Text("Android notifications are enabled.", color = MaterialTheme.colorScheme.primary)
            else {
                Text("Android notifications are off. Your alerts are still saved here.")
                OutlinedButton(onClick = onEnable) { Text("Enable notifications") }
            }
        }
        if (book.alerts.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("No updates yet", style = MaterialTheme.typography.titleMedium)
                    Text("When a found report matches a lost item, its photo and location will appear here.")
                }
            }
        }
        items(book.alerts, key = { it.id }) { alert ->
            val lost = book.reports.firstOrNull { it.id == alert.lostReportId }
            val found = book.reports.firstOrNull { it.id == alert.foundReportId }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (alert.read) "Item reported found" else "New · item reported found", style = MaterialTheme.typography.titleMedium)
                    Text(lost?.description ?: "Lost item")
                    found?.photoPath?.let { ItemPhoto(it) }
                    Text("Found at: ${found?.location ?: "Unknown location"}")
                    if (!found?.description.isNullOrBlank()) Text("Finder's description: ${found?.description}")
                    Text("Check the photo and details to confirm it is your item.")
                    Text(alert.createdAt.displayDate(), style = MaterialTheme.typography.bodySmall)
                    if (!alert.read) TextButton(onClick = { onRead(alert.id) }, enabled = !saving) { Text("Mark as read") }
                }
            }
        }
    }
}

@Composable
private fun ItemPhoto(path: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                while (bounds.outWidth / options.inSampleSize > 1000 || bounds.outHeight / options.inSampleSize > 1000) {
                    options.inSampleSize *= 2
                }
                BitmapFactory.decodeFile(path, options)
            }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) Image(
        bitmap = image.asImageBitmap(), contentDescription = "Reported item photo",
        modifier = Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit,
    ) else Text("Photo preview unavailable", style = MaterialTheme.typography.bodySmall)
}

/** Copy selected images into private storage so saved reports do not lose access after restart. */
private fun importPhoto(context: android.content.Context, uri: Uri): String {
    val directory = File(context.filesDir, "report_photos").apply { mkdirs() }
    val file = File(directory, "${UUID.randomUUID()}.image")
    try {
        val input = context.contentResolver.openInputStream(uri) ?: error("Could not open this photo.")
        input.use { source ->
            file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 10 * 1024 * 1024) { "This photo is larger than 10 MB. Choose a smaller image." }
                    output.write(buffer, 0, count)
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This file could not be read as a photo. Choose another image." }
        return file.absolutePath
    } catch (error: Exception) {
        file.delete()
        throw error
    }
}

private fun Long.displayDate(): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))
