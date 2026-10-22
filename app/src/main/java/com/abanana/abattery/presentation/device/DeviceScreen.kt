package com.abanana.abattery.presentation.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abanana.abattery.data.device.*
import com.abanana.abattery.diagnostics.*
import com.abanana.abattery.presentation.battery.BatteryScreen
import com.abanana.abattery.presentation.battery.BatteryViewModel
import com.abanana.abattery.presentation.locale.LanguagePickerDialog
import com.abanana.abattery.ui.theme.ThemeMode
import java.util.Date
import kotlinx.coroutines.*

val deviceTabs =
    listOf("Dashboard", "Hardware", "System", "Battery", "Network", "Apps", "Camera", "Sensors")

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
fun DeviceScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    viewModel: DeviceViewModel = hiltViewModel(),
    batteryViewModel: BatteryViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val battery by batteryViewModel.batteryInfo.collectAsStateWithLifecycle()
    val reportSnapshot = snapshot.withBattery(battery).withObservations(LocalContext.current)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var page by rememberSaveable { mutableStateOf("Dashboard") }
    var backStack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var menu by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var language by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf("Text") }
    var message by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.start()
                batteryViewModel.startSampling()
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stop()
                batteryViewModel.stopSampling()
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.start()
            batteryViewModel.startSampling()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.stop()
            batteryViewModel.stopSampling()
        }
    }
    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            if (uri != null)
                scope.launch {
                    message =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                    ReportExporter.write(context, uri, reportSnapshot, exportFormat)
                                    "Report exported"
                                }
                                .getOrElse { "Export failed: ${it.message}" }
                        }
                }
        }
    fun navigate(destination: String) {
        if (destination == page) return
        if (destination == "Dashboard") backStack = emptyList() else backStack = backStack + page
        page = destination
        searching = false
        query = ""
    }
    fun goBack() {
        val destination = backStack.lastOrNull() ?: "Dashboard"
        backStack = backStack.dropLast(1)
        page = destination
    }
    BackHandler(page != "Dashboard" || searching) {
        if (searching) {
            searching = false
            query = ""
        } else goBack()
    }
    LanguagePickerDialog(language) { language = false }
    if (message != null)
        AlertDialog(
            onDismissRequest = { message = null },
            title = { DeviceText("abCheck") },
            text = { DeviceText(message.orEmpty()) },
            confirmButton = { TextButton(onClick = { message = null }) { DeviceText("OK") } },
        )
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .displayCutoutPadding()
    ) {
        TopAppBar(
            title = {
                if (page == "Dashboard")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconBadge(Icons.Default.FactCheck, 34.dp)
                        Column {
                            DeviceText(
                                "abCheck",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                            )
                            DeviceText(
                                "Device & System",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                else
                    DeviceText(
                        page,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
            },
            navigationIcon = {
                if (page != "Dashboard")
                    IconButton(onClick = ::goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
            },
            actions = {
                if (page in listOf("Hardware", "System", "Network", "Camera", "Apps"))
                    IconButton(
                        onClick = {
                            searching = !searching
                            query = ""
                        }
                    ) {
                        Icon(
                            if (searching) Icons.Default.SearchOff else Icons.Default.Search,
                            "Search information",
                        )
                    }
                IconButton(
                    onClick = {
                        onThemeModeChange(
                            when (themeMode) {
                                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.DARK
                                ThemeMode.DARK -> ThemeMode.SYSTEM
                            }
                        )
                    }
                ) {
                    Icon(
                        when (themeMode) {
                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK -> Icons.Default.DarkMode
                        },
                        "Change theme",
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, "More options")
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { DeviceText("About") },
                            onClick = {
                                menu = false
                                message =
                                    "abCheck · Device & System\nLocal diagnostics. Device support varies.\nVersion ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}"
                            },
                        )
                        DropdownMenuItem(
                            text = { DeviceText("Privacy policy") },
                            onClick = {
                                menu = false
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            "https://github.com/abanana84/abCheck/blob/main/PRIVACY.md"
                                        ),
                                    )
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { DeviceText("Language") },
                            onClick = {
                                menu = false
                                language = true
                            },
                        )
                        DropdownMenuItem(
                            text = { DeviceText("Device report") },
                            onClick = {
                                menu = false
                                navigate("Device report")
                            },
                        )
                        DropdownMenuItem(
                            text = { DeviceText("Share report") },
                            onClick = {
                                menu = false
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND)
                                            .setType("text/plain")
                                            .putExtra(
                                                Intent.EXTRA_TEXT,
                                                ReportExporter.text(reportSnapshot),
                                            ),
                                        "Share device report",
                                    )
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { DeviceText("Settings") },
                            onClick = {
                                menu = false
                                navigate("Settings")
                            },
                        )
                    }
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        if (page in deviceTabs && page != "Dashboard") {
            DeviceCategories(page, ::navigate)
        }
        if (searching)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                singleLine = true,
                shape = RoundedCornerShape(50),
                placeholder = { DeviceText("Search information") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty())
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Close") }
                },
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        error?.let {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    DeviceText(it, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            @Composable
            fun PageContent(destination: String) {
                when (destination) {
                    "Dashboard" ->
                        Dashboard(
                            snapshot.withBattery(battery),
                            apps,
                            battery?.percent,
                            battery?.temperatureCelsius,
                            ::navigate,
                        )
                    "Battery" -> BatteryOverview(battery) { navigate("Battery details") }
                    "Battery details" ->
                        BatteryScreen(
                            viewModel = batteryViewModel,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            showTopBar = false,
                        )
                    "Hardware",
                    "System",
                    "Network",
                    "Camera" ->
                        SectionList(
                            snapshot.pages[destination]
                                .orEmpty()
                                .map { section ->
                                    section.copy(
                                        fields =
                                            section.fields.filter {
                                                query.isBlank() ||
                                                    section.title.contains(query, true) ||
                                                    deviceSearchMatches(it, query)
                                            }
                                    )
                                }
                                .filter { it.fields.isNotEmpty() },
                            header = {
                                if (destination == "Camera")
                                    ActionTile("Camera test", "Open live preview") {
                                        navigate("Camera test")
                                    }
                                if (destination == "Network")
                                    ActionTile(
                                        "Network tools",
                                        "Wi-Fi, Bluetooth, GPS and connectivity",
                                    ) {
                                        navigate("Tools")
                                    }
                            },
                        )
                    "Sensors" -> SensorScreen()
                    "Apps" -> AppsScreen(apps, viewModel::refreshApps, query)
                    "Device" -> DeviceLibrary(::navigate)
                    "Tests" -> TestsScreen { navigate(it) }
                    "Tools" -> ToolsScreen { navigate(it) }
                    "Benchmarks" -> BenchmarkScreen()
                    "Monitors" -> MonitorScreen(snapshot, battery?.temperatureCelsius)
                    "Widgets" -> WidgetScreen()
                    "Battery monitor" -> BatteryHistoryScreen()
                    "Device report" ->
                        LazyColumn(
                            contentPadding = ScreenPadding,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                ScreenIntro(
                                    "Export a device report",
                                    "Reports include hardware and network details visible to this app. Review before sharing.",
                                )
                            }
                            item {
                                ListGroup(
                                    null,
                                    listOf("Text", "JSON", "HTML", "PDF", "PNG").map { format ->
                                        GroupEntry("Export $format", "Choose where to save") {
                                            exportFormat = format
                                            exporter.launch(
                                                "abCheck-${System.currentTimeMillis()}.${when(format) { "JSON" -> "json"
 "HTML" -> "html"
 "PDF" -> "pdf"
 "PNG" -> "png"
 else -> "txt" }}"
                                            )
                                        }
                                    },
                                )
                            }
                            item {
                                ListGroup(
                                    null,
                                    listOf(
                                        GroupEntry("Copy quick specs", "Copy device, OS and memory") {
                                            context
                                                .getSystemService(ClipboardManager::class.java)
                                                .setPrimaryClip(
                                                    ClipData.newPlainText(
                                                        "Device specs",
                                                        "${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE}\nRAM ${formatBytes(snapshot.ramTotal)}\nStorage ${formatBytes(snapshot.storageTotal)}",
                                                    )
                                                )
                                            message = "Copied to clipboard"
                                        }
                                    ),
                                )
                            }
                        }
                    "Settings" -> SettingsScreen(themeMode, onThemeModeChange, { language = true })
                    else ->
                        Column {
                            Box(Modifier.weight(1f)) {
                                DiagnosticScreen(destination, viewModel.repository, apps, snapshot)
                            }
                            if (destination in testNames) DiagnosticResultBar(destination)
                        }
                }
            }
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
                label = "Destination",
            ) { destination ->
                pageState.SaveableStateProvider(destination) { PageContent(destination) }
            }
        }
        CheckNavigation(page) { destination ->
            if (destination != page) {
                navigate(destination)
                backStack = if (destination == "Dashboard") emptyList() else listOf("Dashboard")
            }
        }
    }
}

@Composable
fun SectionHeading(title: String) {
    DeviceText(title, style = MaterialTheme.typography.titleLarge)
}

@Composable
fun SectionList(sections: List<DeviceSection>, header: @Composable () -> Unit = {}) {
    DataSections(sections, header)
}

@Composable
fun ActionTile(title: String, subtitle: String, onClick: () -> Unit) {
    CheckActionRow(title, subtitle, onClick)
}

@Composable
private fun Dashboard(
    snapshot: DeviceSnapshot,
    apps: List<InstalledApp>,
    percent: Int?,
    temperature: Float?,
    navigate: (String) -> Unit,
) {
    CheckDashboard(snapshot, apps, percent, temperature, navigate)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsScreen(apps: List<InstalledApp>, refresh: () -> Unit, query: String) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf("User") }
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    val filtered =
        apps.filter {
            (filter == "All" || it.system == (filter == "System")) &&
                (it.label.contains(query, true) || it.packageName.contains(query, true))
        }
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            val options = listOf("User", "System", "All")
            options.forEachIndexed { index, name ->
                SegmentedButton(
                    selected = filter == name,
                    onClick = { filter = name },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { DeviceText("$name apps", maxLines = 1) },
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = ScreenPadding,
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeviceText(
                            "${deviceString(filter)} apps (${filtered.size})",
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        IconButton(onClick = refresh) {
                            Icon(
                                Icons.Default.Refresh,
                                "Refresh apps",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            items(filtered, key = { it.packageName }) { app ->
                AppListRow(app, app == filtered.lastOrNull()) { selected = app }
            }
        }
    }
    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { DeviceText(app.label) },
            text = {
                LazyColumn {
                    item {
                        SelectionContainer {
                            DeviceText(
                                "${app.packageName}\nVersion ${app.version}\nMin API ${app.minSdk} · Target API ${app.targetSdk}\nInstalled ${Date(app.installed)}\nUpdated ${Date(app.updated)}\nEnabled ${app.enabled}\n${app.sourcePath}\n\nPermissions"
                            )
                        }
                    }
                    items(app.permissions) { (name, granted) ->
                        DeviceText(
                            "${if (granted) "✓" else "–"} $name",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${app.packageName}"),
                            )
                        )
                    }
                ) {
                    DeviceText("App settings")
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { DeviceText("Close") } },
        )
    }
}

@Composable
private fun SettingsScreen(mode: ThemeMode, change: (ThemeMode) -> Unit, language: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ListGroup(
                "Appearance",
                ThemeMode.entries.map { m ->
                    GroupEntry(
                        "${m.name.lowercase().replaceFirstChar { it.uppercase() }} theme",
                        icon =
                            when (m) {
                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.DarkMode
                            },
                        selected = mode == m,
                    ) {
                        change(m)
                    }
                } + GroupEntry("Language", "Choose app language") { language() },
            )
        }
        item {
            ListGroup(
                "Android access",
                listOf(
                    GroupEntry("App permissions", "Camera, microphone, nearby devices and location") {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    },
                    GroupEntry("Usage access", "Screen time and app usage") {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    GroupEntry("Display over other apps", "Floating monitors") {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    },
                ),
            )
        }
        item {
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.Lock,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DeviceText(
                    "Diagnostics run locally. Permissions are requested when a tool needs them. Internet is used only by connectivity tools you start. No analytics or advertising SDKs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
