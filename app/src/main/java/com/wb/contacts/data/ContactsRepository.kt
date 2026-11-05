package com.wb.contacts.data

import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class ContactsRepository(context: Context) {

    private val helper = ContactsDbHelper(context.applicationContext)

    /**
     * 内存缓存。
     *
     * 每条记录都是独立加密的密文，打开 App 必须逐条解密 —— 这是加密存储的固有成本。
     * 但同一次冷启动里 Application 的来电索引预热和 ViewModel 的列表刷新各要一次全量解密，
     * 白白多花一倍时间（几百条就是 1～2 秒）。缓存 + 只解密一次后，两边共用同一份结果。
     */
    @Volatile
    private var cache: List<Contact>? = null

    /** 保证同一时刻只有一个协程在解密，其余等待结果复用 */
    private val loadMutex = Mutex()

    suspend fun getAll(): List<Contact> {
        cache?.let { return it }
        loadMutex.lock()
        try {
            cache?.let { return it }
            val result = readAllFromDb()
            cache = result
            return result
        } finally {
            loadMutex.unlock()
        }
    }

    /** 写操作后必须失效缓存，否则界面拿到的是旧数据 */
    private fun invalidate() {
        cache = null
    }

    /**
     * 读取并解密全部记录。
     * 解密走 Android Keystore，每条都要一次硬件密钥操作，串行会很慢；
     * 这里用固定并发度并行解密，几百条能快 3～4 倍。
     */
    private suspend fun readAllFromDb(): List<Contact> {
        val rows = withContext(Dispatchers.IO) {
            val result = ArrayList<Pair<Long, ByteArray>>()
            val cursor = helper.readableDatabase.query(
                ContactsDbHelper.TABLE,
                arrayOf(ContactsDbHelper.COL_ID, ContactsDbHelper.COL_PAYLOAD),
                null, null, null, null, null
            )
            cursor.use { c ->
                val idIndex = c.getColumnIndexOrThrow(ContactsDbHelper.COL_ID)
                val payloadIndex = c.getColumnIndexOrThrow(ContactsDbHelper.COL_PAYLOAD)
                while (c.moveToNext()) {
                    val id = c.getLong(idIndex)
                    val payload = c.getBlob(payloadIndex) ?: continue
                    result.add(id to payload)
                }
            }
            result
        }
        if (rows.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            val gate = Semaphore(DECRYPT_CONCURRENCY)
            rows.map { (id, payload) ->
                async {
                    gate.withPermit {
                        // 单条解密失败（密钥变更/数据损坏）不应影响整表明细
                        runCatching { Contact.fromPayload(payload, id) }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    suspend fun insert(contact: Contact): Long = withContext(Dispatchers.IO) {
        val id = helper.writableDatabase.insert(ContactsDbHelper.TABLE, null, contact.values())
        invalidate()
        id
    }

    /** 批量导入，单事务提交；返回成功写入条数 */
    suspend fun insertMany(contacts: List<Contact>): Int = withContext(Dispatchers.IO) {
        if (contacts.isEmpty()) return@withContext 0
        val db = helper.writableDatabase
        var inserted = 0
        db.beginTransaction()
        try {
            contacts.forEach { contact ->
                if (db.insert(ContactsDbHelper.TABLE, null, contact.values()) > 0) inserted++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        invalidate()
        inserted
    }

    suspend fun update(contact: Contact): Int = withContext(Dispatchers.IO) {
        val rows = helper.writableDatabase.update(
            ContactsDbHelper.TABLE,
            contact.values(),
            "${ContactsDbHelper.COL_ID} = ?",
            arrayOf(contact.id.toString())
        )
        invalidate()
        rows
    }

    suspend fun delete(id: Long): Int = withContext(Dispatchers.IO) {
        val rows = helper.writableDatabase.delete(
            ContactsDbHelper.TABLE,
            "${ContactsDbHelper.COL_ID} = ?",
            arrayOf(id.toString())
        )
        invalidate()
        rows
    }

    suspend fun deleteAll(): Int = withContext(Dispatchers.IO) {
        val rows = helper.writableDatabase.delete(ContactsDbHelper.TABLE, null, null)
        invalidate()
        rows
    }

    /** 去重后的分组名（分组信息加密在 payload 内，只能在内存中统计） */
    suspend fun distinctGroups(): List<String> =
        getAll().map { it.groupName }.filter { it.isNotBlank() }.distinct().sorted()

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${ContactsDbHelper.TABLE}", null
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    private fun Contact.values(): ContentValues = ContentValues().apply {
        put(ContactsDbHelper.COL_PAYLOAD, toPayload())
    }

    private companion object {
        /** Keystore 解密并发度，再高收益有限，TEE 本身有串行化 */
        const val DECRYPT_CONCURRENCY = 8
    }
}
