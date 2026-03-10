package com.example.sentineiai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class BehaviorLogEntry(
    val id: Int = 0,
    val timestamp: Long,
    val foregroundApp: String,
    val uploadBytes: Long,
    val screenState: String,
    val riskScore: Int,
    val riskLevel: String
)

class SentinelDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "sentinel_logs.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "behavior_logs"

        private const val COL_ID = "id"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_FOREGROUND_APP = "foregroundApp"
        private const val COL_UPLOAD_BYTES = "uploadBytes"
        private const val COL_SCREEN_STATE = "screenState"
        private const val COL_RISK_SCORE = "riskScore"
        private const val COL_RISK_LEVEL = "riskLevel"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP INTEGER,
                $COL_FOREGROUND_APP TEXT,
                $COL_UPLOAD_BYTES INTEGER,
                $COL_SCREEN_STATE TEXT,
                $COL_RISK_SCORE INTEGER,
                $COL_RISK_LEVEL TEXT
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertLog(entry: BehaviorLogEntry): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_TIMESTAMP, entry.timestamp)
            put(COL_FOREGROUND_APP, entry.foregroundApp)
            put(COL_UPLOAD_BYTES, entry.uploadBytes)
            put(COL_SCREEN_STATE, entry.screenState)
            put(COL_RISK_SCORE, entry.riskScore)
            put(COL_RISK_LEVEL, entry.riskLevel)
        }
        val id = db.insert(TABLE_NAME, null, values)
        db.close()
        return id
    }

    fun getRecentLogs(limit: Int): List<BehaviorLogEntry> {
        val logs = mutableListOf<BehaviorLogEntry>()
        val db = this.readableDatabase
        val selectQuery = "SELECT * FROM $TABLE_NAME ORDER BY $COL_TIMESTAMP DESC LIMIT $limit"
        
        val cursor = db.rawQuery(selectQuery, null)
        if (cursor.moveToFirst()) {
            do {
                val entry = BehaviorLogEntry(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)),
                    foregroundApp = cursor.getString(cursor.getColumnIndexOrThrow(COL_FOREGROUND_APP)),
                    uploadBytes = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPLOAD_BYTES)),
                    screenState = cursor.getString(cursor.getColumnIndexOrThrow(COL_SCREEN_STATE)),
                    riskScore = cursor.getInt(cursor.getColumnIndexOrThrow(COL_RISK_SCORE)),
                    riskLevel = cursor.getString(cursor.getColumnIndexOrThrow(COL_RISK_LEVEL))
                )
                logs.add(entry)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return logs
    }

    fun clearLogs() {
        val db = this.writableDatabase
        db.delete(TABLE_NAME, null, null)
        db.close()
    }
}
