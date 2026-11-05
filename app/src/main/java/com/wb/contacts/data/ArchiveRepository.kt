package com.wb.contacts.data

import android.content.Context

/**
 * 短信与通话记录的加密仓储。
 *
 * 用会话密钥（软件 AES-GCM）而不是 Keystore 直连：这两个表动辄上千条，
 * 逐条走硬件密钥会慢到十几秒。
 */
class ArchiveRepository(context: Context) {

    private val helper = ContactsDbHelper(context.applicationContext)
    private val cipher = SessionCipher(context.applicationContext)

    val smsTable = SecureTable(
        helper = helper,
        table = ContactsDbHelper.TABLE_SMS,
        encrypt = { it.toPayload(cipher) },
        decrypt = { payload, id -> SmsMessage.fromPayload(payload, id, cipher) },
        cached = false
    )

    val callTable = SecureTable(
        helper = helper,
        table = ContactsDbHelper.TABLE_CALL_LOG,
        encrypt = { it.toPayload(cipher) },
        decrypt = { payload, id -> CallRecord.fromPayload(payload, id, cipher) },
        cached = false
    )

    fun warmUp() {
        cipher.warmUp()
    }
}
