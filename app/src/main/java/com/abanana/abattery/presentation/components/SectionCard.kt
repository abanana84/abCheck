package com.abanana.abattery.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.abanana.abattery.ui.theme.AppTheme

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    color: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bg = color ?: AppTheme.colors.surfaceLow
    Surface(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        shape = RoundedCornerShape(20.dp),
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                com.abanana.abattery.ui.theme.AppTheme.colors.outlineVariant,
            ),
        color = bg,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(20.dp), content = content)
    }
}
