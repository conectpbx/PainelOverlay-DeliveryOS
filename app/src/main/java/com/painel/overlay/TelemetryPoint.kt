package com.painel.overlay

import android.location.Location
import org.json.JSONObject
import java.util.UUID

data class TelemetryPoint(
    val id: String,
    val routeId: String?,
    val source: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double,
    val speedKmh: Double,
    val tripKm: Double,
    val totalKm: Double,
    val capturedAt: Long,
    val createdAt: Long
) {
    fun toJson(sentAt: Long = System.currentTimeMillis()): String =
        JSONObject().apply {
            put("point_id", id)
            if (routeId != null) put("route_id", routeId)
            put("source", source)
            put("latitude", latitude)
            put("longitude", longitude)
            put("accuracy_m", accuracyM)
            put("speed_kmh", speedKmh)
            put("trip_km", tripKm)
            put("total_km", totalKm)
            put("captured_at", capturedAt)
            put("sent_at", sentAt)
        }.toString()

    companion object {
        fun fromLocation(
            location: Location,
            routeId: String?,
            speedKmh: Double,
            tripKm: Double,
            totalKm: Double
        ): TelemetryPoint {
            val now = System.currentTimeMillis()
            return TelemetryPoint(
                id = UUID.randomUUID().toString(),
                routeId = routeId,
                source = "painel-overlay",
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyM = location.accuracy.toDouble().coerceAtLeast(0.0),
                speedKmh = speedKmh.coerceAtLeast(0.0),
                tripKm = tripKm.coerceAtLeast(0.0),
                totalKm = totalKm.coerceAtLeast(0.0),
                capturedAt = if (location.time > 0L) location.time else now,
                createdAt = now
            )
        }
    }
}
