// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import dev.itsvic.parceltracker.R

val LocalRedactIdentifiableDetails = compositionLocalOf { false }

@Composable
fun redactedParcelName(name: String): String =
    if (LocalRedactIdentifiableDetails.current) {
      stringResource(R.string.redacted_parcel_name)
    } else {
      name
    }

@Composable
fun redactedText(value: String): String =
    if (LocalRedactIdentifiableDetails.current) {
      stringResource(R.string.redacted)
    } else {
      value
    }

@Composable
fun shouldRedactIdentifiableDetails(): Boolean = LocalRedactIdentifiableDetails.current
