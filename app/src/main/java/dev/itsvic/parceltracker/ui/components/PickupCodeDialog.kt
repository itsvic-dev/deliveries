// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.allegro.AllegroPickupDetails

@Composable
fun PickupCodeDialog(details: AllegroPickupDetails, onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.pickup_code_title)) },
      text = {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          if (details.qrPayload.isNotBlank()) {
            val bitmap = remember(details.qrPayload) { createQrCode(details.qrPayload) }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.pickup_qr_code),
                modifier =
                    Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White).padding(8.dp),
            )
          }
          if (details.code.isNotBlank()) {
            Column {
              Text(
                  stringResource(R.string.pickup_code),
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              SelectionContainer {
                Text(details.code, style = MaterialTheme.typography.headlineMedium)
              }
            }
          }
          if (details.phoneNumber.isNotBlank()) {
            Column {
              Text(
                  stringResource(R.string.pickup_phone_number),
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              SelectionContainer {
                Text(details.phoneNumber, style = MaterialTheme.typography.titleLarge)
              }
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
  )
}

private fun createQrCode(payload: String): Bitmap {
  val matrix =
      QRCodeWriter()
          .encode(
              payload,
              BarcodeFormat.QR_CODE,
              512,
              512,
              mapOf(EncodeHintType.MARGIN to 2),
          )
  val pixels =
      IntArray(matrix.width * matrix.height) { index ->
        if (matrix[index % matrix.width, index / matrix.width]) 0xff000000.toInt()
        else 0xffffffff.toInt()
      }
  return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
    setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
  }
}
