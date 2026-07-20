// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.api.Parcel
import dev.itsvic.parceltracker.api.ParcelHistoryItem
import dev.itsvic.parceltracker.api.Service
import dev.itsvic.parceltracker.api.Status
import dev.itsvic.parceltracker.api.getDeliveryServiceName
import dev.itsvic.parceltracker.ui.components.ParcelHistoryItemRow
import dev.itsvic.parceltracker.ui.theme.MenuItemContentPadding
import dev.itsvic.parceltracker.ui.theme.ParcelTrackerTheme
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParcelView(
    parcel: Parcel,
    humanName: String,
    service: Service,
    isArchived: Boolean,
    archivePromptDismissed: Boolean,
    onBackPressed: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onArchivePromptDismissal: () -> Unit,
) {
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  var expanded by remember { mutableStateOf(false) }

  Scaffold(
      topBar = {
        MediumTopAppBar(
            title = { Text(humanName) },
            navigationIcon = {
              IconButton(onClick = onBackPressed) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back))
              }
            },
            actions = {
              IconButton(onClick = { expanded = !expanded }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
              }
              DropdownMenu(
                  expanded = expanded,
                  onDismissRequest = { expanded = false },
              ) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) },
                    text = { Text(stringResource(R.string.edit)) },
                    onClick = {
                      expanded = false
                      onEdit()
                    },
                    contentPadding = MenuItemContentPadding,
                )
                if (!isArchived)
                    DropdownMenuItem(
                        leadingIcon = {
                          Icon(
                              painterResource(R.drawable.archive), stringResource(R.string.archive))
                        },
                        text = { Text(stringResource(R.string.archive)) },
                        onClick = onArchive,
                        contentPadding = MenuItemContentPadding,
                    )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) },
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = onDelete,
                    contentPadding = MenuItemContentPadding,
                )
              }
            },
            scrollBehavior = scrollBehavior,
        )
      },
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).padding(16.dp, 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                  getDeliveryServiceName(service)?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }

                  SelectionContainer {
                    Text(
                        parcel.id,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
          }

          items(parcel.properties.entries.toList()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(
                      stringResource(it.key),
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(
                      it.value,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      textAlign = TextAlign.End)
                }
          }

          item {
            Text(
                stringResource(parcel.currentStatus.nameResource),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(vertical = 16.dp),
            )
          }

          if (!isArchived &&
              !archivePromptDismissed &&
              (parcel.currentStatus == Status.Delivered || parcel.currentStatus == Status.PickedUp))
              item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(bottom = 16.dp)) {
                      Column(
                          Modifier.padding(24.dp),
                          verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.archive_prompt_question),
                                style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.archive_prompt_text))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()) {
                                  FilledTonalButton(
                                      onArchivePromptDismissal, modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.ignore))
                                      }
                                  Button(onArchive, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.archive))
                                  }
                                }
                          }
                    }
              }

          items(parcel.history.size) { index ->
            if (index > 0) HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 16.dp))
            ParcelHistoryItemRow(parcel.history[index])
          }
        }
      }
}

@Composable
@PreviewLightDark
private fun ParcelViewPreview() {
  val parcel =
      Parcel(
          "EXMPL0001",
          listOf(
              ParcelHistoryItem(
                  "The package got lost. Whoops!",
                  LocalDateTime.of(2025, 1, 1, 12, 0, 0),
                  "Warsaw, Poland"),
              ParcelHistoryItem(
                  "Arrived at local warehouse",
                  LocalDateTime.of(2025, 1, 1, 10, 0, 0),
                  "Warsaw, Poland"),
              ParcelHistoryItem(
                  "En route to local warehouse",
                  LocalDateTime.of(2024, 12, 1, 12, 0, 0),
                  "Netherlands"),
              ParcelHistoryItem(
                  "Label created", LocalDateTime.of(2024, 12, 1, 12, 0, 0), "Netherlands"),
          ),
          Status.DeliveryFailure)
  ParcelTrackerTheme {
    ParcelView(
        parcel,
        "My precious package",
        Service.EXAMPLE,
        isArchived = false,
        archivePromptDismissed = false,
        onBackPressed = {},
        onEdit = {},
        onDelete = {},
        onArchive = {},
        onArchivePromptDismissal = {},
    )
  }
}
