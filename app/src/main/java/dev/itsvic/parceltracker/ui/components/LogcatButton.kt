package dev.itsvic.parceltracker.ui.components

import android.content.Intent
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.itsvic.parceltracker.LogcatDumperActivity
import dev.itsvic.parceltracker.R

@Composable
fun LogcatButton(modifier: Modifier = Modifier) {
  val context = LocalContext.current

  FilledTonalButton(
      onClick = { context.startActivity(Intent(context, LogcatDumperActivity::class.java)) },
      modifier = modifier,
  ) {
    Text(stringResource(R.string.dump_logs_button))
  }
}

fun openLogcatDumper(context: android.content.Context) {
  context.startActivity(Intent(context, LogcatDumperActivity::class.java))
}
