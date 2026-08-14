// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.ui.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeView(
    activeParcels: List<ParcelWithStatus>,
    archivedParcels: List<ParcelWithStatus>,
    archivedExpanded: Boolean,
    onArchivedExpandedChange: (Boolean) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNavigateToAddParcel: () -> Unit,
    onNavigateToParcel: (Parcel) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  var menuExpanded by remember { mutableStateOf(false) }
  var aboutDialogOpen by remember { mutableStateOf(false) }
  val motionScheme = MaterialTheme.motionScheme
  val chevronRotation by
      animateFloatAsState(
          targetValue = if (archivedExpanded) 90f else 0f,
          animationSpec = motionScheme.defaultSpatialSpec(),
          label = "archiveChevronRotation",
      )

  Scaffold(
      topBar = {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            scrollBehavior = scrollBehavior,
            actions = {
              IconButton(onClick = { menuExpanded = !menuExpanded }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
              }
              DropdownMenu(
                  expanded = menuExpanded,
                  onDismissRequest = { menuExpanded = false },
              ) {
                DropdownMenuItem(
                    leadingIcon = {
                      Icon(Icons.Filled.Settings, stringResource(R.string.settings))
                    },
                    text = { Text(stringResource(R.string.settings)) },
                    onClick = {
                      menuExpanded = false
                      onNavigateToSettings()
                    },
                    contentPadding = MenuItemContentPadding,
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Filled.Info, stringResource(R.string.about_app)) },
                    text = { Text(stringResource(R.string.about_app)) },
                    onClick = {
                      menuExpanded = false
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
            if (activeParcels.isEmpty() && archivedParcels.isEmpty()) {
              item {
                Text(
                    stringResource(R.string.no_parcels_flavor),
                    modifier = Modifier.padding(horizontal = 16.dp))
              }
            }

            items(activeParcels.reversed(), key = { it.parcel.id }) { parcel ->
              ParcelRow(parcel.parcel, parcel.status?.status) { onNavigateToParcel(parcel.parcel) }
            }

            if (archivedParcels.isNotEmpty()) {
              item {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { onArchivedExpandedChange(!archivedExpanded) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                      "${stringResource(R.string.archive)} (${archivedParcels.size})",
                  )
                  Icon(
                      Icons.AutoMirrored.Filled.KeyboardArrowRight,
                      contentDescription = null,
                      modifier = Modifier.rotate(chevronRotation),
                  )
                }
              }

              item {
                AnimatedVisibility(
                    visible = archivedExpanded,
                    enter =
                        expandVertically(
                            expandFrom = Alignment.Top,
                            animationSpec = motionScheme.defaultSpatialSpec(),
                        ) +
                            fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
                    exit =
                        shrinkVertically(
                            shrinkTowards = Alignment.Top,
                            animationSpec = motionScheme.defaultSpatialSpec(),
                        ) +
                            fadeOut(animationSpec = motionScheme.defaultEffectsSpec()),
                ) {
                  Column {
                    archivedParcels.reversed().forEach { parcel ->
                      ParcelRow(parcel.parcel, parcel.status?.status) {
                        onNavigateToParcel(parcel.parcel)
                      }
                    }
                  }
                }
              }
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
        activeParcels =
            listOf(
                ParcelWithStatus(
                    Parcel(0, "My precious package", "EXMPL0001", null, Service.EXAMPLE),
                    ParcelStatus(0, Status.InTransit, Instant.now()))),
        archivedParcels = emptyList(),
        archivedExpanded = false,
        onArchivedExpandedChange = {},
        isRefreshing = false,
        onRefresh = {},
        onNavigateToAddParcel = {},
        onNavigateToParcel = {},
        onNavigateToSettings = {},
    )
  }
}
