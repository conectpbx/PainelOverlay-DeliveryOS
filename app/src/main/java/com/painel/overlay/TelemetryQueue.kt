package com.painel.overlay

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TelemetryQueue(context: Context) :
    SQLiteOpenHelper(context, "telemetry_queue.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE telemetry_points (
                id TEXT PRIMARY KEY,
                source TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                accuracy_m REAL NOT NULL,
                speed_kmh REAL NOT NULL,
                trip_km REAL NOT NULL,
                total_km REAL NOT NULL,
                captured_at INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX telemetry_points_created_idx ON telemetry_points(created_at ASC)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun enqueue(point: TelemetryPoint) {
        val values = ContentValues().apply {
            put("id", point.id)
            put("source", point.source)
            put("latitude", point.latitude)
            put("longitude", point.longitude)
            put("accuracy_m", point.accuracyM)
            put("speed_kmh", point.speedKmh)
            put("trip_km", point.tripKm)
            put("total_km", point.totalKm)
            put("captured_at", point.capturedAt)
            put("created_at", point.createdAt)
        }
        writableDatabase.insertWithOnConflict(
            "telemetry_points",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        trimToLimit()
    }

    @Synchronized
    fun oldest(limit: Int = 50): List<TelemetryPoint> {
        val result = mutableListOf<TelemetryPoint>()
        readableDatabase.query(
            "telemetry_points",
            null,
            null,
            null,
            null,
            null,
            "created_at ASC",
            limit.coerceIn(1, 200).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += TelemetryPoint(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    source = cursor.getString(cursor.getColumnIndexOrThrow("source")),
                    latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                    longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                    accuracyM = cursor.getDouble(cursor.getColumnIndexOrThrow("accuracy_m")),
                    speedKmh = cursor.getDouble(cursor.getColumnIndexOrThrow("speed_kmh")),
                    tripKm = cursor.getDouble(cursor.getColumnIndexOrThrow("trip_km")),
                    totalKm = cursor.getDouble(cursor.getColumnIndexOrThrow("total_km")),
                    capturedAt = cursor.getLong(cursor.getColumnIndexOrThrow("captured_at")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return result
    }

    @Synchronized
    fun remove(id: String) {
        writableDatabase.delete("telemetry_points", "id = ?", arrayOf(id))
    }

    @Synchronized
    fun count(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM telemetry_points", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    private fun trimToLimit(maxRows: Int = 25_000) {
        writableDatabase.execSQL(
            """
            DELETE FROM telemetry_points
            WHERE id IN (
                SELECT id FROM telemetry_points
                ORDER BY created_at ASC
                LIMIT MAX((SELECT COUNT(*) FROM telemetry_points) - $maxRows, 0)
            )
            """.trimIndent()
        )
    }
}
