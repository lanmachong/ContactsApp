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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wb.contacts.data.Contact
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
        is Screen.CallSettings -> CallSettingsScreen(vm)
        is Screen.Sms -> SmsScreen(vm)
        is Screen.CallLog -> CallLogScreen(vm)
        is Screen.About -> AboutScreen(vm)
    }

    if (vm.pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = vm::dismissDelete,
            title = { Text("删除联系人") },
            text = { Text("删除后无法恢复，确定继续吗？") },
            confirmButton = {
                TextButton(onClick = vm::confirmDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = vm::dismissDelete) { Text("取消") } }
        )
    }

    if (vm.confirmClearAll) {
        AlertDialog(
            onDismissRequest = vm::dismissClearAll,
            title = { Text("清空通讯录") },
            text = { Text("将删除本机保存的全部联系人，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = vm::doClearAll) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = vm::dismissClearAll) { Text("取消") } }
        )
    }

    if (vm.confirmWipeAll) {
        AlertDialog(
            onDismissRequest = vm::dismissWipeAll,
            title = { Text("清空系统通讯录") },
            text = {
                val countText = if (vm.systemContactCount > 0) {
                    "全部 ${vm.systemContactCount} 位联系人"
                } else {
                    "全部联系人"
                }
                Text(
                    "会删除系统通讯录里当前的$countText，" +
                        "包括重复的、没有号码的、以及从没导入过的。\n\n" +
                        "· 本应用内的加密副本不受影响，仍然可以查和拨号\n" +
                        "· 此操作不可撤销，请确认已经导出 CSV 备份\n" +
                        "· 云账户联系人的删除可能同步到云端"
                )
            },
            confirmButton = {
                TextButton(onClick = vm::doWipeAll) {
                    Text("我已备份，全部删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = vm::dismissWipeAll) { Text("取消") } }
        )
    }

    val result = vm.resultDialog
    if (result != null) {
        AlertDialog(
            onDismissRequest = vm::dismissResult,
            title = { Text(result.title) },
            text = { Text(result.message) },
            confirmButton = { TextButton(onClick = vm::dismissResult) { Text("知道了") } }
        )
    }

    if (vm.confirmWipeSms) {
        AlertDialog(
            onDismissRequest = vm::dismissWipeSms,
            title = { Text("清空系统短信") },
            text = {
                Text(
                    "会删除手机短信应用里的全部短信。\n\n" +
                        "· 本应用内的加密副本不受影响，仍然可以查看\n" +
                        "· 此操作不可撤销，建议先导出 CSV 备份\n" +
                        "· Android 4.4 起需本应用是默认短信应用才能删掉，否则系统会拒绝"
                )
            },
            confirmButton = {
                TextButton(onClick = vm::doWipeSms) {
                    Text("我已备份，全部删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = vm::dismissWipeSms) { Text("取消") } }
        )
    }

    if (vm.confirmClearSms) {
        AlertDialog(
            onDismissRequest = vm::dismissClearSms,
            title = { Text("清空本应用短信副本") },
            text = { Text("将删除本机保存的全部短信副本，系统里的原短信不受影响，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = vm::doClearSms) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = vm::dismissClearSms) { Text("取消") } }
        )
    }

    if (vm.confirmWipeCalls) {
        AlertDialog(
            onDismissRequest = vm::dismissWipeCalls,
            title = { Text("清空系统通话记录") },
            text = {
                Text(
                    "会尝试删除手机里的全部通话记录。\n\n" +
                        "· 本应用内的加密副本不受影响\n" +
                        "· 此操作不可撤销，建议先导出 CSV 备份\n" +
                        "· 系统通常只把删除权限授予系统应用，普通 App 会被拒绝"
                )
            },
            confirmButton = {
                TextButton(onClick = vm::doWipeCalls) {
                    Text("我已备份，全部删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = vm::dismissWipeCalls) { Text("取消") } }
        )
    }

    if (vm.confirmClearCalls) {
        AlertDialog(
            onDismissRequest = vm::dismissClearCalls,
            title = { Text("清空本应用通话记录副本") },
            text = { Text("将删除本机保存的全部通话记录副本，系统里的原记录不受影响，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = vm::doClearCalls) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = vm::dismissClearCalls) { Text("取消") } }
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
                                Text("通讯录", fontWeight = FontWeight.Bold)
                                Text(
                                    "${contacts.size} 位联系人 · 本地加密存储" +
                                        if (versionName.isNotBlank()) " · v$versionName" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { vm.enterSearch(true) }) {
                                Icon(Icons.Default.Search, contentDescription = "搜索")
                            }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("从系统通讯录导入") },
                                        onClick = { menuExpanded = false; vm.openImport() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("短信备份") },
                                        onClick = { menuExpanded = false; vm.openSms() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("通话记录备份") },
                                        onClick = { menuExpanded = false; vm.openCallLog() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("来电识别悬浮窗") },
                                        onClick = { menuExpanded = false; vm.openCallSettings() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("检测重复联系人") },
                                        onClick = { menuExpanded = false; vm.markDuplicates() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("清空本应用通讯录") },
                                        onClick = { menuExpanded = false; vm.requestClearAll() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("关于") },
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
                    Icon(Icons.Default.Add, contentDescription = "新增联系人")
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
                placeholder = { Text("姓名 / 号码 / 拼音首字母") },
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
                Icon(Icons.Default.ArrowBack, contentDescription = "关闭搜索")
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清空")
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
            label = { Text("全部") }
        )
        FilterChip(
            selected = selected == ContactsViewModel.FAVORITE_FILTER,
            onClick = { onSelect(ContactsViewModel.FAVORITE_FILTER) },
            label = { Text("收藏") },
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
                label = { Text(group) }
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
    val meta = listOf(contact.groupName, contact.tags).filter { it.isNotBlank() }
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
                            contentDescription = "已收藏",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFF5A623)
                        )
                    }
                }
                Text(
                    text = contact.phone.ifBlank { "无号码" },
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
                        contentDescription = "拨号",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (contact.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "切换收藏",
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
                text = if (hasData) "没有匹配的联系人" else "通讯录还是空的",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (hasData) "换个关键词或筛选条件试试" else "数据加密后只保存在这台手机上，不会上传",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(onClick = onImport) {
                Text("从系统通讯录导入")
            }
            TextButton(onClick = onAdd) { Text("手动添加联系人") }
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
                "正在解密本地通讯录…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
