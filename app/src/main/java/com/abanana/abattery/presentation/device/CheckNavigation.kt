package com.abanana.abattery.presentation.device

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun CheckNavigation(page: String, navigate: (String) -> Unit) {
    val selected =
        when {
            page == "Dashboard" -> "Dashboard"
            page == "Device" || page in deviceTabs || page == "Battery details" -> "Device"
            page == "Tests" || page in testNames -> "Tests"
            else -> "Tools"
        }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets(0, 0, 0, 0),
        tonalElevation = 0.dp,
    ) {
        listOf("Dashboard", "Device", "Tests", "Tools").forEach { name ->
            NavigationBarItem(
                selected = selected == name,
                onClick = { navigate(name) },
                icon = {
                    Icon(
                        when (name) {
                            "Dashboard" -> Icons.Default.SpaceDashboard
                            "Device" -> Icons.Default.PhoneAndroid
                            "Tests" -> Icons.Default.FactCheck
                            else -> Icons.Default.Handyman
                        },
                        null,
                    )
                },
                label = { DeviceText(name, maxLines = 1) },
            )
        }
    }
}

@Composable
internal fun DeviceCategories(page: String, navigate: (String) -> Unit) {
    val categories = deviceTabs.drop(1)
    val index = categories.indexOf(page).coerceAtLeast(0)
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = index)
    LaunchedEffect(page) { scroll.animateScrollToItem(index) }
    LazyRow(
        state = scroll,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it }) { name ->
            FilterChip(
                selected = page == name,
                onClick = { navigate(name) },
                label = { DeviceText(name) },
                leadingIcon = {
                    Icon(checkIcon(name), null, Modifier.size(FilterChipDefaults.IconSize))
                },
                shape = RoundedCornerShape(50),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
        }
    }
}

@Composable
internal fun DeviceLibrary(navigate: (String) -> Unit) {
    val descriptions =
        mapOf(
            "Hardware" to "Processor, graphics, display and memory",
            "System" to "Android, build and device identity",
            "Battery" to "Health, cycles and charging",
            "Network" to "Connections and network configuration",
            "Apps" to "Installed apps and permissions",
            "Camera" to "Camera capabilities and preview",
            "Sensors" to "Live readings and sensor details",
        )
    LazyColumn(
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenIntro("Device information", "Explore the details your device makes available.") }
        item {
            ListGroup(
                null,
                deviceTabs.drop(1).map { name ->
                    GroupEntry(name, descriptions.getValue(name)) { navigate(name) }
                },
            )
        }
    }
}

/** Page heading with a one-line explanation, used at the top of list screens. */
@Composable
internal fun ScreenIntro(title: String, description: String) {
    Column(
        Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DeviceText(title, style = MaterialTheme.typography.headlineMedium)
        DeviceText(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
