// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Android 16-style grouped settings rows, adapted from Metrolist-KMP (GPLv3) 2026. Written by @nyxiereal */
@Composable
fun Material3SettingsGroup(
    title: String? = null,
    items: List<Material3SettingsItem>,
    horizontalPadding: Dp = 16.dp,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    title?.let {
      Text(
          text = it,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.primary,
          modifier =
              Modifier.padding(
                  start = horizontalPadding,
                  end = horizontalPadding,
                  top = 8.dp,
                  bottom = 12.dp,
              ),
      )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      items.forEachIndexed { index, item ->
        Material3SettingsItemRow(
            item = item,
            isFirst = index == 0,
            isLast = index == items.lastIndex,
        )
      }
    }
  }
}

@Composable
private fun Material3SettingsItemRow(
    item: Material3SettingsItem,
    isFirst: Boolean,
    isLast: Boolean,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val outerRadius = 20.dp
  val innerRadius = 5.dp
  val topRadius by animateDpAsState(if (isFirst) outerRadius else innerRadius)
  val bottomRadius by animateDpAsState(if (isLast) outerRadius else innerRadius)
  val shape =
      RoundedCornerShape(
          topStart = topRadius,
          topEnd = topRadius,
          bottomStart = bottomRadius,
          bottomEnd = bottomRadius,
      )
  val backgroundColor by
      animateColorAsState(
          if (isPressed) MaterialTheme.colorScheme.surfaceContainerHigh
          else MaterialTheme.colorScheme.surfaceContainer)

  Row(
      modifier =
          item.modifier
              .fillMaxWidth()
              .clip(shape)
              .background(backgroundColor)
              .then(
                  if (item.checked != null) {
                    Modifier.toggleable(
                        value = item.checked,
                        enabled = item.enabled && item.onClick != null,
                        role = Role.Switch,
                        interactionSource = interactionSource,
                        indication = null,
                        onValueChange = { item.onClick?.invoke() },
                    )
                  } else {
                    Modifier.clickable(
                        enabled = item.enabled && item.onClick != null,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { item.onClick?.invoke() },
                    )
                  })
              .padding(horizontal = 16.dp, vertical = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      ProvideTextStyle(
          MaterialTheme.typography.bodyLarge.copy(
              color =
                  if (item.enabled) MaterialTheme.colorScheme.onSurface
                  else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))) {
            item.title()
          }
      item.description?.let { description ->
        Spacer(Modifier.height(2.dp))
        ProvideTextStyle(
            MaterialTheme.typography.bodyMedium.copy(
                color =
                    if (item.enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))) {
              description()
            }
      }
    }

    item.trailingContent?.let {
      it()
    }
  }
}

data class Material3SettingsItem(
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val enabled: Boolean = true,
    val modifier: Modifier = Modifier,
    val checked: Boolean? = null,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun Material3SettingsSwitch(checked: Boolean, enabled: Boolean = true) {
  Switch(
      checked = checked,
      onCheckedChange = null,
      enabled = enabled,
      modifier = Modifier.clearAndSetSemantics {},
  )
}
