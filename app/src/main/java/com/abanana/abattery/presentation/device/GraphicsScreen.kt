package com.abanana.abattery.presentation.device

import androidx.compose.runtime.*
import com.abanana.abattery.data.device.*
import com.abanana.abattery.diagnostics.GraphicsInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GraphicsScreen() {
    var sections by remember { mutableStateOf<List<DeviceSection>>(emptyList()) }
    LaunchedEffect(Unit) { sections = withContext(Dispatchers.IO) { GraphicsInfo.sections } }
    SectionList(sections)
}
