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
                routeId = Prefs.getActiveRouteId(context),
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
                    val result = request(Prefs.getDeliveryEndpoint(context), point.toJson())
                    Prefs.setDeliveryLastResult(
                        context,
                        result.message + " | fila=" + queue.count()
                    )

                    if (result.code in 200..299) {
                        queue.remove(point.id)
                        sent++
                    } else {
                        break
                    }
                }

                if (queue.count() == 0) {
                    flushPendingRouteFinish()
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

    private fun flushPendingRouteFinish() {
        val pending = Prefs.getPendingRouteFinish(context) ?: return
        val payload = JSONObject().apply {
            put("route_id", pending.routeId)
            put("ended_at", pending.endedAt)
            put("end_total_km", pending.totalKm)
            put("distance_km", pending.distanceKm)
        }.toString()

        val endpoint = Prefs.getDeliveryEndpoint(context).trimEnd('/') + "/finish"
        val result = request(endpoint, payload)

        if (result.code in 200..299) {
            Prefs.clearPendingRouteFinish(context)
            Prefs.setDeliveryLastResult(context, "Jornada finalizada e sincronizada")
        } else {
            Prefs.setDeliveryLastResult(context, "Fim da jornada pendente — " + result.message)
        }
    }

    fun pendingCount(): Int = queue.count()

    fun testConnection(callback: (String) -> Unit) {
        val now = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("point_id", UUID.randomUUID().toString())
            Prefs.getActiveRouteId(context)?.let { put("route_id", it) }
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
            val result = request(Prefs.getDeliveryEndpoint(context), payload)
            Prefs.setDeliveryLastResult(context, result.message)
            callback(result.message)
        }
    }

    private data class HttpResult(val code: Int, val message: String)

    private fun request(endpointRaw: String, payload: String): HttpResult {
        val endpoint = endpointRaw.trim()
        if (!(endpoint.startsWith("http://") || endpoint.startsWith("https://"))) {
            return HttpResult(-1, "erro: URL inválida")
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                applyAuthentication(this)
                doOutput = true
            }

            connection.outputStream.use {
                it.write(payload.toByteArray(Charsets.UTF_8))
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
