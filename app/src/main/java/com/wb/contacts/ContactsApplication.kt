package com.wb.contacts

import android.app.Application
import com.wb.contacts.call.CallNotifier
import com.wb.contacts.call.CallOverlayController
import com.wb.contacts.call.CallerIndex
import com.wb.contacts.data.ArchiveRepository
import com.wb.contacts.data.ContactsRepository
import com.wb.contacts.data.Crypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ContactsApplication : Application() {

    /** 全局唯一仓储（数据库 + 加解密） */
    val repository: ContactsRepository by lazy { ContactsRepository(this) }

    /** 短信 / 通话记录仓储，与 ViewModel 共用实例 */
    val archiveRepository: ArchiveRepository by lazy { ArchiveRepository(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        CallNotifier.ensureChannel(this)
        CallOverlayController.init(this)
        // 提前把 Keystore 密钥准备好，别让首屏等硬件
        scope.launch { runCatching { Crypto.warmUp() } }
        warmUpCallerIndex()
    }

    /**
     * 把加密库里的号码解密后建到内存索引里。
     * 来电时需要在几十毫秒内完成匹配，不能等数据库查询。
     */
    fun warmUpCallerIndex() {
        scope.launch {
            runCatching { CallerIndex.update(repository.getAll()) }
        }
    }

    companion object {
        @Volatile
        lateinit var instance: ContactsApplication
            private set
    }
}
