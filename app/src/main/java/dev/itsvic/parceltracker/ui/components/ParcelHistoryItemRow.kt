// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.itsvic.parceltracker.api.ParcelHistoryItem
import dev.itsvic.parceltracker.ui.redactedText
import dev.itsvic.parceltracker.ui.theme.ParcelTrackerTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ParcelHistoryItemRow(item: ParcelHistoryItem) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    SelectionContainer { Text(item.description, color = MaterialTheme.colorScheme.onBackground) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
          item.time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)),
          fontSize = 13.sp,
          lineHeight = 19.5f.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(
          redactedText(item.location),
          fontSize = 13.sp,
          lineHeight = 19.5f.sp,
          textAlign = TextAlign.End,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
@PreviewLightDark
private fun ParcelHistoryItemRowPreview() {
  val exampleItem =
      ParcelHistoryItem(
          "Customs service",
          LocalDateTime.of(2024, 12, 22, 9, 38, 48),
          "ZCPP\nul. Rodziny Hiszpańskich 8\n00-940 Warszawa")

  ParcelTrackerTheme {
    Box(modifier = Modifier.background(color = MaterialTheme.colorScheme.background)) {
      ParcelHistoryItemRow(exampleItem)
    }
  }
}
