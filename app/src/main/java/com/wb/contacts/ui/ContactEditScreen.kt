package com.wb.contacts.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wb.contacts.R
import com.wb.contacts.data.Contact
import com.wb.contacts.util.Labels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(contact: Contact?, groups: List<String>, vm: ContactsViewModel) {
    val context = LocalContext.current
    var name by remember(contact) { mutableStateOf(contact?.name.orEmpty()) }
    var phone by remember(contact) { mutableStateOf(contact?.phone.orEmpty()) }
    var phone2 by remember(contact) { mutableStateOf(contact?.phone2.orEmpty()) }
    // 分组展示的是本地化文案，保存时再转回库里的内部键
    var group by remember(contact) {
        mutableStateOf(Labels.groupDisplay(context, contact?.groupName.orEmpty()))
    }
    var tags by remember(contact) { mutableStateOf(contact?.tags.orEmpty()) }
    var note by remember(contact) { mutableStateOf(contact?.note.orEmpty()) }
    var favorite by remember(contact) { mutableStateOf(contact?.favorite ?: false) }

    fun submit() {
        val draft = (contact ?: Contact()).copy(
            name = name.trim(),
            phone = phone.trim(),
            phone2 = phone2.trim(),
            groupName = Labels.groupToStorage(context, group),
            tags = tags.trim(),
            note = note.trim(),
            favorite = favorite
        )
        vm.save(draft)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (contact == null) {
                            stringResource(R.string.edit_title_new)
                        } else {
                            stringResource(R.string.edit_title_edit)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { submit() }) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.action_save)
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.edit_label_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.edit_label_phone)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = phone2,
                onValueChange = { phone2 = it },
                label = { Text(stringResource(R.string.edit_label_phone2)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = group,
                onValueChange = { group = it },
                label = { Text(stringResource(R.string.edit_label_group)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (groups.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groups.forEach { item ->
                        AssistChip(
                            onClick = { group = Labels.groupDisplay(context, item) },
                            label = { Text(Labels.groupDisplay(context, item)) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text(stringResource(R.string.edit_label_tags)) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.edit_tags_placeholder)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.edit_label_note)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.edit_favorite),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.edit_favorite_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = favorite, onCheckedChange = { favorite = it })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
