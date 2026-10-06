package com.painel.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.hypot

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var locationManager: LocationManager

    private var bubbleView: View? = null
    private var expandedView: View? = null
    private var isExpanded = false

    private var lastLocation: Location? = null
    private var speedKmh = 0.0
    private var apiServer: ApiServer? = null
    private lateinit var deliveryOsClient: DeliveryOsClient

    companion object {
        const val CHANNEL_ID = "painel_channel"
        const val NOTIF_ID = 1
        const val ACTION_STOP = "com.painel.overlay.STOP"
        const val API_PORT = 8080
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        deliveryOsClient = DeliveryOsClient(applicationContext)

        addBubble()
        startLocationUpdates()
        startApiServer()
    }

    private fun startApiServer() {
        apiServer = try {
            ApiServer(applicationContext, API_PORT).also {
                it.start(30_000, false)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Painel ativo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém o velocímetro e odômetro rodando"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Painel ativo")
            .setContentText("Velocímetro e odômetro rodando em segundo plano")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(0, "Parar", stopPending)
            .build()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun addBubble() {
        bubbleView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_bubble, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var downTime = 0L
        val dragThresholdPx = 16

        bubbleView!!.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downTime = System.currentTimeMillis()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x =
                        initialX + (event.rawX - initialTouchX).toInt()
                    params.y =
                        initialY + (event.rawY - initialTouchY).toInt()

                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val moved = hypot(
                        (event.rawX - initialTouchX).toDouble(),
                        (event.rawY - initialTouchY).toDouble()
                    )

                    val elapsed =
                        System.currentTimeMillis() - downTime

                    if (moved < dragThresholdPx && elapsed < 300) {
                        toggleExpanded()
                    }
                    true
                }

                else -> false
            }
        }

        windowManager.addView(bubbleView, params)
    }

    private fun toggleExpanded() {
        if (isExpanded) {
            removeExpanded()
        } else {
            showExpanded()
        }
    }

    private fun showExpanded() {
        bubbleView?.visibility = View.GONE

        expandedView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_expanded, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        expandedView!!
            .findViewById<View>(R.id.btnCollapse)
            .setOnClickListener { removeExpanded() }

        expandedView!!
            .findViewById<View>(R.id.btnResetTrip)
            .setOnClickListener {
                Prefs.resetTrip(this)
                updateExpandedTexts()
            }

        expandedView!!
            .findViewById<View>(R.id.btnStopService)
            .setOnClickListener { stopSelf() }

        windowManager.addView(expandedView, params)
        isExpanded = true
        updateExpandedTexts()
    }

    private fun removeExpanded() {
        expandedView?.let {
            runCatching { windowManager.removeView(it) }
        }
        expandedView = null
        isExpanded = false
        bubbleView?.visibility = View.VISIBLE
    }

    private fun updateExpandedTexts() {
        expandedView
            ?.findViewById<TextView>(R.id.txtSpeedExpanded)
            ?.text = "%.0f".format(speedKmh)

        expandedView
            ?.findViewById<TextView>(R.id.txtTrip)
            ?.text = "%.2f km".format(Prefs.getTrip(this))

        expandedView
            ?.findViewById<TextView>(R.id.txtTotal)
            ?.text = "%.2f km".format(Prefs.getTotal(this))
    }

    private fun updateBubbleText() {
        bubbleView
            ?.findViewById<TextView>(R.id.txtSpeedBubble)
            ?.text = "%.0f".format(speedKmh)
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val previous = lastLocation
            var kmh =
                if (location.hasSpeed()) location.speed * 3.6 else 0.0

            if (previous != null) {
                val dt =
                    (location.time - previous.time) / 1000.0

                val distanceMeters = GeoUtils.distanceMeters(
                    previous.latitude,
                    previous.longitude,
                    location.latitude,
                    location.longitude
                )

                if (!location.hasSpeed() && dt > 0) {
                    kmh = (distanceMeters / dt) * 3.6
                }

                val goodFix = location.accuracy <= 25f
                val movedEnough = distanceMeters >= 3.0

                if (goodFix &&
                    movedEnough &&
                    dt in 0.001..15.0
                ) {
                    Prefs.addDistanceKm(
                        this@OverlayService,
                        distanceMeters / 1000.0
                    )
                }
            }

            lastLocation = location
            speedKmh = kmh.coerceAtLeast(0.0)

            updateBubbleText()
            if (isExpanded) {
                updateExpandedTexts()
            }

            apiServer?.apply {
                speedKmh = this@OverlayService.speedKmh
                accuracyM = location.accuracy.toDouble()
                tripKm =
                    Prefs.getTrip(this@OverlayService)
                totalKm =
                    Prefs.getTotal(this@OverlayService)
                lastUpdateMs =
                    System.currentTimeMillis()
            }

            deliveryOsClient.send(
                location = location,
                speedKmh = speedKmh,
                tripKm = Prefs.getTrip(this@OverlayService),
                totalKm = Prefs.getTotal(this@OverlayService)
            )
        }

        @Deprecated(
            "Deprecated in Java, mantido por compatibilidade com versões antigas do Android"
        )
        override fun onStatusChanged(
            provider: String?,
            status: Int,
            extras: Bundle?
        ) {
        }

        override fun onProviderEnabled(provider: String) {
        }

        override fun onProviderDisabled(provider: String) {
        }
    }

    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                locationListener
            )
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        runCatching {
            locationManager.removeUpdates(locationListener)
        }

        runCatching {
            apiServer?.stop()
        }

        if (::deliveryOsClient.isInitialized) {
            runCatching {
                deliveryOsClient.shutdown()
            }
        }

        bubbleView?.let {
            runCatching {
                windowManager.removeView(it)
            }
        }

        expandedView?.let {
            runCatching {
                windowManager.removeView(it)
            }
        }
    }
}
