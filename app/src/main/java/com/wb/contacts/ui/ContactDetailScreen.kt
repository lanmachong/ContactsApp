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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.contacts.data.Contact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(contact: Contact, vm: ContactsViewModel) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("联系人") },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.toggleFavorite(contact) }) {
                        Icon(
                            imageVector = if (contact.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "收藏",
                            tint = if (contact.favorite) Color(0xFFF5A623)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { vm.openEdit(contact.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { vm.requestDelete(contact.id) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
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
                val subtitle = listOf(contact.groupName, contact.tags)
                    .filter { it.isNotBlank() }.joinToString(" · ")
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
                    PhoneCard(label = "手机", number = contact.phone)
                }
                if (contact.phone2.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    PhoneCard(label = "备用", number = contact.phone2)
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
                            Text("拨号")
                        }
                        OutlinedButton(
                            onClick = { ContactActions.sms(context, contact.phone) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("发短信")
                        }
                    }
                } else {
                    Text(
                        text = "这个联系人还没有号码（导入时系统里只有姓名或邮箱）。点右上角编辑补一个即可拨号。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (contact.note.isNotBlank()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "备注",
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
                    text = "数据以 AES-256-GCM 加密保存在本机，密钥由系统 Keystore 保管。来电时会显示这个联系人的姓名。",
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
            Icon(Icons.Default.Phone, contentDescription = "拨号", tint = MaterialTheme.colorScheme.primary)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { ContactActions.copy(context, number) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                "复制",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
