// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.ui.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.api.Service
import dev.itsvic.parceltracker.api.getDeliveryService
import dev.itsvic.parceltracker.api.getDeliveryServiceName
import dev.itsvic.parceltracker.api.serviceOptions
import dev.itsvic.parceltracker.db.Parcel
import dev.itsvic.parceltracker.ui.theme.ParcelTrackerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditParcelView(
    parcel: Parcel?,
    prefillService: Service = Service.UNDEFINED,
    prefillTrackingId: String = "",
    prefillPostalCode: String = "",
    onBackPressed: () -> Unit,
    onCompleted: (Parcel) -> Unit,
) {
  val isEdit = parcel != null

  var humanName by remember { mutableStateOf(parcel?.humanName ?: "") }
  var nameError by remember { mutableStateOf(false) }
  var trackingId by remember { mutableStateOf(parcel?.parcelId ?: prefillTrackingId) }
  var idError by remember { mutableStateOf(false) }
  var specifyPostalCode by remember {
    mutableStateOf(parcel?.postalCode != null || prefillPostalCode.isNotBlank())
  }
  var postalCode by remember { mutableStateOf(parcel?.postalCode ?: prefillPostalCode) }
  var postalCodeError by remember { mutableStateOf(false) }
  var service by remember { mutableStateOf(parcel?.service ?: prefillService) }
  var serviceError by remember { mutableStateOf(false) }

  val backend = if (service != Service.UNDEFINED) getDeliveryService(service) else null

  fun validateInputs(): Boolean {
    // reset error states first
    nameError = false
    idError = false
    serviceError = false
    postalCodeError = false

    var success = true
    if (humanName.isBlank()) {
      success = false
      nameError = true
    }
    if (trackingId.isBlank()) {
      success = false
      idError = true
    }
    if (service == Service.UNDEFINED) {
      success = false
      serviceError = true
    }
    if (((backend?.acceptsPostCode == true && specifyPostalCode) ||
        (backend?.requiresPostCode == true)) && postalCode.isBlank()) {
      success = false
      postalCodeError = true
    }

    if (!success) return false
    return true
  }

  var expanded by remember { mutableStateOf(false) }
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

  val sortedServiceOptions =
      serviceOptions.sortedBy { getDeliveryService(it)?.acceptsFormat(trackingId)?.not() }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text(stringResource(if (isEdit) R.string.edit_parcel else R.string.add_a_parcel))
            },
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
            Modifier.padding(innerPadding).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
          modifier = Modifier.padding(horizontal = 16.dp).sizeIn(maxWidth = 488.dp).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
            value = humanName,
            onValueChange = {
              humanName = it
              nameError = false
            },
            singleLine = true,
            label = { Text(stringResource(R.string.parcel_name)) },
            modifier = Modifier.fillMaxWidth(),
            isError = nameError,
            supportingText = {
              if (nameError) Text(stringResource(R.string.human_name_error_text))
            },
        )

        OutlinedTextField(
            value = trackingId,
            onValueChange = {
              trackingId = it
              idError = false
            },
            singleLine = true,
            label = { Text(stringResource(R.string.tracking_id)) },
            modifier = Modifier.fillMaxWidth(),
            isError = idError,
            supportingText = { if (idError) Text(stringResource(R.string.tracking_id_error_text)) },
        )

        // Service dropdown
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
          OutlinedTextField(
              value =
                  if (service == Service.UNDEFINED) ""
                  else stringResource(getDeliveryServiceName(service)!!),
              onValueChange = {},
              modifier =
                  Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                      .fillMaxWidth(),
              readOnly = true,
              label = { Text(stringResource(R.string.delivery_service)) },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
              colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
              isError = serviceError,
              supportingText = {
                if (serviceError) Text(stringResource(R.string.service_error_text))
              },
          )

          ExposedDropdownMenu(
              expanded = expanded,
              onDismissRequest = { expanded = false },
          ) {
            sortedServiceOptions.forEach { option ->
              DropdownMenuItem(
                  text = { Text(stringResource(getDeliveryServiceName(option)!!)) },
                  onClick = {
                    service = option
                    expanded = false
                    serviceError = false
                  },
                  contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
              )
            }
          }
        }

        AnimatedVisibility(backend?.acceptsPostCode == true && !backend.requiresPostCode) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.fillMaxWidth(0.8f)) {
              Text(stringResource(R.string.specify_a_postal_code))
              Text(
                  stringResource(R.string.specify_postal_code_flavor_text),
                  fontSize = 14.sp,
                  lineHeight = 21.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Checkbox(
                checked = specifyPostalCode,
                onCheckedChange = { specifyPostalCode = it },
            )
          }
        }

        AnimatedVisibility(
            backend?.requiresPostCode == true ||
                (backend?.requiresPostCode == false &&
                    backend.acceptsPostCode &&
                    specifyPostalCode)) {
              OutlinedTextField(
                  value = postalCode,
                  onValueChange = {
                    postalCode = it
                    postalCodeError = false
                  },
                  singleLine = true,
                  label = { Text(stringResource(R.string.postal_code)) },
                  modifier = Modifier.fillMaxWidth(),
                  isError = postalCodeError,
                  supportingText = {
                    if (postalCodeError) Text(stringResource(R.string.postal_code_error_text))
                  },
              )
            }

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
          Button(
              onClick = {
                val isOk = validateInputs()
                if (isOk) {
                  // data valid, pass it along
                  onCompleted(
                      Parcel(
                          id = parcel?.id ?: 0,
                          humanName = humanName,
                          parcelId = trackingId,
                          service = service,
                          postalCode =
                              if (backend?.requiresPostCode == true ||
                                  (backend?.acceptsPostCode == true && specifyPostalCode))
                                  postalCode
                              else null,
                      ))
                }
              }) {
                Text(stringResource(if (isEdit) R.string.save else R.string.add_parcel))
              }
        }
      }
    }
  }
}

@Composable
@PreviewLightDark
fun AddParcelPreview() {
  ParcelTrackerTheme { AddEditParcelView(null, onBackPressed = {}, onCompleted = {}) }
}

@Composable
@PreviewLightDark
fun EditParcelPreview() {
  ParcelTrackerTheme {
    AddEditParcelView(
        Parcel(0, "Test", "Test", null, Service.EXAMPLE),
        onBackPressed = {},
        onCompleted = {},
    )
  }
}
