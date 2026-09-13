package org.schabi.newpipe.download

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.Closeable

/** Device-local receipts survive restarts and do not grow an in-memory preference set. */
internal class AutomaticDownloadHistory(context: Context, name: String = "automatic-downloads.db") : SQLiteOpenHelper(context, name, null, 1), Closeable {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE receipts (channel INTEGER NOT NULL, source TEXT NOT NULL, PRIMARY KEY(channel, source))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun contains(channel: Long, source: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM receipts WHERE channel=? AND source=?",
        arrayOf(channel.toString(), source)
    ).use { it.moveToFirst() }

    fun add(channel: Long, source: String) {
        writableDatabase.insertWithOnConflict(
            "receipts",
            null,
            ContentValues().apply {
                put("channel", channel)
                put("source", source)
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }
}
