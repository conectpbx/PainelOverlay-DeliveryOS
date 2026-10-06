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
}
