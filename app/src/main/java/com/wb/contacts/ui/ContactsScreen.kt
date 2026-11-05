package com.wb.contacts.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wb.contacts.R
import com.wb.contacts.data.Contact
import com.wb.contacts.util.Labels
import com.wb.contacts.util.PinyinUtil

@Composable
fun ContactsRoot(vm: ContactsViewModel) {
    val context = LocalContext.current
    val message = vm.toast

    LaunchedEffect(message) {
        if (!message.isNullOrEmpty()) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }

    BackHandler(enabled = vm.screen !is Screen.Home || vm.searching) {
        if (vm.searching) vm.enterSearch(false) else vm.back()
    }

    when (val screen = vm.screen) {
        is Screen.Home -> HomeScreen(vm)

        is Screen.Detail -> {
            val contact = vm.find(screen.id)
            LaunchedEffect(vm.loaded, screen.id, contact) {
                if (contact == null && vm.loaded) vm.back()
            }
            if (contact != null) {
                ContactDetailScreen(contact = contact, vm = vm)
            } else {
                Box(Modifier.fillMaxSize())
            }
        }

        is Screen.Edit -> {
            val groups by vm.groups.collectAsState()
            ContactEditScreen(
                contact = if (screen.id == null) null else vm.find(screen.id),
                groups = groups,
                vm = vm
            )
        }

        is Screen.Import -> ImportScreen(vm)
        is Screen.ClearData -> ClearDataScreen(vm)
        is Screen.CallSettings -> CallSettingsScreen(vm)
        is Screen.Sms -> SmsScreen(vm)
        is Screen.CallLog -> CallLogScreen(vm)
        is Screen.About -> AboutScreen(vm)
    }

    if (vm.pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = vm::dismissDelete,
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message)) },
            confirmButton = {
                TextButton(onClick = vm::confirmDelete) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDelete) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    val result = vm.resultDialog
    if (result != null) {
        AlertDialog(
            onDismissRequest = vm::dismissResult,
            title = { Text(result.title) },
            text = { Text(result.message) },
            confirmButton = {
                TextButton(onClick = vm::dismissResult) { Text(stringResource(R.string.action_ok)) }
            }
        )
    }


}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: ContactsViewModel) {
    val contacts by vm.contacts.collectAsState()
    val groups by vm.groups.collectAsState()
    val sections = remember(contacts, vm.query, vm.groupFilter) {
        vm.buildSections(contacts, vm.query, vm.groupFilter)
    }
    var menuExpanded by remember { mutableStateOf(false) }
    // 版本号直接读安装包信息，避免和构建配置对不上
    val appContext = LocalContext.current.applicationContext
    val versionName = remember {
        runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (vm.searching) {
                    SearchTopBar(vm.query, vm::onQueryChange) { vm.enterSearch(false) }
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    stringResource(R.string.home_title),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    pluralStringResource(R.plurals.home_subtitle, contacts.size, contacts.size) +
                                        if (versionName.isNotBlank()) {
                                            stringResource(R.string.home_subtitle_version, versionName)
                                        } else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { vm.enterSearch(true) }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.action_search)
                                )
                            }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.action_more)
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.home_menu_import)) },
                                        onClick = { menuExpanded = false; vm.openImport() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.home_menu_sms)) },
                                        onClick = { menuExpanded = false; vm.openSms() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.home_menu_calllog)) },
                                        onClick = { menuExpanded = false; vm.openCallLog() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.home_menu_callid)) },
                                        onClick = { menuExpanded = false; vm.openCallSettings() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.home_menu_duplicates)) },
                                        onClick = { menuExpanded = false; vm.markDuplicates() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.home_menu_clear_data)) },
                                        onClick = { menuExpanded = false; vm.openClearData() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.home_menu_about)) },
                                        onClick = { menuExpanded = false; vm.openAbout() }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { vm.openEdit(null) }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add_contact)
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                FilterRow(groups, vm.groupFilter, vm::selectGroup)
                when {
                    // 首屏正在解密本地库，先给加载反馈，别让用户对着空白等
                    !vm.loaded -> LoadingState()
                    sections.isEmpty() -> EmptyState(
                        hasData = contacts.isNotEmpty(),
                        onAdd = { vm.openEdit(null) },
                        onImport = vm::openImport
                    )
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            sections.forEach { section ->
                                item(key = "header-${section.title}") {
                                    SectionHeader(section.title)
                                }
                                items(section.items, key = { it.id }) { contact ->
                                    ContactRow(
                                        contact = contact,
                                        onClick = { vm.openDetail(contact.id) },
                                        onToggleFavorite = { vm.toggleFavorite(contact) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (vm.busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(vm.busyText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.home_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.home_close_search)
                )
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_clear)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterRow(groups: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.home_filter_all)) }
        )
        FilterChip(
            selected = selected == ContactsViewModel.FAVORITE_FILTER,
            onClick = { onSelect(ContactsViewModel.FAVORITE_FILTER) },
            label = { Text(stringResource(R.string.home_filter_favorite)) },
            leadingIcon = {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        )
        groups.forEach { group ->
            FilterChip(
                selected = selected == group,
                onClick = { onSelect(group) },
                // 库里存的是内部键，这里翻译成当前语言的文案
                label = { Text(Labels.groupDisplay(context, group)) }
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun ContactRow(contact: Contact, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val context = LocalContext.current
    val meta = listOf(
        Labels.groupDisplay(context, contact.groupName),
        contact.tags,
        // 重复标记是独立字段，显示时补上本地化文案
        if (contact.duplicate) stringResource(R.string.tag_duplicate) else ""
    ).filter { it.isNotBlank() }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(contact.name, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    if (contact.favorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = stringResource(R.string.home_favorited),
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFF5A623)
                        )
                    }
                }
                Text(
                    text = contact.phone.ifBlank { stringResource(R.string.home_no_phone) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        meta.forEach { item ->
                            Text(
                                text = item,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            if (contact.hasValidPhone()) {
                IconButton(onClick = { ContactActions.dial(context, contact.phone) }) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = stringResource(R.string.action_dial),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (contact.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(R.string.home_toggle_favorite),
                    tint = if (contact.favorite) Color(0xFFF5A623) else MaterialTheme.colorScheme.outline
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 72.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
fun Avatar(name: String, size: Dp) {
    val label = PinyinUtil.bucket(name)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(name)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 2.8).sp
        )
    }
}

@Composable
fun EmptyState(hasData: Boolean, onAdd: () -> Unit, onImport: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (hasData) {
                    stringResource(R.string.home_empty_no_match)
                } else {
                    stringResource(R.string.home_empty_none)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (hasData) {
                    stringResource(R.string.home_empty_no_match_hint)
                } else {
                    stringResource(R.string.home_empty_none_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(onClick = onImport) {
                Text(stringResource(R.string.home_import_from_system))
            }
            TextButton(onClick = onAdd) { Text(stringResource(R.string.home_add_manually)) }
        }
    }
}

/** 首屏加载态：正在解密本地库，让用户知道 App 在动而不是卡死 */
@Composable
fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.home_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
