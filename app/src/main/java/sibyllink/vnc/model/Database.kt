package sibyllink.vnc.model

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Database(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    val database: SQLiteDatabase = this.writableDatabase
    fun addServerProfile(host: String, port: Int, username: String, password: String) {
        val values = ContentValues().apply {
            put(COLUMN_HOST, host)
            put(COLUMN_PORT, port)
            put(COLUMN_USERNAME, username)
            put(COLUMN_PASSWORD, password)
        }
        database.insert(TABLE_SERVER_PROFILES, null, values)
    }

    fun updateServerProfile(id: Int, host: String, port: Int, username: String, password: String) {
        val values = ContentValues().apply {
            put(COLUMN_HOST, host)
            put(COLUMN_PORT, port)
            put(COLUMN_USERNAME, username)
            put(COLUMN_PASSWORD, password)
        }

        val selection = "$COLUMN_ID = ?"
        val selectionArgs = arrayOf(id.toString())

        database.update(TABLE_SERVER_PROFILES, values, selection, selectionArgs)
    }

    fun deleteServerProfile(id: Int) {
        val selection = "$COLUMN_ID = ?"
        val selectionArgs = arrayOf(id.toString())
        database.delete(TABLE_SERVER_PROFILES, selection, selectionArgs)
    }

    fun getAll(): List<ServerProfile> {
        val profiles = mutableListOf<ServerProfile>()
        val cursor = database.query(
            TABLE_SERVER_PROFILES, null, null, null, null, null, null
        )

        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            val profile = cursorToServerProfile(cursor)
            profiles.add(profile)
            cursor.moveToNext()
        }
        cursor.close()
        return profiles
    }

    private fun cursorToServerProfile(cursor: Cursor): ServerProfile {
        val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
        val host = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HOST))
        val port = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PORT))
        val username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME))
        val password = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD))
        return ServerProfile(id, host, port, username, password)
    }

    companion object {
        private const val DATABASE_NAME = "server_profiles.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_SERVER_PROFILES = "server_profiles"
        const val COLUMN_ID = "id"
        const val COLUMN_HOST = "host"
        const val COLUMN_PORT = "port"
        const val COLUMN_USERNAME = "username"
        const val COLUMN_PASSWORD = "password"

        private const val TABLE_CREATE =
            "CREATE TABLE $TABLE_SERVER_PROFILES ($COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_HOST TEXT NOT NULL, $COLUMN_PORT INTEGER NOT NULL, $COLUMN_USERNAME TEXT, $COLUMN_PASSWORD TEXT);"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(TABLE_CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SERVER_PROFILES")
        onCreate(db)
    }
}