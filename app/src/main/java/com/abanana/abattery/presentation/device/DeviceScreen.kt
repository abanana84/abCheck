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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.flow.drop

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
    var page by rememberSaveable { mutableStateOf("Dashboard") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
        if (destination == "Dashboard") backStack = emptyList()
        else if (destination !in deviceTabs && destination != page) backStack = backStack + page
        page = destination
        searching = false
        query = ""
        if (destination in deviceTabs) tab = deviceTabs.indexOf(destination)
    }
    fun goBack() {
        val destination = backStack.lastOrNull() ?: "Dashboard"
        backStack = backStack.dropLast(1)
        page = destination
        if (destination in deviceTabs) tab = deviceTabs.indexOf(destination)
    }
    val pagerState = rememberPagerState(initialPage = tab) { deviceTabs.size }
    LaunchedEffect(tab) { if (pagerState.currentPage != tab) pagerState.animateScrollToPage(tab) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { index ->
                if (page in deviceTabs && tab != index) navigate(deviceTabs[index])
            }
    }
    BackHandler(page != "Dashboard") { goBack() }
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
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .displayCutoutPadding()
    ) {
        CenterAlignedTopAppBar(
            title = {
                DeviceText(
                    if (page in deviceTabs) "abCheck" else page,
                    fontWeight = FontWeight.Medium,
                    fontSize = 25.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (page !in deviceTabs) goBack()
                        else
                            message =
                                "abCheck · Device & system information\nLocal diagnostics. Device support varies.\nVersion ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}"
                    }
                ) {
                    Icon(
                        if (page in deviceTabs) Icons.Default.Info
                        else Icons.AutoMirrored.Filled.ArrowBack,
                        "Back or about",
                        tint = MaterialTheme.colorScheme.primary,
                    )
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
                        Icon(Icons.Default.Search, "Search information")
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
                    Icon(Icons.Default.Brightness6, "Change theme")
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, "More options")
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { DeviceText("Privacy policy") },
                            onClick = {
                                menu = false
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            "https://github.com/abanana84/abattery/blob/main/PRIVACY.md"
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
                TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        if (page in deviceTabs)
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { positions ->
                    androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(positions[tab]),
                        height = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                divider = { HorizontalDivider(thickness = 0.5.dp) },
            ) {
                deviceTabs.forEachIndexed { index, name ->
                    Tab(
                        selected = tab == index,
                        onClick = { navigate(name) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                        content = {
                            Box(
                                Modifier.height(48.dp).padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                DeviceText(name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        },
                    )
                }
            }
        if (searching)
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { DeviceText("Search information") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
            )
        error?.let {
            DeviceText(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
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
                    "Battery" -> ReferenceBattery(battery) { navigate("Battery details") }
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
                    "Tests" -> TestsScreen { navigate(it) }
                    "Tools" -> ToolsScreen { navigate(it) }
                    "Benchmarks" -> BenchmarkScreen()
                    "Monitors" -> MonitorScreen(snapshot, battery?.temperatureCelsius)
                    "Widgets" -> WidgetScreen()
                    "Battery monitor" -> BatteryHistoryScreen()
                    "Device report" ->
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item { SectionHeading("Export a device report") }
                            item {
                                DeviceText(
                                    "Reports include hardware and network details visible to this app. Review before sharing."
                                )
                            }
                            items(listOf("Text", "JSON", "HTML", "PDF", "PNG")) { format ->
                                ActionTile("Export $format", "Choose where to save") {
                                    exportFormat = format
                                    exporter.launch(
                                        "abCheck-${System.currentTimeMillis()}.${when(format) { "JSON" -> "json"
 "HTML" -> "html"
 "PDF" -> "pdf"
 "PNG" -> "png"
 else -> "txt" }}"
                                    )
                                }
                            }
                            item {
                                ActionTile("Copy quick specs", "Copy device, OS and memory") {
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
            if (page in deviceTabs) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                ) { index ->
                    PageContent(deviceTabs[index])
                }
            } else key(page) { PageContent(page) }
        }
    }
}

@Composable
fun SectionHeading(title: String) {
    DeviceText(
        title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun SectionList(sections: List<DeviceSection>, header: @Composable () -> Unit = {}) {
    ReferenceSections(sections, header)
}

@Composable
fun ActionTile(title: String, subtitle: String, onClick: () -> Unit) {
    ReferenceActionRow(title, subtitle, onClick)
}

@Composable
private fun Dashboard(
    snapshot: DeviceSnapshot,
    apps: List<InstalledApp>,
    percent: Int?,
    temperature: Float?,
    navigate: (String) -> Unit,
) {
    ReferenceDashboard(snapshot, apps, percent, temperature, navigate)
}

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
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeviceText(
                            "${deviceString(filter)} apps (${filtered.size})",
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp,
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
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            listOf("User", "System", "All").forEach { name ->
                NavigationBarItem(
                    selected = filter == name,
                    onClick = { filter = name },
                    icon = {
                        Icon(
                            when (name) {
                                "User" -> Icons.Default.Person
                                "System" -> Icons.Default.Android
                                else -> Icons.Default.Apps
                            },
                            null,
                        )
                    },
                    label = { DeviceText(name) },
                )
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeading("Appearance") }
        items(ThemeMode.entries) { m ->
            ActionTile(
                "${m.name.lowercase().replaceFirstChar { it.uppercase() }} theme",
                if (mode == m) "Selected" else "",
            ) {
                change(m)
            }
        }
        item { ActionTile("Language", "Choose app language") { language() } }
        item { SectionHeading("Android access") }
        item {
            ActionTile("App permissions", "Camera, microphone, nearby devices and location") {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            }
        }
        item {
            ActionTile("Usage access", "Screen time and app usage") {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        item {
            ActionTile("Display over other apps", "Floating monitors") {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            }
        }
        item {
            DeviceText(
                "Diagnostics run locally. Permissions are requested when a tool needs them. Internet is used only by connectivity tools you start. No analytics or advertising SDKs.",
                fontSize = 13.sp,
            )
        }
    }
}
