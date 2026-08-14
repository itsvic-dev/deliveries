// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.ui.views

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.itsvic.parceltracker.DHL_API_KEY
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.allegro.AllegroRepository
import dev.itsvic.parceltracker.allegro.AllegroSession
import dev.itsvic.parceltracker.dataStore
import dev.itsvic.parceltracker.olx.OlxRepository
import dev.itsvic.parceltracker.olx.OlxSession
import dev.itsvic.parceltracker.ui.components.Material3SettingsGroup
import dev.itsvic.parceltracker.ui.components.Material3SettingsItem
import dev.itsvic.parceltracker.ui.theme.ParcelTrackerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsView(
    onBackPressed: () -> Unit,
    olxCallbackUrl: String? = null,
    onOlxCallbackConsumed: () -> Unit = {},
) {
  val context = LocalContext.current
  val resources = LocalResources.current
  val scope = rememberCoroutineScope()
  val allegroRepository = remember(context) { AllegroRepository(context) }
  val olxRepository = remember(context) { OlxRepository(context) }
  var allegroSession by remember { mutableStateOf(allegroRepository.session()) }
  var olxSession by remember { mutableStateOf(olxRepository.session()) }
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var showLogin by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<AccountMessage?>(null) }
  var olxBusy by remember { mutableStateOf(false) }
  var olxMessage by remember { mutableStateOf<AccountMessage?>(null) }
  val preferences by context.dataStore.data.collectAsState(emptyPreferences())
  val dhlApiKey = preferences[DHL_API_KEY].orEmpty()
  var showDhlApiKeyDialog by remember { mutableStateOf(false) }
  val unknownError = stringResource(R.string.error_unknown)
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  androidx.compose.runtime.LaunchedEffect(olxCallbackUrl) {
    val callbackUrl = olxCallbackUrl ?: return@LaunchedEffect
    onOlxCallbackConsumed()
    scope.launch {
      olxBusy = true
      olxMessage = null
      val result =
          withContext(Dispatchers.IO) {
            runSuspendCatching { olxRepository.completeLogin(callbackUrl) }
          }
      olxBusy = false
      result.fold(
          onSuccess = { newSession ->
            olxSession = newSession
            olxMessage = null
          },
          onFailure = {
            olxSession = olxRepository.session()
            olxMessage =
                AccountMessage(
                    resources.getString(R.string.olx_login_failed, it.message ?: unknownError),
                    isError = true,
                )
          },
      )
    }
  }

  Scaffold(
      topBar = {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.settings_accounts)) },
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
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
          stringResource(R.string.accounts_detail),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      AllegroAccountCard(
          session = allegroSession,
          username = username,
          password = password,
          showLogin = showLogin,
          busy = busy,
          message = message,
          onUsernameChange = { username = it },
          onPasswordChange = { password = it },
          onShowLogin = {
            showLogin = true
            message = null
          },
          onCancelLogin = {
            showLogin = false
            password = ""
            message = null
          },
          onLogin = {
            busy = true
            message = null
            scope.launch {
              val result =
                  withContext(Dispatchers.IO) {
                    runSuspendCatching {
                      val newSession = allegroRepository.login(username, password)
                      newSession
                    }
                  }
              busy = false
              password = ""
              result.fold(
                  onSuccess = { newSession ->
                    allegroSession = newSession
                    showLogin = false
                    message = null
                  },
                  onFailure = {
                    message =
                        AccountMessage(
                            resources.getString(
                                R.string.allegro_login_failed, it.message ?: unknownError),
                            isError = true,
                        )
                  },
              )
            }
          },
          onLogout = {
            busy = true
            message = null
            scope.launch {
              val result =
                  withContext(NonCancellable + Dispatchers.IO) {
                    runSuspendCatching { allegroRepository.logout() }
                  }
              busy = false
              allegroSession = allegroRepository.session()
              if (result.isFailure) {
                message =
                    AccountMessage(
                        result.exceptionOrNull()?.message ?: unknownError,
                        isError = true,
                    )
              }
            }
          },
      )

      OlxAccountCard(
          session = olxSession,
          busy = olxBusy,
          message = olxMessage,
          onLogin = {
            olxMessage = null
            val request = olxRepository.beginLogin()
            CustomTabsIntent.Builder().build().launchUrl(context, request.authorizationUrl.toUri())
          },
          onLogout = {
            olxBusy = true
            olxMessage = null
            scope.launch {
              withContext(NonCancellable + Dispatchers.IO) {
                runSuspendCatching { olxRepository.logout() }
              }
              olxBusy = false
              olxSession = olxRepository.session()
            }
          },
      )

      Material3SettingsGroup(
          title = stringResource(R.string.settings_api_keys),
          items =
              listOf(
                  Material3SettingsItem(
                      title = { Text(stringResource(R.string.service_dhl)) },
                      description = {
                        Text(
                            stringResource(
                                if (dhlApiKey.isBlank()) R.string.api_key_not_configured
                                else R.string.api_key_configured))
                      },
                      onClick = { showDhlApiKeyDialog = true },
                  )),
          horizontalPadding = 0.dp,
      )

      Text(
          stringResource(R.string.account_security_detail),
          modifier = Modifier.padding(horizontal = 8.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  if (showDhlApiKeyDialog) {
    DhlApiKeyDialog(
        currentValue = dhlApiKey,
        onDismiss = { showDhlApiKeyDialog = false },
        onSave = { value ->
          scope.launch { context.dataStore.edit { it[DHL_API_KEY] = value.trim() } }
          showDhlApiKeyDialog = false
        },
    )
  }
}

@Composable
private fun OlxAccountCard(
    session: OlxSession?,
    busy: Boolean,
    message: AccountMessage?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
                "O",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
          }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
              stringResource(R.string.service_olx_account),
              style = MaterialTheme.typography.titleLarge)
          Text(
              session?.displayName ?: stringResource(R.string.account_not_connected),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (session != null) {
          Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                stringResource(R.string.account_connected),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }

      Text(
          stringResource(R.string.olx_account_detail),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (session == null) {
        FilledTonalButton(onClick = onLogin, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
          if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
          else Text(stringResource(R.string.olx_sign_in))
        }
      } else {
        OutlinedButton(onClick = onLogout, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
          Text(stringResource(R.string.olx_sign_out))
        }
      }

      message?.let {
        Text(
            it.text,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (it.isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
private fun AllegroAccountCard(
    session: AllegroSession?,
    username: String,
    password: String,
    showLogin: Boolean,
    busy: Boolean,
    message: AccountMessage?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onShowLogin: () -> Unit,
    onCancelLogin: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
                "A",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
              stringResource(R.string.service_allegro_account),
              style = MaterialTheme.typography.titleLarge,
          )
          Text(
              session?.username ?: stringResource(R.string.account_not_connected),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (session != null) {
          Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Text(
                stringResource(R.string.account_connected),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }

      Text(
          stringResource(R.string.allegro_account_detail),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      when {
        session != null -> {
          OutlinedButton(
              onClick = onLogout,
              enabled = !busy,
              modifier = Modifier.fillMaxWidth(),
          ) {
            Text(stringResource(R.string.allegro_sign_out))
          }
        }
        showLogin -> {
          OutlinedTextField(
              value = username,
              onValueChange = onUsernameChange,
              modifier = Modifier.fillMaxWidth(),
              label = { Text(stringResource(R.string.allegro_login)) },
              singleLine = true,
              enabled = !busy,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          )
          OutlinedTextField(
              value = password,
              onValueChange = onPasswordChange,
              modifier = Modifier.fillMaxWidth(),
              label = { Text(stringResource(R.string.allegro_password)) },
              singleLine = true,
              enabled = !busy,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          )
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            OutlinedButton(
                onClick = onCancelLogin,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
              Text(stringResource(R.string.cancel))
            }
            FilledTonalButton(
                onClick = onLogin,
                enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
              if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
              } else {
                Text(stringResource(R.string.allegro_sign_in))
              }
            }
          }
        }
        else -> {
          FilledTonalButton(onClick = onShowLogin, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.allegro_sign_in))
          }
        }
      }

      message?.let {
        Text(
            it.text,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (it.isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
private fun DhlApiKeyDialog(
    currentValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
  var value by remember(currentValue) { mutableStateOf(currentValue) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.dhl_api_key)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          OutlinedTextField(
              value = value,
              onValueChange = { value = it },
              modifier = Modifier.fillMaxWidth(),
              label = { Text(stringResource(R.string.dhl_api_key)) },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              singleLine = true,
          )
          Text(
              AnnotatedString.fromHtml(
                  stringResource(R.string.dhl_api_key_flavor_text),
                  linkStyles =
                      TextLinkStyles(
                          style =
                              SpanStyle(
                                  color = MaterialTheme.colorScheme.primary,
                                  textDecoration = TextDecoration.Underline))),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { onSave(value) }) { Text(stringResource(R.string.save)) }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

private data class AccountMessage(val text: String, val isError: Boolean = false)

private suspend inline fun <T> runSuspendCatching(crossinline action: suspend () -> T): Result<T> =
    try {
      Result.success(action())
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      Result.failure(error)
    }

@Composable
@PreviewLightDark
private fun AccountsViewPreview() {
  ParcelTrackerTheme { AccountsView(onBackPressed = {}) }
}
