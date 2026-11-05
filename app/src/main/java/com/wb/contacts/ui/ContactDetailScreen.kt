package com.wb.contacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.contacts.R
import com.wb.contacts.data.Contact
import com.wb.contacts.util.Labels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(contact: Contact, vm: ContactsViewModel) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.toggleFavorite(contact) }) {
                        Icon(
                            imageVector = if (contact.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(R.string.detail_favorite),
                            tint = if (contact.favorite) Color(0xFFF5A623)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { vm.openEdit(contact.id) }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.detail_edit)
                        )
                    }
                    IconButton(onClick = { vm.requestDelete(contact.id) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(contact.name, 88.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                val subtitle = listOf(
                    Labels.groupDisplay(context, contact.groupName),
                    contact.tags,
                    // 重复标记是独立字段，显示时补上本地化文案
                    if (contact.duplicate) stringResource(R.string.tag_duplicate) else ""
                ).filter { it.isNotBlank() }.joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (contact.phone.isNotBlank()) {
                    PhoneCard(label = stringResource(R.string.detail_phone_label), number = contact.phone)
                }
                if (contact.phone2.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    PhoneCard(label = stringResource(R.string.detail_phone2_label), number = contact.phone2)
                }

                Spacer(Modifier.height(16.dp))
                if (contact.hasValidPhone()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { ContactActions.dial(context, contact.phone) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_dial))
                        }
                        OutlinedButton(
                            onClick = { ContactActions.sms(context, contact.phone) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.detail_send_sms))
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.detail_no_phone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (contact.note.isNotBlank()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.detail_note_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = contact.note,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.detail_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PhoneCard(label: String, number: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { ContactActions.dial(context, number) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(number, style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = { ContactActions.dial(context, number) }) {
            Icon(
                Icons.Default.Phone,
                contentDescription = stringResource(R.string.action_dial),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { ContactActions.copy(context, number) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                stringResource(R.string.action_copy),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
