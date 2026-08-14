// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.ui.views

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.api.Service
import dev.itsvic.parceltracker.api.Status
import dev.itsvic.parceltracker.db.Parcel
import dev.itsvic.parceltracker.db.ParcelStatus
import dev.itsvic.parceltracker.db.ParcelWithStatus
import dev.itsvic.parceltracker.ui.components.AboutDialog
import dev.itsvic.parceltracker.ui.components.ParcelRow
import dev.itsvic.parceltracker.ui.theme.MenuItemContentPadding
import dev.itsvic.parceltracker.ui.theme.ParcelTrackerTheme
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(
    parcels: List<ParcelWithStatus>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNavigateToAddParcel: () -> Unit,
    onNavigateToParcel: (Parcel) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  var expanded by remember { mutableStateOf(false) }
  var aboutDialogOpen by remember { mutableStateOf(false) }

  Scaffold(
      topBar = {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            scrollBehavior = scrollBehavior,
            actions = {
              IconButton(onClick = { expanded = !expanded }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
              }
              DropdownMenu(
                  expanded = expanded,
                  onDismissRequest = { expanded = false },
              ) {
                DropdownMenuItem(
                    leadingIcon = {
                      Icon(Icons.Filled.Settings, stringResource(R.string.settings))
                    },
                    text = { Text(stringResource(R.string.settings)) },
                    onClick = {
                      expanded = false
                      onNavigateToSettings()
                    },
                    contentPadding = MenuItemContentPadding,
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Filled.Info, stringResource(R.string.about_app)) },
                    text = { Text(stringResource(R.string.about_app)) },
                    onClick = {
                      expanded = false
                      aboutDialogOpen = true
                    },
                    contentPadding = MenuItemContentPadding,
                )
              }
            },
        )
      },
      floatingActionButton = {
        FloatingActionButton(onClick = onNavigateToAddParcel) {
          Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_a_parcel))
        }
      },
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
          LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (parcels.isEmpty()) {
              item {
                Text(
                    stringResource(R.string.no_parcels_flavor),
                    modifier = Modifier.padding(horizontal = 16.dp))
              }
            }

            items(parcels.reversed()) { parcel ->
              ParcelRow(parcel.parcel, parcel.status?.status) { onNavigateToParcel(parcel.parcel) }
            }
          }
        }

        if (aboutDialogOpen) {
          AboutDialog { aboutDialogOpen = false }
        }
      }
}

@Composable
@PreviewLightDark
fun HomeViewPreview() {
  ParcelTrackerTheme {
    HomeView(
        parcels =
            listOf(
                ParcelWithStatus(
                    Parcel(0, "My precious package", "EXMPL0001", null, Service.EXAMPLE),
                    ParcelStatus(0, Status.InTransit, Instant.now()))),
        isRefreshing = false,
        onRefresh = {},
        onNavigateToAddParcel = {},
        onNavigateToParcel = {},
        onNavigateToSettings = {},
    )
  }
}
