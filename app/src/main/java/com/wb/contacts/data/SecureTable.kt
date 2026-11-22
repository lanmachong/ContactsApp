package com.wb.contacts.data

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 通用加密表。
 *
 * 表结构固定为「自增 id + 整条记录的密文 BLOB」，没有任何明文字段名，
 * 拿到 db 文件也读不出内容。
 *
 * 两种使用方式：
 * · cached = true（联系人）：一次性全量解密并缓存在内存，冷启动只解密一次
 * · cached = false（短信 / 通话记录）：分页按需解密，避免上千条一次性进内存
 */
class SecureTable<T>(
    private val helper: ContactsDbHelper,
    private val table: String,
    private val encrypt: (T) -> ByteArray,
    private val decrypt: (ByteArray, Long) -> T,
    private val cached: Boolean = false
) {

    @Volatile
    private var cache: List<T>? = null

    private val loadMutex = Mutex()

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $table", null
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /** 全量读取（仅 cached 表使用） */
    suspend fun getAll(): List<T> {
        if (!cached) return page(0, Int.MAX_VALUE.coerceAtMost(MAX_PAGE))
        cache?.let { return it }
        loadMutex.lock()
        try {
            cache?.let { return it }
            val result = page(0, MAX_PAGE)
            cache = result
            return result
        } finally {
            loadMutex.unlock()
        }
    }

    /** 一页的读取结果：[items] 是解密成功的记录，[rowsRead] 是本页从数据库实际读到的行数。 */
    data class PageResult<T>(val items: List<T>, val rowsRead: Int)

    /**
     * 分页读取：先取这一页的密文，再并发解密。
     * 解密失败（数据损坏 / 密钥变更）只丢这一条，不影响整页。
     */
    suspend fun page(offset: Int, limit: Int): List<T> = pageWithRows(offset, limit).items

    /**
     * 与 [page] 相同，但额外返回本页实际读到的数据库行数。
     *
     * 遍历整表时必须用 [PageResult.rowsRead] 推进 offset，绝不能用 items.size：
     * [page] 内部会 filterNotNull 丢掉解密失败的记录，items.size 可能小于真实读取行数，
     * 用它推进会导致 offset 落后 → 重复读取，甚至在「整页都解密失败」时提前终止遍历。
     */
    private suspend fun pageWithRows(offset: Int, limit: Int): PageResult<T> =
        withContext(Dispatchers.IO) {
            if (limit <= 0) return@withContext PageResult(emptyList(), 0)
            val rows = ArrayList<Pair<Long, ByteArray>>()
            helper.readableDatabase.query(
                table,
                arrayOf(ContactsDbHelper.COL_ID, ContactsDbHelper.COL_PAYLOAD),
                null, null, null, null,
                "${ContactsDbHelper.COL_ID} DESC LIMIT $offset, $limit"
            ).use { c ->
                val idIndex = c.getColumnIndexOrThrow(ContactsDbHelper.COL_ID)
                val payloadIndex = c.getColumnIndexOrThrow(ContactsDbHelper.COL_PAYLOAD)
                while (c.moveToNext()) {
                    val id = c.getLong(idIndex)
                    val payload = c.getBlob(payloadIndex) ?: continue
                    rows.add(id to payload)
                }
            }
            val rowsRead = rows.size
            if (rows.isEmpty()) return@withContext PageResult(emptyList(), 0)

            val items = withContext(Dispatchers.Default) {
                val gate = Semaphore(DECRYPT_CONCURRENCY)
                rows.map { (id, payload) ->
                    async {
                        gate.withPermit { runCatching { decrypt(payload, id) }.getOrNull() }
                    }
                }.awaitAll().filterNotNull()
            }
            PageResult(items, rowsRead)
        }

    /**
     * 逐页遍历整表。
     *
     * 导出上千条记录时用它：一页一页解密、一页一页处理，
     * 处理完就释放，不用把上万条一次性塞进内存。
     */
    suspend fun forEachPage(pageSize: Int = 500, onPage: suspend (List<T>) -> Unit) {
        var offset = 0
        while (true) {
            val result = pageWithRows(offset, pageSize)
            if (result.rowsRead == 0) break
            // 整页都解密失败时 items 为空但 rowsRead > 0：offset 仍必须按真实行数推进，
            // 不能靠 items.isEmpty() 提前 break，否则后续页全部漏掉。
            if (result.items.isNotEmpty()) onPage(result.items)
            offset += result.rowsRead
            if (result.rowsRead < pageSize) break
        }
    }

    suspend fun insert(item: T): Long = withContext(Dispatchers.IO) {
        val id = helper.writableDatabase.insert(table, null, values(item))
        invalidate()
        id
    }

    /**
     * 批量写入，单事务提交；返回成功条数。
     *
     * 两步走：先在 [Dispatchers.Default] 上并发把整批记录加密成密文，再用一条
     * 预编译的 INSERT 语句在单事务里顺序写库。以前是「加密一条、编译一条 SQL、插一条」，
     * 上万条时要编译上万次 SQL；现在加密并行、SQL 只编译一次。
     *
     * [onProgress] 每写入 50 条回调一次累计条数 —— 上万条加密写入可能要几十秒，
     * 没有它进度会卡在最后一步不动。
     */
    suspend fun insertMany(
        items: List<T>,
        onProgress: (Int) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext 0
        val payloads = withContext(Dispatchers.Default) {
            val gate = Semaphore(ENCRYPT_CONCURRENCY)
            items.map { item ->
                async {
                    gate.withPermit { runCatching { encrypt(item) }.getOrNull() }
                }
            }.awaitAll()
        }
        val db = helper.writableDatabase
        val statement = db.compileStatement(
            "INSERT INTO $table (${ContactsDbHelper.COL_PAYLOAD}) VALUES (?)"
        )
        var inserted = 0
        db.beginTransaction()
        try {
            payloads.forEachIndexed { index, payload ->
                if (payload != null) {
                    statement.clearBindings()
                    statement.bindBlob(1, payload)
                    if (statement.executeInsert() > 0) inserted++
                }
                if ((index + 1) % 50 == 0 || index == items.lastIndex) onProgress(index + 1)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            statement.close()
        }
        invalidate()
        inserted
    }

    suspend fun update(id: Long, item: T): Int = withContext(Dispatchers.IO) {
        val rows = helper.writableDatabase.update(
            table,
            values(item),
            "${ContactsDbHelper.COL_ID} = ?",
            arrayOf(id.toString())
        )
        invalidate()
        rows
    }

    suspend fun delete(id: Long): Int = withContext(Dispatchers.IO) {
        val rows = helper.writableDatabase.delete(
            table,
            "${ContactsDbHelper.COL_ID} = ?",
            arrayOf(id.toString())
        )
        invalidate()
        rows
    }

    suspend fun deleteAll(): Int = withContext(Dispatchers.IO) {
        val rows = helper.writableDatabase.delete(table, null, null)
        invalidate()
        rows
    }

    private fun invalidate() {
        cache = null
    }

    private fun values(item: T): ContentValues = ContentValues().apply {
        put(ContactsDbHelper.COL_PAYLOAD, encrypt(item))
    }

    private companion object {
        const val DECRYPT_CONCURRENCY = 8
        const val ENCRYPT_CONCURRENCY = 8
        const val MAX_PAGE = 100000
    }
}
