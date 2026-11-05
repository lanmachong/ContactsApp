package com.wb.contacts.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 加密库：每张表只有自增 id 与整条记录的密文 BLOB，
 * 没有任何明文字段名/字段值，拿到 db 文件也无法读取内容。
 *
 * v2 起新增 sms / call_log 两张表，结构与 contacts 完全一致。
 * 升级只建新表，绝不 DROP 老表，避免用户数据丢失。
 */
class ContactsDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        createTable(db, TABLE_CONTACTS)
        createTable(db, TABLE_SMS)
        createTable(db, TABLE_CALL_LOG)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 只有 contacts 表；v2 追加 sms / call_log。
        // 禁止 DROP TABLE，否则升级会抹掉用户已导入的数据。
        createTable(db, TABLE_CONTACTS)
        createTable(db, TABLE_SMS)
        createTable(db, TABLE_CALL_LOG)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 装了旧版本 APK 再装新版：同样只补建表，不动已有数据
        createTable(db, TABLE_CONTACTS)
        createTable(db, TABLE_SMS)
        createTable(db, TABLE_CALL_LOG)
    }

    private fun createTable(db: SQLiteDatabase, table: String) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $table (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PAYLOAD BLOB NOT NULL
            )
            """.trimIndent()
        )
    }

    companion object {
        const val DB_NAME = "contacts.db"
        const val DB_VERSION = 2

        const val COL_ID = "id"
        const val COL_PAYLOAD = "payload"

        const val TABLE_CONTACTS = "contacts"
        const val TABLE_SMS = "sms"
        const val TABLE_CALL_LOG = "call_log"

        /** 兼容旧引用名 */
        const val TABLE = TABLE_CONTACTS
    }
}
