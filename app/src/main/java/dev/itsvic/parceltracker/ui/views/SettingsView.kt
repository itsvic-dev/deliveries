// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.itsvic.parceltracker.BuildConfig
import dev.itsvic.parceltracker.DEMO_MODE
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.UNMETERED_ONLY
import dev.itsvic.parceltracker.allegro.openAllegroNetworkInspector
import dev.itsvic.parceltracker.api.ParcelHistoryItem
import dev.itsvic.parceltracker.api.Service
import dev.itsvic.parceltracker.api.Status
import dev.itsvic.parceltracker.dataStore
import dev.itsvic.parceltracker.db.Parcel
import dev.itsvic.parceltracker.enqueueAccountSyncWorker
import dev.itsvic.parceltracker.enqueueNotificationWorker
import dev.itsvic.parceltracker.sendNotification
import dev.itsvic.parceltracker.ui.components.Material3SettingsGroup
import dev.itsvic.parceltracker.ui.components.Material3SettingsItem
import dev.itsvic.parceltracker.ui.components.Material3SettingsSwitch
import dev.itsvic.parceltracker.ui.components.openLogcatDumper
import dev.itsvic.parceltracker.ui.theme.ParcelTrackerTheme
import java.time.LocalDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    onBackPressed: () -> Unit,
    onNavigateToAccounts: () -> Unit,
) {
  val context = LocalContext.current
  val preferences by context.dataStore.data.collectAsState(emptyPreferences())
  val demoMode = preferences[DEMO_MODE] == true
  val unmeteredOnly = preferences[UNMETERED_ONLY] == true
  val coroutineScope = rememberCoroutineScope()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  fun <T> setValue(key: Preferences.Key<T>, value: T) {
    coroutineScope.launch { context.dataStore.edit { it[key] = value } }
  }

  val setUnmeteredOnly: (Boolean) -> Unit = { value ->
    coroutineScope.launch {
      context.dataStore.edit { it[UNMETERED_ONLY] = value }
      context.enqueueNotificationWorker()
      context.enqueueAccountSyncWorker()
    }
  }

  Scaffold(
      topBar = {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.settings)) },
            navigationIcon = {
              IconButton(onClick = onBackPressed) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back))
              }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            scrollBehavior = scrollBehavior,
        )
      },
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
  ) { innerPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Material3SettingsGroup(
            title = stringResource(R.string.settings_accounts),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = Icons.Filled.Person,
                        title = { Text(stringResource(R.string.manage_accounts)) },
                        description = { Text(stringResource(R.string.settings_accounts_detail)) },
                        trailingContent = {
                          Icon(
                              Icons.AutoMirrored.Filled.KeyboardArrowRight,
                              contentDescription = null,
                              tint = MaterialTheme.colorScheme.onSurfaceVariant,
                              modifier = Modifier.size(24.dp),
                          )
                        },
                        onClick = onNavigateToAccounts,
                    )))
      }

      item {
        Material3SettingsGroup(
            title = stringResource(R.string.settings_updates),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = Icons.Filled.Notifications,
                        title = { Text(stringResource(R.string.unmetered_only_setting)) },
                        description = {
                          Text(stringResource(R.string.unmetered_only_setting_detail))
                        },
                        trailingContent = { Material3SettingsSwitch(unmeteredOnly) },
                        checked = unmeteredOnly,
                        onClick = { setUnmeteredOnly(!unmeteredOnly) },
                    )))
      }

      item {
        Material3SettingsGroup(
            title = stringResource(R.string.settings_misc),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = Icons.Filled.MoreVert,
                        title = { Text(stringResource(R.string.demo_mode)) },
                        description = { Text(stringResource(R.string.demo_mode_detail)) },
                        trailingContent = { Material3SettingsSwitch(demoMode) },
                        checked = demoMode,
                        onClick = { setValue(DEMO_MODE, !demoMode) },
                    )))
      }

      item {
        Material3SettingsGroup(
            title = stringResource(R.string.settings_diagnostics),
            items =
                buildList {
                  if (BuildConfig.DEBUG) {
                    add(
                        Material3SettingsItem(
                            icon = Icons.Filled.Info,
                            title = { Text(stringResource(R.string.open_network_inspector)) },
                            onClick = { openAllegroNetworkInspector(context) },
                        ))
                    add(
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.send_test_notification)) },
                            onClick = {
                              context.sendNotification(
                                  Parcel(0xf100f, "Cool stuff", "", null, Service.EXAMPLE),
                                  Status.OutForDelivery,
                                  ParcelHistoryItem(
                                      "The courier has picked up the package",
                                      LocalDateTime.now(),
                                      "",
                                  ),
                              )
                            },
                        ))
                  }
                  add(
                      Material3SettingsItem(
                          title = { Text(stringResource(R.string.dump_logs_button)) },
                          onClick = { openLogcatDumper(context) },
                      ))
                },
        )
      }

      item {
        Text(
            "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
@PreviewLightDark
private fun SettingsViewPreview() {
  ParcelTrackerTheme {
    SettingsView(
        onBackPressed = {},
        onNavigateToAccounts = {},
    )
  }
}
