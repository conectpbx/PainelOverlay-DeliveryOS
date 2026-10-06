package com.painel.overlay

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class ApiServer(private val context: Context, port: Int) : NanoHTTPD(port) {
    @Volatile var speedKmh: Double = 0.0
    @Volatile var accuracyM: Double = -1.0
    @Volatile var tripKm: Double = 0.0
    @Volatile var totalKm: Double = 0.0
    @Volatile var lastUpdateMs: Long = 0L

    private val dashboardHtml: String by lazy { readAsset("web/dashboard.html") }

    private fun readAsset(path: String): String {
        return context.assets.open(path).use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val resp = when {
            session.method == Method.OPTIONS ->
                newFixedLengthResponse(Response.Status.OK, "text/plain", "")
            session.uri == "/status" -> {
                val json = JSONObject().apply {
                    put("speed_kmh", speedKmh)
                    put("accuracy_m", accuracyM)
                    put("trip_km", tripKm)
                    put("total_km", totalKm)
                    put("updated_at", lastUpdateMs)
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            }
            session.uri == "/" || session.uri == "/index.html" ->
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", dashboardHtml)
            else ->
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
        resp.addHeader("Access-Control-Allow-Origin", "*")
        resp.addHeader("Cache-Control", "no-store")
        return resp
    }
}
