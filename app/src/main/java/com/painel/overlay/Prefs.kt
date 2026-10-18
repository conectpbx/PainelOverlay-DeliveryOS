package com.painel.overlay

import android.content.Context

object Prefs {
    private const val FILE_NAME = "painel_prefs"
    private const val KEY_TOTAL = "total_km"
    private const val KEY_TRIP = "trip_km"
    private const val KEY_DELIVERY_ENDPOINT = "delivery_endpoint"
    private const val KEY_DELIVERY_TOKEN = "delivery_token"
    private const val KEY_DELIVERY_SYNC = "delivery_sync"
    private const val KEY_DELIVERY_LAST_RESULT = "delivery_last_result"
    private const val KEY_DELIVERY_METHOD = "delivery_method"
    private const val KEY_DELIVERY_AUTH_TYPE = "delivery_auth_type"
    private const val KEY_DELIVERY_HEADER_NAME = "delivery_header_name"
    private const val KEY_DELIVERY_INTERVAL_SECONDS = "delivery_interval_seconds"
    private const val KEY_ACTIVE_ROUTE_ID = "active_route_id"
    private const val KEY_ACTIVE_ROUTE_STARTED_AT = "active_route_started_at"
    private const val KEY_PENDING_FINISH_ROUTE_ID = "pending_finish_route_id"
    private const val KEY_PENDING_FINISH_ENDED_AT = "pending_finish_ended_at"
    private const val KEY_PENDING_FINISH_TOTAL_KM = "pending_finish_total_km"
    private const val KEY_PENDING_FINISH_DISTANCE_KM = "pending_finish_distance_km"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun getTotal(ctx: Context) = sp(ctx).getString(KEY_TOTAL, "0")?.toDoubleOrNull() ?: 0.0
    fun getTrip(ctx: Context) = sp(ctx).getString(KEY_TRIP, "0")?.toDoubleOrNull() ?: 0.0
    fun setTotal(ctx: Context, km: Double) { sp(ctx).edit().putString(KEY_TOTAL, km.toString()).apply() }
    fun setTrip(ctx: Context, km: Double) { sp(ctx).edit().putString(KEY_TRIP, km.toString()).apply() }
    fun addDistanceKm(ctx: Context, km: Double) {
        setTotal(ctx, getTotal(ctx) + km)
        setTrip(ctx, getTrip(ctx) + km)
    }
    fun resetTrip(ctx: Context) = setTrip(ctx, 0.0)

    fun getDeliveryEndpoint(ctx: Context) = sp(ctx).getString(KEY_DELIVERY_ENDPOINT, "") ?: ""
    fun setDeliveryEndpoint(ctx: Context, v: String) { sp(ctx).edit().putString(KEY_DELIVERY_ENDPOINT, v).apply() }
    fun getDeliveryToken(ctx: Context) = sp(ctx).getString(KEY_DELIVERY_TOKEN, "") ?: ""
    fun setDeliveryToken(ctx: Context, v: String) { sp(ctx).edit().putString(KEY_DELIVERY_TOKEN, v).apply() }
    fun isDeliverySyncEnabled(ctx: Context) = sp(ctx).getBoolean(KEY_DELIVERY_SYNC, false)
    fun setDeliverySyncEnabled(ctx: Context, v: Boolean) { sp(ctx).edit().putBoolean(KEY_DELIVERY_SYNC, v).apply() }
    fun getDeliveryLastResult(ctx: Context) = sp(ctx).getString(KEY_DELIVERY_LAST_RESULT, "nunca enviado") ?: "nunca enviado"
    fun setDeliveryLastResult(ctx: Context, v: String) { sp(ctx).edit().putString(KEY_DELIVERY_LAST_RESULT, v).apply() }

    fun getDeliveryMethod(ctx: Context) = sp(ctx).getString(KEY_DELIVERY_METHOD, "POST") ?: "POST"
    fun setDeliveryMethod(ctx: Context, v: String) { sp(ctx).edit().putString(KEY_DELIVERY_METHOD, v).apply() }
    fun getDeliveryAuthType(ctx: Context) = sp(ctx).getString(KEY_DELIVERY_AUTH_TYPE, "Bearer Token") ?: "Bearer Token"
    fun setDeliveryAuthType(ctx: Context, v: String) { sp(ctx).edit().putString(KEY_DELIVERY_AUTH_TYPE, v).apply() }
    fun getDeliveryHeaderName(ctx: Context) = sp(ctx).getString(KEY_DELIVERY_HEADER_NAME, "Authorization") ?: "Authorization"
    fun setDeliveryHeaderName(ctx: Context, v: String) { sp(ctx).edit().putString(KEY_DELIVERY_HEADER_NAME, v).apply() }
    fun getDeliveryIntervalSeconds(ctx: Context) = sp(ctx).getInt(KEY_DELIVERY_INTERVAL_SECONDS, 5).coerceIn(2, 3600)
    fun setDeliveryIntervalSeconds(ctx: Context, v: Int) {
        sp(ctx).edit().putInt(KEY_DELIVERY_INTERVAL_SECONDS, v.coerceIn(2, 3600)).apply()
    }

    fun getActiveRouteId(ctx: Context): String? =
        sp(ctx).getString(KEY_ACTIVE_ROUTE_ID, null)

    fun getActiveRouteStartedAt(ctx: Context): Long =
        sp(ctx).getLong(KEY_ACTIVE_ROUTE_STARTED_AT, 0L)

    fun startRoute(ctx: Context, routeId: String, startedAt: Long = System.currentTimeMillis()) {
        sp(ctx).edit()
            .putString(KEY_ACTIVE_ROUTE_ID, routeId)
            .putLong(KEY_ACTIVE_ROUTE_STARTED_AT, startedAt)
            .apply()
    }

    fun queueRouteFinish(ctx: Context, endedAt: Long = System.currentTimeMillis()) {
        val routeId = getActiveRouteId(ctx) ?: return
        sp(ctx).edit()
            .putString(KEY_PENDING_FINISH_ROUTE_ID, routeId)
            .putLong(KEY_PENDING_FINISH_ENDED_AT, endedAt)
            .putString(KEY_PENDING_FINISH_TOTAL_KM, getTotal(ctx).toString())
            .putString(KEY_PENDING_FINISH_DISTANCE_KM, getTrip(ctx).toString())
            .remove(KEY_ACTIVE_ROUTE_ID)
            .remove(KEY_ACTIVE_ROUTE_STARTED_AT)
            .apply()
    }

    data class PendingRouteFinish(
        val routeId: String,
        val endedAt: Long,
        val totalKm: Double,
        val distanceKm: Double
    )

    fun getPendingRouteFinish(ctx: Context): PendingRouteFinish? {
        val s = sp(ctx)
        val id = s.getString(KEY_PENDING_FINISH_ROUTE_ID, null) ?: return null
        return PendingRouteFinish(
            routeId = id,
            endedAt = s.getLong(KEY_PENDING_FINISH_ENDED_AT, System.currentTimeMillis()),
            totalKm = s.getString(KEY_PENDING_FINISH_TOTAL_KM, "0")?.toDoubleOrNull() ?: 0.0,
            distanceKm = s.getString(KEY_PENDING_FINISH_DISTANCE_KM, "0")?.toDoubleOrNull() ?: 0.0
        )
    }

    fun clearPendingRouteFinish(ctx: Context) {
        sp(ctx).edit()
            .remove(KEY_PENDING_FINISH_ROUTE_ID)
            .remove(KEY_PENDING_FINISH_ENDED_AT)
            .remove(KEY_PENDING_FINISH_TOTAL_KM)
            .remove(KEY_PENDING_FINISH_DISTANCE_KM)
            .apply()
    }
}
