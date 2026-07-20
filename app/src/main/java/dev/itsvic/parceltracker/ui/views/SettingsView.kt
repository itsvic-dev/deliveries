// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.itsvic.parceltracker.BuildConfig
import dev.itsvic.parceltracker.DEMO_MODE
import dev.itsvic.parceltracker.DHL_API_KEY
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.UNMETERED_ONLY
import dev.itsvic.parceltracker.api.ParcelHistoryItem
import dev.itsvic.parceltracker.api.Service
import dev.itsvic.parceltracker.api.Status
import dev.itsvic.parceltracker.dataStore
import dev.itsvic.parceltracker.db.Parcel
import dev.itsvic.parceltracker.enqueueNotificationWorker
import dev.itsvic.parceltracker.sendNotification
import dev.itsvic.parceltracker.ui.components.LogcatButton
import dev.itsvic.parceltracker.ui.theme.ParcelTrackerTheme
import java.time.LocalDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    onBackPressed: () -> Unit,
) {
  val context = LocalContext.current
  val preferences by context.dataStore.data.collectAsState(emptyPreferences())
  val demoMode = preferences[DEMO_MODE] == true
  val unmeteredOnly = preferences[UNMETERED_ONLY] == true
  val coroutineScope = rememberCoroutineScope()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  val dhlApiKey = preferences[DHL_API_KEY] ?: ""

  fun <T> setValue(key: Preferences.Key<T>, value: T) {
    coroutineScope.launch { context.dataStore.edit { it[key] = value } }
  }

  val setUnmeteredOnly: (Boolean) -> Unit = { value ->
    coroutineScope.launch {
      context.dataStore.edit { it[UNMETERED_ONLY] = value }
      // reschedule notification worker to update constraints
      context.enqueueNotificationWorker()
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
            scrollBehavior = scrollBehavior,
        )
      },
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
  ) { innerPadding ->
    Column(Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
      Row(
          modifier =
              Modifier.clickable { setUnmeteredOnly(unmeteredOnly.not()) }
                  .padding(16.dp, 12.dp)
                  .fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
          Text(stringResource(R.string.unmetered_only_setting))
          Text(
              stringResource(R.string.unmetered_only_setting_detail),
              style = MaterialTheme.typography.bodyMedium)
        }
        SettingsSwitch(checked = unmeteredOnly, onCheckedChange = setUnmeteredOnly)
      }

      Text(
          stringResource(R.string.settings_api_keys),
          modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 2.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedTextField(
          dhlApiKey,
          { setValue(DHL_API_KEY, it) },
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
          label = { Text(stringResource(R.string.service_dhl)) },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
      )

      Text(
          AnnotatedString.fromHtml(
              stringResource(R.string.dhl_api_key_flavor_text),
              linkStyles =
                  TextLinkStyles(
                      style =
                          SpanStyle(
                              textDecoration = TextDecoration.Underline,
                              color = MaterialTheme.colorScheme.primary))),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

      Text(
          stringResource(R.string.settings_experimental),
          modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 2.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
          modifier =
              Modifier.clickable { setValue(DEMO_MODE, demoMode.not()) }
                  .padding(16.dp, 12.dp)
                  .fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
          Text(stringResource(R.string.demo_mode))
          Text(
              stringResource(R.string.demo_mode_detail),
              style = MaterialTheme.typography.bodyMedium)
        }
        SettingsSwitch(checked = demoMode, onCheckedChange = { setValue(DEMO_MODE, it) })
      }

      if (BuildConfig.DEBUG)
          FilledTonalButton(
              onClick = {
                context.sendNotification(
                    Parcel(0xf100f, "Cool stuff", "", null, Service.EXAMPLE),
                    Status.OutForDelivery,
                    ParcelHistoryItem(
                        "The courier has picked up the package", LocalDateTime.now(), ""))
              },
              modifier = Modifier.padding(16.dp, 12.dp).fillMaxWidth()) {
                Text("Send test notification")
              }

      LogcatButton(modifier = Modifier.padding(16.dp, 12.dp).fillMaxWidth())

      Text(
          "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
          modifier = Modifier.padding(16.dp, 8.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SettingsSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      thumbContent = {
        Icon(
            imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            modifier = Modifier.size(SwitchDefaults.IconSize),
        )
      },
  )
}

@Composable
@PreviewLightDark
private fun SettingsViewPreview() {
  ParcelTrackerTheme {
    SettingsView(
        onBackPressed = {},
    )
  }
}
