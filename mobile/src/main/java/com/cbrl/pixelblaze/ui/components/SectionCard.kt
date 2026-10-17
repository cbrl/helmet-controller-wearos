package com.cbrl.pixelblaze.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cbrl.pixelblaze.ui.theme.HelmetText

val SectionShape = RoundedCornerShape(20.dp)

/** A flat panel with an optional heading, and actions on the right of the heading. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier.fillMaxWidth().panel(SectionShape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SectionLabel(title, Modifier.weight(1f), line = true)
                    actions()
                }
            }
            content()
        }
    }
}

/**
 * A small heading in wide capitals. With [line], a hairline runs from the heading to the end of the
 * space, as the light lines of the Alive 2007 stage.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, line: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = HelmetText.section,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (line) {
            Box(
                Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
                    .height(1.dp)
                    .background(Hairline),
            )
        }
    }
}
