package com.wb.contacts.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wb.contacts.ContactsApplication
import com.wb.contacts.R
import com.wb.contacts.data.ArchiveRepository
import com.wb.contacts.data.CallRecord
import com.wb.contacts.data.Contact
import com.wb.contacts.data.ContactsRepository
import com.wb.contacts.data.SmsMessage
import com.wb.contacts.system.SystemCallLog
import com.wb.contacts.system.SystemContacts
import com.wb.contacts.system.SystemSms
import com.wb.contacts.util.Labels
import com.wb.contacts.util.PermissionUtil
import com.wb.contacts.util.PhoneText
import com.wb.contacts.util.PinyinUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: Long) : Screen
    data class Edit(val id: Long?) : Screen

    /** 导入 / 备份系统通讯录 */
    data object Import : Screen

    /** 清空数据（短信 / 通话记录 / 通讯录 × 系统 / 本应用） */
    data object ClearData : Screen

    /** 来电识别悬浮窗设置 */
    data object CallSettings : Screen

    /** 短信备份 */
    data object Sms : Screen

    /** 通话记录备份 */
    data object CallLog : Screen

    /** 关于 */
    data object About : Screen
}

data class ContactSection(val title: String, val items: List<Contact>)

/** 要清空的内容 */
enum class ClearTarget { CONTACTS, SMS, CALLS }

/** 清空的方向：系统里的原始数据，还是本应用里的加密副本 */
enum class ClearDirection { SYSTEM, LOCAL }

/** 导入结果汇总，直接用于结果弹窗文案 */
data class ImportOutcome(
    val total: Int,
    val imported: Int,
    val updated: Int,
    val unchanged: Int,
    val noPhone: Int,
    val noRawId: Int,
    val mergedDuplicates: Int
) {
    fun describe(context: Context): String = buildString {
        if (total == 0) {
            append(context.getString(R.string.import_desc_empty))
            return@buildString
        }
        appendLine(context.resources.getQuantityString(R.plurals.import_desc_total, total, total))
        appendLine(context.resources.getQuantityString(R.plurals.import_desc_added, imported, imported))
        if (updated > 0) appendLine(context.resources.getQuantityString(R.plurals.import_desc_updated, updated, updated))
        if (unchanged > 0) appendLine(context.resources.getQuantityString(R.plurals.import_desc_unchanged, unchanged, unchanged))
        if (mergedDuplicates > 0) {
            appendLine(context.resources.getQuantityString(R.plurals.import_desc_merged, mergedDuplicates, mergedDuplicates))
        }
        if (noPhone > 0) appendLine(context.resources.getQuantityString(R.plurals.import_desc_nophone, noPhone, noPhone))
        if (noRawId > 0) appendLine(context.resources.getQuantityString(R.plurals.import_desc_norawid, noRawId, noRawId))
        appendLine()
        append(context.getString(R.string.import_desc_encrypted))
    }
}

class ContactsViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val FAVORITE_FILTER = "__favorite__"
    }

    // 与 Application 共用同一个仓储实例，否则内存缓存不共享，
    // 冷启动时来电索引预热和列表刷新会把整库各解密一遍，白白多等一两秒
    private val repo: ContactsRepository =
        (app as? ContactsApplication)?.repository ?: ContactsRepository(app)

    /** 短信 / 通话记录的加密仓储，与 Application 共用实例 */
    private val archiveRepo: ArchiveRepository =
        (app as? ContactsApplication)?.archiveRepository ?: ArchiveRepository(app)

    /** ViewModel 没有重组的机会，文案统一从这里取当前语言的字符串 */
    private val context: Context get() = getApplication<Application>()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _groups = MutableStateFlow<List<String>>(emptyList())
    val groups: StateFlow<List<String>> = _groups.asStateFlow()

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var query by mutableStateOf("")
        private set
    var groupFilter by mutableStateOf<String?>(null)
        private set
    var searching by mutableStateOf(false)
        private set
    var pendingDeleteId by mutableStateOf<Long?>(null)
        private set
    var toast by mutableStateOf<String?>(null)
        private set

    // ——— 清空数据 ———
    /** 勾选要清空的内容，默认一个都不勾 */
    var clearTargets by mutableStateOf<Set<ClearTarget>>(emptySet())
        private set

    /** 清空方向，默认清空本应用里的加密副本（系统数据删了不可恢复，先给保守默认） */
    var clearDirection by mutableStateOf(ClearDirection.LOCAL)
        private set

    /** 用户输入的确认词 */
    var clearConfirmInput by mutableStateOf("")
        private set

    /** 首次加载是否已完成，避免「查不到联系人」被误判为已删除 */
    var loaded by mutableStateOf(false)
        private set

    // ——— 导入 / 清理 ———
    var systemContactCount by mutableStateOf(0)
        private set
    var systemScanned by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var busyText by mutableStateOf("")
        private set
    var resultDialog by mutableStateOf<ResultDialog?>(null)
        private set

    // ——— 来电识别 ———
    var callServiceRunning by mutableStateOf(false)
        private set
    var overlayAllowed by mutableStateOf(false)
        private set
    var screeningRoleHeld by mutableStateOf(false)
        private set

    // ——— 短信 / 通话记录 ———
    var smsMessages by mutableStateOf<List<SmsMessage>>(emptyList())
        private set
    var callRecords by mutableStateOf<List<CallRecord>>(emptyList())
        private set
    var systemSmsCount by mutableStateOf(0)
        private set
    var systemCallCount by mutableStateOf(0)
        private set
    var smsLoaded by mutableStateOf(false)
        private set
    var callLogLoaded by mutableStateOf(false)
        private set

    data class ResultDialog(val title: String, val message: String)

    init {
        refresh()
        refreshCallState()
    }

    fun refresh() {
        viewModelScope.launch {
            val all = repo.getAll()
            _contacts.value = all
            _groups.value = all.map { it.groupName }.filter { it.isNotBlank() }.distinct().sorted()
            // 通讯录一变，来电索引同步更新
            (getApplication() as? ContactsApplication)?.let {
                com.wb.contacts.call.CallerIndex.update(all)
            }
            loaded = true
        }
    }

    // ————————————————— 导航 —————————————————

    fun openDetail(id: Long) {
        screen = Screen.Detail(id)
    }

    fun openEdit(id: Long?) {
        screen = Screen.Edit(id)
    }

    fun openImport() {
        screen = Screen.Import
        scanSystemContacts()
    }

    /** 打开「清空数据」页，进入时重置上一次的勾选和确认词 */
    fun openClearData() {
        screen = Screen.ClearData
        clearTargets = emptySet()
        clearDirection = ClearDirection.LOCAL
        clearConfirmInput = ""
        refreshClearCounts()
    }

    fun openCallSettings() {
        screen = Screen.CallSettings
        refreshCallState()
    }

    fun openSms() {
        screen = Screen.Sms
        loadSms()
    }

    fun openCallLog() {
        screen = Screen.CallLog
        loadCallLog()
    }

    fun openAbout() {
        screen = Screen.About
    }

    fun back() {
        screen = Screen.Home
        refreshCallState()
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun enterSearch(value: Boolean) {
        searching = value
        if (!value) query = ""
    }

    fun selectGroup(value: String?) {
        groupFilter = if (groupFilter == value) null else value
    }

    fun find(id: Long): Contact? = _contacts.value.firstOrNull { it.id == id }

    // ————————————————— 增删改 —————————————————

    fun save(draft: Contact): Boolean {
        val name = draft.name.trim()
        val phone = draft.phone.trim()
        if (name.isEmpty()) {
            toast = context.getString(R.string.err_name_empty)
            return false
        }
        if (!draft.copy(phone = phone).hasValidPhone()) {
            toast = context.getString(R.string.err_phone_invalid)
            return false
        }
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            // 分组统一存内部键，避免切换语言后新旧数据错位
            val normalized = draft.copy(
                name = name,
                phone = phone,
                groupName = Labels.normalizeGroup(draft.groupName),
                updatedAt = now
            )
            if (draft.id == 0L) {
                repo.insert(normalized.copy(createdAt = now))
                toast = context.getString(R.string.toast_added)
            } else {
                repo.update(normalized)
                toast = context.getString(R.string.toast_saved)
            }
            refresh()
            screen = Screen.Home
        }
        return true
    }

    fun toggleFavorite(contact: Contact) {
        viewModelScope.launch {
            repo.update(
                contact.copy(favorite = !contact.favorite, updatedAt = System.currentTimeMillis())
            )
            refresh()
        }
    }

    fun requestDelete(id: Long) {
        pendingDeleteId = id
    }

    fun dismissDelete() {
        pendingDeleteId = null
    }

    fun confirmDelete() {
        val id = pendingDeleteId ?: return
        pendingDeleteId = null
        viewModelScope.launch {
            repo.delete(id)
            refresh()
            screen = Screen.Home
            toast = context.getString(R.string.toast_deleted)
        }
    }

    fun requestClearAll() {
        openClearData()
    }

    fun dismissClearAll() {
        screen = Screen.Home
    }

    fun consumeToast() {
        toast = null
    }

    /** 供 UI 层直接给出提示（例如跳转系统设置失败时） */
    fun notify(message: String) {
        toast = message
    }

    fun dismissResult() {
        resultDialog = null
    }

    // ————————————————— 导入系统通讯录 —————————————————

    /**
     * 标记重复联系人。
     *
     * 判定范围同时覆盖系统和本应用：
     * · 系统里同一个号码出现在两个不同联系人上（Contacts 表按人去重后的重复卡片）
     * · 本应用里同一个号码出现多次
     * 命中的联系人会被打上重复标记，用户搜号码就能定位到自己该删哪一条，
     * 系统那边的重复还需要他自己去系统通讯录里删 —— 本应用不替他动系统数据。
     */
    fun markDuplicates() {
        if (!PermissionUtil.canReadContacts(context)) {
            toast = context.getString(R.string.err_read_for_dup)
            return
        }
        if (busy) return
        busy = true
        busyText = context.getString(R.string.busy_compare_dup)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { applyDuplicateTags() }
            }
            busy = false
            busyText = ""
            outcome.onSuccess { (tagged, untagged, dupCount) ->
                refresh()
                resultDialog = ResultDialog(
                    context.getString(R.string.dup_done_title),
                    buildString {
                        appendLine(context.resources.getQuantityString(R.plurals.dup_found, dupCount, dupCount))
                        appendLine(context.getString(R.string.dup_tagged, tagged, untagged))
                        appendLine()
                        append(context.getString(R.string.dup_hint))
                    }
                )
            }.onFailure {
                resultDialog = ResultDialog(
                    context.getString(R.string.dup_failed_title),
                    it.message ?: context.getString(R.string.err_read_contacts)
                )
            }
        }
    }

    /**
     * 重复标记的核心逻辑：返回 (新标记数, 撤销数, 重复号码数)。
     * 导入完成后也会跑一遍，保证用户改完系统记录重新导入时标记是准的。
     */
    private suspend fun applyDuplicateTags(): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        // 系统里：号码 -> 出现在几个不同联系人上
        val sys = runCatching { SystemContacts.readAll(context) }.getOrDefault(emptyList())
        val sysOwners = HashMap<String, MutableSet<Long>>()
        sys.forEach { person ->
            person.phones.forEach { number ->
                val d = PhoneText.digits(number)
                if (d.isNotEmpty()) {
                    sysOwners.getOrPut(d) { HashSet() }.add(person.contactId)
                }
            }
        }
        val dupNumbers = sysOwners.filterValues { it.size > 1 }.keys.toMutableSet()

        val all = repo.getAll()
        // 本应用里：号码 -> 出现次数
        val libCounts = HashMap<String, Int>()
        all.forEach { c ->
            listOf(c.phone, c.phone2).forEach { number ->
                val d = PhoneText.digits(number)
                if (d.isNotEmpty()) libCounts[d] = (libCounts[d] ?: 0) + 1
            }
        }
        libCounts.filterValues { it > 1 }.keys.forEach { dupNumbers.add(it) }

        var tagged = 0
        var untagged = 0
        val now = System.currentTimeMillis()
        all.forEach { c ->
            val phones = listOf(c.phone, c.phone2)
                .map { PhoneText.digits(it) }
                .filter { it.isNotEmpty() }
            val shouldTag = phones.any { dupNumbers.contains(it) }
            if (shouldTag == c.duplicate) return@forEach
            // 重复是独立布尔字段，不混进用户可以随便编辑的 tags 里
            repo.update(c.copy(duplicate = shouldTag, updatedAt = now))
            if (shouldTag) tagged++ else untagged++
        }
        Triple(tagged, untagged, dupNumbers.size)
    }

    // ————————————————— 导出到系统通讯录 —————————————————

    /**
     * 把本应用里的联系人写回系统通讯录。
     * 用于「反悔卸载」或「想把数据还原回系统」的场景，按号码去重，已有号码跳过。
     */
    fun exportToSystem() {
        if (!PermissionUtil.canWriteContacts(context)) {
            toast = context.getString(R.string.err_write_contacts)
            return
        }
        val list = _contacts.value
        if (list.isEmpty()) {
            toast = context.getString(R.string.err_no_contacts)
            return
        }
        if (busy) return
        busy = true
        busyText = context.getString(R.string.busy_export_system)
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) {
                runCatching { SystemContacts.exportToSystem(context, list) }
            }
            busy = false
            busyText = ""
            scanSystemContacts()
            summary.onSuccess { s ->
                resultDialog = ResultDialog(
                    context.getString(R.string.export_done_title),
                    buildString {
                        appendLine(context.resources.getQuantityString(R.plurals.export_created, s.created, s.created))
                        if (s.skippedExisting > 0) {
                            appendLine(context.resources.getQuantityString(R.plurals.export_skipped_existing, s.skippedExisting, s.skippedExisting))
                        }
                        if (s.skippedNoPhone > 0) {
                            appendLine(context.resources.getQuantityString(R.plurals.export_skipped_nophone, s.skippedNoPhone, s.skippedNoPhone))
                        }
                        if (s.failed > 0) {
                            appendLine(context.resources.getQuantityString(R.plurals.export_failed_count, s.failed, s.failed))
                        }
                        appendLine()
                        append(context.getString(R.string.export_copy_unchanged))
                    }
                )
            }.onFailure {
                resultDialog = ResultDialog(
                    context.getString(R.string.export_failed_title),
                    it.message ?: context.getString(R.string.err_write_contacts_sys)
                )
            }
        }
    }

    fun scanSystemContacts() {
        if (!PermissionUtil.canReadContacts(context)) {
            systemScanned = false
            return
        }
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching { SystemContacts.readAll(context).size }.getOrDefault(0)
            }
            systemContactCount = count
            systemScanned = true
        }
    }

    /**
     * 读取系统通讯录并写入加密库。
     *
     * 去重依据是 raw contact id，不是号码 —— 按号码去重会把「同号但确实是两条记录」
     * 的联系人丢掉，导致系统里还有人没被导入。
     *
     * 重复不是「跳过」而是「比对更新」：系统里改了姓名或补了号码，重新导入要能把库里的
     * 记录补全。之前直接跳过，导致用户把号码从姓名栏挪到电话栏后，库里那条永远是空的。
     */
    fun importFromSystem() {
        if (!PermissionUtil.canReadContacts(context)) {
            toast = context.getString(R.string.err_read_contacts_perm)
            return
        }
        if (busy) return
        busy = true
        busyText = context.getString(R.string.busy_read_contacts)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val library = repo.getAll()

                    // —— 第一步：把库内因多次导入产生的重复副本合并掉 ——
                    // 同一条 rawId 出现在多条库记录里，说明同一人被导入过两次，保留最新的一条。
                    val ownerOfRawId = HashMap<Long, Contact>()
                    val allRawIdsByOwner = HashMap<Long, MutableSet<Long>>()
                    val redundantIds = ArrayList<Long>()
                    library.sortedByDescending { it.updatedAt }.forEach { c ->
                        if (c.sourceRawIds.isEmpty()) return@forEach
                        val owner = c.sourceRawIds.firstNotNullOfOrNull { ownerOfRawId[it] }
                        if (owner == null) {
                            c.sourceRawIds.forEach { ownerOfRawId[it] = c }
                            allRawIdsByOwner[c.id] = c.sourceRawIds.toMutableSet()
                        } else {
                            redundantIds += c.id
                            allRawIdsByOwner.getOrPut(owner.id) { owner.sourceRawIds.toMutableSet() }
                                .addAll(c.sourceRawIds)
                        }
                    }
                    var mergedDuplicates = redundantIds.size
                    allRawIdsByOwner.forEach { (id, ids) ->
                        val owner = library.firstOrNull { it.id == id } ?: return@forEach
                        val merged = ids - owner.sourceRawIds.toSet()
                        if (merged.isNotEmpty()) {
                            val patched = owner.copy(sourceRawIds = ids.toList())
                            // 合并后的记录重新登记，后续比对才能命中
                            ids.forEach { ownerOfRawId[it] = patched }
                            repo.update(patched)
                        }
                    }
                    redundantIds.forEach { repo.delete(it) }
                    mergedDuplicates = redundantIds.size

                    // —— 第二步：逐人比对，新面孔插入，已存在的按系统内容补全 ——
                    val sys = SystemContacts.readAll(context)
                    val now = System.currentTimeMillis()
                    var imported = 0
                    var updatedCount = 0
                    var unchanged = 0

                    sys.forEach { person ->
                        val numbers = person.phones
                            .map { it.trim() }
                            .filter { PhoneText.digits(it).isNotEmpty() }
                            .distinct()
                        // 姓名栏里藏着号码的脏数据：把号码救回电话字段，否则按号码永远搜不到
                        val guessed = if (numbers.isEmpty()) PhoneText.fromName(person.name) else null
                        val effectivePhones = if (guessed != null) listOf(guessed) else numbers
                        val existing = person.rawIds.firstNotNullOfOrNull { ownerOfRawId[it] }

                        if (existing != null) {
                            val newName = person.name.trim().ifBlank { existing.name }
                            val newPhone = effectivePhones.getOrNull(0) ?: existing.phone
                            val newPhone2 = effectivePhones.getOrNull(1) ?: existing.phone2
                            val newRawIds = (existing.sourceRawIds + person.rawIds).distinct()
                            val changed = newName != existing.name ||
                                newPhone != existing.phone ||
                                newPhone2 != existing.phone2 ||
                                newRawIds.size != existing.sourceRawIds.size
                            if (changed) {
                                val patched = existing.copy(
                                    name = newName,
                                    phone = newPhone,
                                    phone2 = newPhone2,
                                    sourceRawIds = newRawIds,
                                    updatedAt = now
                                )
                                repo.update(patched)
                                newRawIds.forEach { ownerOfRawId[it] = patched }
                                updatedCount++
                            } else {
                                unchanged++
                            }
                        } else {
                            val contact = Contact(
                                name = person.name.trim().ifBlank {
                                    effectivePhones.firstOrNull()
                                        ?: context.getString(R.string.contact_unnamed)
                                },
                                phone = effectivePhones.getOrNull(0).orEmpty(),
                                phone2 = effectivePhones.getOrNull(1).orEmpty(),
                                // 库里存语言无关的内部键，显示时才翻译
                                groupName = Labels.GROUP_SYSTEM_IMPORT,
                                sourceRawIds = person.rawIds,
                                createdAt = now,
                                updatedAt = now
                            )
                            repo.insert(contact)
                            person.rawIds.forEach { ownerOfRawId[it] = contact }
                            imported++
                        }
                    }

                    val noPhone = sys.count { person ->
                        person.phones.none { PhoneText.digits(it).isNotEmpty() } &&
                            PhoneText.fromName(person.name) == null
                    }
                    ImportOutcome(
                        total = sys.size,
                        imported = imported,
                        updated = updatedCount,
                        unchanged = unchanged,
                        noPhone = noPhone,
                        noRawId = sys.count { it.rawIds.isEmpty() },
                        mergedDuplicates = mergedDuplicates
                    )
                }
            }

            busy = false
            busyText = ""
            outcome.onSuccess { result ->
                // 导入完顺手标一次重复，用户不用再手动点
                val (tagged, _, dupCount) = runCatching { applyDuplicateTags() }
                    .getOrDefault(Triple(0, 0, 0))
                refresh()
                scanSystemContacts()
                val extra = if (dupCount > 0) {
                    context.getString(R.string.import_extra_dups, dupCount, tagged)
                } else {
                    ""
                }
                resultDialog = ResultDialog(
                    context.getString(R.string.import_done_title),
                    result.describe(context) + extra
                )
            }.onFailure {
                resultDialog = ResultDialog(
                    context.getString(R.string.import_failed_title),
                    it.message ?: context.getString(R.string.err_read_system)
                )
            }
        }
    }

    // ————————————————— 清理系统通讯录 —————————————————

    /**
     * 清理 = 把系统通讯录删干净。
     * 早先只删「导入过的那批」，结果重复的联系人、没有号码的人全留在系统里，
     * 用户还得再点一次全量删除，体验很差。现在一步到位：按当前系统里的全部
     * raw contact 删除，本应用内的加密副本不受影响，照样能查能拨。
     */

    // ————————————————— 清空数据 —————————————————

    /**
     * 本应用内各模块的条数，供清空页展示「本应用有多少、删除多少」。
     * 系统的条数由各自的页面刷新，这里只在进清空页时拉一次。
     */
    var localContactCount by mutableStateOf(0)
        private set
    var localSmsCount by mutableStateOf(0)
        private set
    var localCallCount by mutableStateOf(0)
        private set

    /** 刷新清空页要展示的计数 */
    fun refreshClearCounts() {
        viewModelScope.launch {
            localContactCount = _contacts.value.size
            localSmsCount = withContext(Dispatchers.IO) {
                runCatching { archiveRepo.smsTable.count() }.getOrDefault(0)
            }
            localCallCount = withContext(Dispatchers.IO) {
                runCatching { archiveRepo.callTable.count() }.getOrDefault(0)
            }
            systemContactCount = withContext(Dispatchers.IO) {
                runCatching { SystemContacts.countAll(context) }.getOrDefault(0)
            }
            systemScanned = true
            systemSmsCount = withContext(Dispatchers.IO) {
                runCatching { SystemSms.countAll(context) }.getOrDefault(0)
            }
            systemCallCount = withContext(Dispatchers.IO) {
                runCatching { SystemCallLog.countAll(context) }.getOrDefault(0)
            }
        }
    }

    fun toggleClearTarget(target: ClearTarget, on: Boolean) {
        clearTargets = if (on) clearTargets + target else clearTargets - target
    }

    /** 选择清空方向（方法名不叫 setClearDirection，避免和属性的 setter 同名冲突） */
    fun selectClearDirection(direction: ClearDirection) {
        clearDirection = direction
    }

    fun onClearConfirmChange(value: String) {
        clearConfirmInput = value
    }

    /** 确认词是否输入正确（忽略大小写与首尾空格） */
    fun clearConfirmMatched(keyword: String): Boolean =
        clearConfirmInput.trim().equals(keyword.trim(), ignoreCase = true)

    /** 可以执行的条件：勾了至少一项 + 方向已定 + 确认词正确 */
    fun clearReady(keyword: String): Boolean =
        clearTargets.isNotEmpty() && clearConfirmMatched(keyword)

    /**
     * 执行清空。
     *
     * 逐项独立处理，一项失败不影响其他项 —— 比如系统通话记录删不掉
     * （普通应用没有 WRITE_CALL_LOG），短信和通讯录照样能清。
     * 最后把每项的结果汇总成一张弹窗，如实回报删了多少、还剩多少。
     */
    fun executeClear() {
        val keyword = context.getString(R.string.clear_data_confirm_keyword)
        if (!clearReady(keyword)) {
            toast = context.getString(
                if (clearTargets.isEmpty()) {
                    R.string.clear_data_need_select
                } else {
                    R.string.clear_data_confirm_mismatch
                }
            )
            return
        }
        if (busy) return

        val targets = clearTargets.toList()
        val direction = clearDirection
        busy = true
        busyText = context.getString(R.string.busy_clear_data)
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                targets.map { target -> clearOne(target, direction) }
            }
            busy = false
            busyText = ""
            // 无论删的是哪一边，界面上的计数都要重新拉一遍
            refresh()
            refreshClearCounts()
            loadSms()
            loadCallLog()
            resultDialog = ResultDialog(
                context.getString(R.string.clear_data_done_title),
                buildString {
                    results.forEach { r ->
                        appendLine(r.describe(context))
                    }
                    appendLine()
                    append(
                        context.getString(
                            if (direction == ClearDirection.SYSTEM) {
                                R.string.clear_data_local_kept
                            } else {
                                R.string.clear_data_system_kept
                            }
                        )
                    )
                }
            )
            // 执行完把勾选清掉，避免用户返回再进来时看到上次的残留状态
            clearTargets = emptySet()
            clearConfirmInput = ""
        }
    }

    /** 单项清空的结果 */
    private data class ClearResult(
        val target: ClearTarget,
        val deleted: Int,
        val remaining: Int,
        val denied: Boolean
    ) {
        /** 没删干净时给出原因提示，避免用户一头雾水 */
        private fun hintRes(): Int = when (target) {
            ClearTarget.CONTACTS -> R.string.clear_data_hint_contacts
            ClearTarget.SMS -> R.string.clear_data_hint_sms
            ClearTarget.CALLS -> R.string.clear_data_hint_calls
        }

        fun describe(context: Context): String {
            val label = context.getString(
                when (target) {
                    ClearTarget.CONTACTS -> R.string.clear_data_target_contacts
                    ClearTarget.SMS -> R.string.clear_data_target_sms
                    ClearTarget.CALLS -> R.string.clear_data_target_calls
                }
            )
            return buildString {
                if (denied) {
                    append(context.getString(R.string.clear_data_result_denied, label))
                } else {
                    append(
                        context.getString(
                            R.string.clear_data_result_deleted,
                            label,
                            deleted
                        )
                    )
                    if (remaining > 0) {
                        append(
                            context.getString(
                                R.string.clear_data_result_remaining,
                                remaining
                            )
                        )
                    }
                }
                if (denied || remaining > 0) {
                    append("\n")
                    append(context.getString(hintRes()))
                }
            }
        }
    }

    /** 清空一项 */
    private suspend fun clearOne(
        target: ClearTarget,
        direction: ClearDirection
    ): ClearResult = when (direction) {
        ClearDirection.LOCAL -> clearLocal(target)
        ClearDirection.SYSTEM -> clearSystem(target)
    }

    /** 清空本应用里的加密副本 —— 不需要任何系统权限，一定能删 */
    private suspend fun clearLocal(target: ClearTarget): ClearResult = when (target) {
        ClearTarget.CONTACTS -> {
            val before = repo.count()
            repo.deleteAll()
            ClearResult(target, before, 0, false)
        }
        ClearTarget.SMS -> {
            val before = archiveRepo.smsTable.count()
            archiveRepo.smsTable.deleteAll()
            ClearResult(target, before, 0, false)
        }
        ClearTarget.CALLS -> {
            val before = archiveRepo.callTable.count()
            archiveRepo.callTable.deleteAll()
            ClearResult(target, before, 0, false)
        }
    }

    /**
     * 清空系统里的原始数据。
     *
     * 每一项的删除权限不一样，失败的原因也各不相同，如实回报：
     * · 通讯录 —— 需要 WRITE_CONTACTS
     * · 短信 —— Android 4.4 起必须是默认短信应用
     * · 通话记录 —— WRITE_CALL_LOG 是 signature 级权限，普通 App 拿不到
     */
    private suspend fun clearSystem(target: ClearTarget): ClearResult = when (target) {
        ClearTarget.CONTACTS -> {
            if (!PermissionUtil.canWriteContacts(context)) {
                ClearResult(target, 0, 0, true)
            } else {
                var deleted = withContext(Dispatchers.IO) {
                    runCatching { SystemContacts.deleteAll(context) }.getOrDefault(-1)
                }
                var remaining = withContext(Dispatchers.IO) {
                    runCatching { SystemContacts.countAll(context) }.getOrDefault(0)
                }
                // 第一轮没删干净就再来一轮，避免「清完还剩几个」
                if (deleted >= 0 && remaining > 0) {
                    val again = withContext(Dispatchers.IO) {
                        runCatching { SystemContacts.deleteAll(context) }.getOrDefault(0)
                    }
                    if (again > 0) {
                        deleted += again
                        remaining = withContext(Dispatchers.IO) {
                            runCatching { SystemContacts.countAll(context) }.getOrDefault(remaining)
                        }
                    }
                }
                if (deleted < 0) ClearResult(target, 0, remaining, true)
                else ClearResult(target, deleted, remaining, false)
            }
        }
        ClearTarget.SMS -> {
            if (!PermissionUtil.canWriteSms(context)) {
                ClearResult(target, 0, 0, true)
            } else {
                var outcome = withContext(Dispatchers.IO) {
                    runCatching { SystemSms.deleteAll(context) }
                        .getOrElse { SystemSms.DeleteOutcome(0, 0, true) }
                }
                // 第一轮没删干净就再来一轮，避免「清完还剩几个」
                if (!outcome.denied && outcome.remaining > 0) {
                    val again = withContext(Dispatchers.IO) {
                        runCatching { SystemSms.deleteAll(context) }
                            .getOrElse { SystemSms.DeleteOutcome(0, outcome.remaining, false) }
                    }
                    outcome = SystemSms.DeleteOutcome(
                        deleted = outcome.deleted + again.deleted,
                        remaining = again.remaining,
                        denied = outcome.denied || again.denied
                    )
                }
                ClearResult(target, outcome.deleted, outcome.remaining, outcome.denied)
            }
        }
        ClearTarget.CALLS -> {
            if (!PermissionUtil.canWriteCallLog(context)) {
                ClearResult(target, 0, 0, true)
            } else {
                val deleted = withContext(Dispatchers.IO) {
                    runCatching { SystemCallLog.deleteAll(context) }.getOrDefault(-1)
                }
                val remaining = withContext(Dispatchers.IO) {
                    runCatching { SystemCallLog.countAll(context) }.getOrDefault(0)
                }
                if (deleted < 0) ClearResult(target, 0, remaining, true)
                else ClearResult(target, deleted, remaining, false)
            }
        }
    }

    /** 导出 CSV 备份（明文，仅用于自备份，导出后请自行保管） */
    fun exportCsv(uri: Uri) {
        val all = _contacts.value
        if (all.isEmpty()) {
            toast = context.getString(R.string.err_empty_export)
            return
        }
        busy = true
        busyText = context.getString(R.string.busy_export_backup)
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching { SystemContacts.exportCsv(context, uri, all) }.getOrDefault(-1)
            }
            busy = false
            busyText = ""
            if (count < 0) {
                toast = context.getString(R.string.toast_export_failed)
            } else {
                resultDialog = ResultDialog(
                    context.getString(R.string.backup_done_title),
                    context.resources.getQuantityString(R.plurals.backup_done_message, count, count)
                )
            }
        }
    }

    // ————————————————— 来电识别 —————————————————

    // ————————————————— 短信备份 —————————————————

    /** 页面展示条数上限：一次解密几千条会拖慢首屏，分批加载更稳 */
    private val PAGE_SIZE = 200

    fun loadSms(append: Boolean = false) {
        if (!PermissionUtil.canReadSms(context)) {
            smsLoaded = true
            smsMessages = emptyList()
            return
        }
        viewModelScope.launch {
            val offset = if (append) smsMessages.size else 0
            val page = withContext(Dispatchers.IO) {
                runCatching { archiveRepo.smsTable.page(offset, PAGE_SIZE) }.getOrDefault(emptyList())
            }
            val total = withContext(Dispatchers.IO) {
                runCatching { archiveRepo.smsTable.count() }.getOrDefault(0)
            }
            smsMessages = if (append) smsMessages + page else page
            smsLoaded = true
            systemSmsCount = withContext(Dispatchers.IO) {
                runCatching { SystemSms.countAll(context) }.getOrDefault(0)
            }
        }
    }

    /** 从系统导入短信到加密库，按系统短信 id 去重，重复导入只补新的 */
    fun importSms() {
        if (!PermissionUtil.canReadSms(context)) {
            toast = context.getString(R.string.err_read_sms)
            return
        }
        if (busy) return
        busy = true
        busyText = context.getString(R.string.busy_read_sms)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val known = HashSet<Long>()
                    // 先把库里的系统 id 读出来，避免重复导入产生副本
                    var offset = 0
                    while (true) {
                        val page = archiveRepo.smsTable.page(offset, PAGE_SIZE)
                        if (page.isEmpty()) break
                        page.forEach { known.add(it.sourceSmsId) }
                        offset += PAGE_SIZE
                    }

                    val sys = SystemSms.readAll(context)
                    val now = System.currentTimeMillis()
                    val pending = ArrayList<SmsMessage>()
                    var skipped = 0
                    sys.forEach { s ->
                        if (s.id in known) {
                            skipped++
                            return@forEach
                        }
                        pending.add(
                            SmsMessage(
                                address = s.address,
                                body = s.body,
                                date = s.date,
                                type = s.type,
                                read = s.read,
                                threadId = s.threadId,
                                sourceSmsId = s.id,
                                createdAt = now
                            )
                        )
                    }
                    val inserted = archiveRepo.smsTable.insertMany(pending)
                    Triple(sys.size, inserted, skipped)
                }
            }
            busy = false
            busyText = ""
            outcome.onSuccess { (total, inserted, skipped) ->
                loadSms()
                resultDialog = ResultDialog(
                    context.getString(R.string.sms_import_done_title),
                    buildString {
                        appendLine(context.resources.getQuantityString(R.plurals.sms_import_total, total, total))
                        appendLine(context.resources.getQuantityString(R.plurals.sms_import_added, inserted, inserted))
                        if (skipped > 0) {
                            appendLine(context.resources.getQuantityString(R.plurals.sms_import_skipped, skipped, skipped))
                        }
                        appendLine()
                        append(context.getString(R.string.sms_import_encrypted))
                    }
                )
            }.onFailure {
                resultDialog = ResultDialog(
                    context.getString(R.string.sms_import_failed_title),
                    it.message ?: context.getString(R.string.err_read_system_sms)
                )
            }
        }
    }

    fun requestWipeSms() {
        // 删除入口已集中到「清空数据」页，这里只保留跳转
        openClearData()
    }

    fun deleteSms(id: Long) {
        viewModelScope.launch {
            archiveRepo.smsTable.delete(id)
            loadSms()
        }
    }

    /** 把本应用里的短信写回系统 */
    fun exportSmsToSystem() {
        if (!PermissionUtil.canWriteSms(context)) {
            toast = context.getString(R.string.err_write_sms)
            return
        }
        val list = smsMessages
        if (list.isEmpty()) {
            toast = context.getString(R.string.err_no_sms)
            return
        }
        if (busy) return
        busy = true
        busyText = context.getString(R.string.busy_export_sms)
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) {
                runCatching { SystemSms.exportToSystem(context, list) }
            }
            busy = false
            busyText = ""
            summary.onSuccess { s ->
                resultDialog = ResultDialog(
                    context.getString(R.string.sms_export_done_title),
                    buildString {
                        appendLine(context.resources.getQuantityString(R.plurals.sms_export_created, s.created, s.created))
                        if (s.skippedExisting > 0) {
                            appendLine(context.resources.getQuantityString(R.plurals.sms_export_skipped, s.skippedExisting, s.skippedExisting))
                        }
                        if (s.failed > 0) {
                            appendLine(context.resources.getQuantityString(R.plurals.sms_export_failed_count, s.failed, s.failed))
                        }
                        appendLine()
                        append(context.getString(R.string.sms_export_copy_unchanged))
                    }
                )
            }.onFailure {
                resultDialog = ResultDialog(
                    context.getString(R.string.export_failed_title),
                    it.message ?: context.getString(R.string.err_write_system_sms)
                )
            }
        }
    }

    fun exportSmsCsv(uri: Uri) {
        val list = smsMessages
        if (list.isEmpty()) {
            toast = context.getString(R.string.err_no_sms_export)
            return
        }
        busy = true
        busyText = context.getString(R.string.busy_export_sms_backup)
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching { SystemSms.exportCsv(context, uri, list) }.getOrDefault(-1)
            }
            busy = false
            busyText = ""
            if (count < 0) {
                toast = context.getString(R.string.toast_export_failed)
            } else {
                resultDialog = ResultDialog(
                    context.getString(R.string.sms_backup_done_title),
                    context.resources.getQuantityString(R.plurals.sms_backup_done_message, count, count)
                )
            }
        }
    }

    // ————————————————— 通话记录备份 —————————————————

    fun loadCallLog(append: Boolean = false) {
        if (!PermissionUtil.canReadCallLog(context)) {
            callLogLoaded = true
            callRecords = emptyList()
            return
        }
        viewModelScope.launch {
            val offset = if (append) callRecords.size else 0
            val page = withContext(Dispatchers.IO) {
                runCatching { archiveRepo.callTable.page(offset, PAGE_SIZE) }.getOrDefault(emptyList())
            }
            callRecords = if (append) callRecords + page else page
            callLogLoaded = true
            systemCallCount = withContext(Dispatchers.IO) {
                runCatching { SystemCallLog.countAll(context) }.getOrDefault(0)
            }
        }
    }

    /** 从系统导入通话记录到加密库 */
    fun importCallLog() {
        if (!PermissionUtil.canReadCallLog(context)) {
            toast = context.getString(R.string.err_read_calllog)
            return
        }
        if (busy) return
        busy = true
        busyText = context.getString(R.string.busy_read_calllog)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val known = HashSet<Long>()
                    var offset = 0
                    while (true) {
                        val page = archiveRepo.callTable.page(offset, PAGE_SIZE)
                        if (page.isEmpty()) break
                        page.forEach { known.add(it.sourceCallId) }
                        offset += PAGE_SIZE
                    }

                    val sys = SystemCallLog.readAll(context)
                    val now = System.currentTimeMillis()
                    val pending = ArrayList<CallRecord>()
                    var skipped = 0
                    sys.forEach { c ->
                        if (c.id in known) {
                            skipped++
                            return@forEach
                        }
                        pending.add(
                            CallRecord(
                                number = c.number,
                                contactName = c.cachedName,
                                type = c.type,
                                date = c.date,
                                duration = c.duration,
                                sourceCallId = c.id,
                                createdAt = now
                            )
                        )
                    }
                    val inserted = archiveRepo.callTable.insertMany(pending)
                    Triple(sys.size, inserted, skipped)
                }
            }
            busy = false
            busyText = ""
            outcome.onSuccess { (total, inserted, skipped) ->
                loadCallLog()
                resultDialog = ResultDialog(
                    context.getString(R.string.call_import_done_title),
                    buildString {
                        appendLine(context.resources.getQuantityString(R.plurals.call_import_total, total, total))
                        appendLine(context.resources.getQuantityString(R.plurals.call_import_added, inserted, inserted))
                        if (skipped > 0) {
                            appendLine(context.resources.getQuantityString(R.plurals.call_import_skipped, skipped, skipped))
                        }
                        appendLine()
                        append(context.getString(R.string.call_import_encrypted))
                    }
                )
            }.onFailure {
                resultDialog = ResultDialog(
                    context.getString(R.string.import_failed_title),
                    it.message ?: context.getString(R.string.err_read_system_calls)
                )
            }
        }
    }

    fun requestWipeCalls() {
        // 删除入口已集中到「清空数据」页，这里只保留跳转
        openClearData()
    }

    fun deleteCall(id: Long) {
        viewModelScope.launch {
            archiveRepo.callTable.delete(id)
            loadCallLog()
        }
    }

    fun exportCallsCsv(uri: Uri) {
        val list = callRecords
        if (list.isEmpty()) {
            toast = context.getString(R.string.err_no_calls_export)
            return
        }
        busy = true
        busyText = context.getString(R.string.busy_export_calls_backup)
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching { SystemCallLog.exportCsv(context, uri, list) }.getOrDefault(-1)
            }
            busy = false
            busyText = ""
            if (count < 0) {
                toast = context.getString(R.string.toast_export_failed)
            } else {
                resultDialog = ResultDialog(
                    context.getString(R.string.call_backup_done_title),
                    context.resources.getQuantityString(R.plurals.call_backup_done_message, count, count)
                )
            }
        }
    }

    fun refreshCallState() {
        overlayAllowed = PermissionUtil.canDrawOverlays(context)
        screeningRoleHeld = PermissionUtil.hasScreeningRole(context)
    }

    fun onCallServiceChanged(running: Boolean) {
        callServiceRunning = running
        refreshCallState()
    }

    // ————————————————— 列表分组 —————————————————

    /** 按筛选条件过滤，并生成「收藏 / 字母分组」列表结构 */
    fun buildSections(all: List<Contact>, rawQuery: String, filter: String?): List<ContactSection> {
        // 分组在库里是内部键，显示与搜索都要用本地化后的文案
        val groupCache = HashMap<String, String>()
        fun groupText(raw: String): String =
            groupCache.getOrPut(raw) { Labels.groupDisplay(context, raw) }
        val dupLabel = context.getString(R.string.tag_duplicate)

        val filtered = all.filter { contact ->
            val passFilter = when (filter) {
                null -> true
                FAVORITE_FILTER -> contact.favorite
                else -> contact.groupName == filter
            }
            val extras = ArrayList<String>(2)
            if (contact.groupName.isNotBlank()) extras.add(groupText(contact.groupName))
            if (contact.duplicate) extras.add(dupLabel)
            passFilter && PinyinUtil.matches(contact, rawQuery, extras)
        }
        if (filtered.isEmpty()) return emptyList()

        val sections = mutableListOf<ContactSection>()
        val favorites = filtered.filter { it.favorite }
            .sortedBy { PinyinUtil.initials(it.name) }
        if (favorites.isNotEmpty()) {
            sections += ContactSection(context.getString(R.string.home_section_favorites), favorites)
        }
        filtered.filter { !it.favorite }
            .groupBy { PinyinUtil.bucket(it.name) }
            .toSortedMap(compareBy { if (it == "#") "ZZZ" else it })
            .forEach { (letter, items) ->
                sections += ContactSection(letter, items.sortedBy { PinyinUtil.initials(it.name) })
            }
        return sections
    }
}
