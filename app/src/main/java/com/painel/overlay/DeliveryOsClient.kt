package com.painel.overlay

import android.content.Context
import android.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DeliveryOsClient(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val sending = AtomicBoolean(false)
    @Volatile private var lastSentAt = 0L

    fun send(location: Location, speedKmh: Double, tripKm: Double, totalKm: Double) {
        if (!Prefs.isDeliverySyncEnabled(context)) return

        val now = System.currentTimeMillis()
        val interval = Prefs.getDeliveryIntervalSeconds(context) * 1000L
        if (now - lastSentAt < interval || !sending.compareAndSet(false, true)) return

        lastSentAt = now
        val payload = JSONObject().apply {
            put("source", "painel-overlay")
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy_m", location.accuracy.toDouble().coerceAtLeast(0.0))
            put("speed_kmh", speedKmh.coerceAtLeast(0.0))
            put("trip_km", tripKm.coerceAtLeast(0.0))
            put("total_km", totalKm.coerceAtLeast(0.0))
            put("captured_at", if (location.time > 0L) location.time else now)
            put("sent_at", now)
        }.toString()

        executeRequest(payload) { sending.set(false) }
    }

    fun testConnection(callback: (String) -> Unit) {
        val now = System.currentTimeMillis()

        // A API do Delivery OS valida o mesmo schema da telemetria real.
        // O teste usa valores neutros, mas envia todos os campos obrigatórios.
        val payload = JSONObject().apply {
            put("source", "painel-overlay-test")
            put("latitude", 0.0)
            put("longitude", 0.0)
            put("accuracy_m", 0.0)
            put("speed_kmh", 0.0)
            put("trip_km", Prefs.getTrip(context).coerceAtLeast(0.0))
            put("total_km", Prefs.getTotal(context).coerceAtLeast(0.0))
            put("captured_at", now)
            put("sent_at", now)
        }.toString()

        executor.execute {
            val result = request(payload)
            Prefs.setDeliveryLastResult(context, result)
            callback(result)
        }
    }

    private fun executeRequest(payload: String, done: () -> Unit) = executor.execute {
        try {
            Prefs.setDeliveryLastResult(context, request(payload))
        } finally {
            done()
        }
    }

    private fun request(payload: String): String {
        val endpoint = Prefs.getDeliveryEndpoint(context).trim()
        if (!(endpoint.startsWith("http://") || endpoint.startsWith("https://"))) {
            return "erro: URL inválida"
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = Prefs.getDeliveryMethod(context).uppercase()
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                applyAuthentication(this)
                doOutput = requestMethod in setOf("POST", "PUT", "PATCH")
            }

            if (connection.doOutput) {
                connection.outputStream.use {
                    it.write(payload.toByteArray(Charsets.UTF_8))
                }
            }

            val code = connection.responseCode
            val body = try {
                val stream = if (code in 200..399) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText().take(300) }.orEmpty()
            } catch (_: Exception) {
                ""
            }

            if (body.isNotBlank()) "HTTP " + code + " — " + body else "HTTP " + code
        } catch (e: Exception) {
            "erro: " + (e.message ?: e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    private fun applyAuthentication(connection: HttpURLConnection) {
        val type = Prefs.getDeliveryAuthType(context)
        val credential = Prefs.getDeliveryToken(context).trim()
        if (credential.isBlank() || type == "Nenhuma") return

        when (type) {
            "Bearer Token" ->
                connection.setRequestProperty("Authorization", "Bearer " + credential)
            "API Key" ->
                connection.setRequestProperty(
                    Prefs.getDeliveryHeaderName(context).ifBlank { "X-API-Key" },
                    credential
                )
            "Header personalizado" ->
                connection.setRequestProperty(
                    Prefs.getDeliveryHeaderName(context).ifBlank { "Authorization" },
                    credential
                )
        }
    }

    fun shutdown() = executor.shutdownNow()
}
