package com.wb.contacts.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.contacts.R
import com.wb.contacts.data.AvatarCatalog
import com.wb.contacts.data.Contact

/**
 * 来电头像选择器。
 * 顶部是当前的动物头像，下面是 10 款供选；点某款即保存并关闭。
 */
@Composable
fun AvatarPickerDialog(contact: Contact, vm: ContactsViewModel, onDismiss: () -> Unit) {
    val current = AvatarCatalog.resolve(contact)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.avatar_picker_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.avatar_picker_desc, contact.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed((0 until AvatarCatalog.COUNT).toList()) { _, idx ->
                        val selected = idx == current
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    vm.setAvatar(contact, idx)
                                    onDismiss()
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(58.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(AvatarCatalog.drawableRes(idx)),
                                        contentDescription = stringResource(AvatarCatalog.nameRes(idx)),
                                        modifier = Modifier.size(52.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.size(4.dp))
                            Text(
                                stringResource(AvatarCatalog.nameRes(idx)),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

/** 列表 / 详情页用的动物头像（替代原首字母 Avatar） */
@Composable
fun AnimalAvatar(contact: Contact, size: androidx.compose.ui.unit.Dp) {
    val idx = AvatarCatalog.resolve(contact)
    Image(
        painter = painterResource(AvatarCatalog.drawableRes(idx)),
        contentDescription = stringResource(AvatarCatalog.nameRes(idx)),
        modifier = Modifier.size(size)
    )
}
