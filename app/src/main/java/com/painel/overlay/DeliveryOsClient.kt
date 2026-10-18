package com.painel.overlay

import android.content.Context
import android.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DeliveryOsClient(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val sending = AtomicBoolean(false)
    private val queue = TelemetryQueue(context.applicationContext)
    @Volatile private var lastEnqueueAt = 0L

    fun send(location: Location, speedKmh: Double, tripKm: Double, totalKm: Double) {
        if (!Prefs.isDeliverySyncEnabled(context)) return

        val now = System.currentTimeMillis()
        val interval = Prefs.getDeliveryIntervalSeconds(context) * 1000L
        if (now - lastEnqueueAt < interval) return

        lastEnqueueAt = now
        queue.enqueue(
            TelemetryPoint.fromLocation(
                location = location,
                speedKmh = speedKmh,
                tripKm = tripKm,
                totalKm = totalKm
            )
        )
        flushPending()
    }

    fun flushPending() {
        if (!Prefs.isDeliverySyncEnabled(context)) return
        if (!sending.compareAndSet(false, true)) return

        executor.execute {
            try {
                var sent = 0
                val batch = queue.oldest(100)
                for (point in batch) {
                    val result = request(point.toJson())
                    Prefs.setDeliveryLastResult(
                        context,
                        result.message + " | fila=" + queue.count()
                    )

                    if (result.code in 200..299) {
                        queue.remove(point.id)
                        sent++
                    } else {
                        // Mantém o ponto na fila para novo retry.
                        break
                    }
                }

                if (sent > 0) {
                    Prefs.setDeliveryLastResult(
                        context,
                        "Sincronizados $sent ponto(s) | fila=" + queue.count()
                    )
                }
            } finally {
                sending.set(false)
            }
        }
    }

    fun pendingCount(): Int = queue.count()

    fun testConnection(callback: (String) -> Unit) {
        val now = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("point_id", UUID.randomUUID().toString())
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
            Prefs.setDeliveryLastResult(context, result.message)
            callback(result.message)
        }
    }

    private data class HttpResult(val code: Int, val message: String)

    private fun request(payload: String): HttpResult {
        val endpoint = Prefs.getDeliveryEndpoint(context).trim()
        if (!(endpoint.startsWith("http://") || endpoint.startsWith("https://"))) {
            return HttpResult(-1, "erro: URL inválida")
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

            HttpResult(
                code,
                if (body.isNotBlank()) "HTTP $code — $body" else "HTTP $code"
            )
        } catch (e: Exception) {
            HttpResult(-1, "erro: " + (e.message ?: e.javaClass.simpleName))
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

    fun shutdown() {
        runCatching { queue.close() }
        executor.shutdownNow()
    }
}
