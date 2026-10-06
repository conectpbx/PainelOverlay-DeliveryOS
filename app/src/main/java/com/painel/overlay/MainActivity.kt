package com.painel.overlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var txtApiUrl: TextView
    private lateinit var editTotal: EditText
    private lateinit var editDeliveryEndpoint: EditText
    private lateinit var editDeliveryToken: EditText
    private lateinit var editDeliveryHeader: EditText
    private lateinit var editDeliveryInterval: EditText
    private lateinit var spinnerDeliveryMethod: Spinner
    private lateinit var spinnerDeliveryAuth: Spinner
    private lateinit var switchDeliverySync: SwitchCompat
    private lateinit var txtDeliveryStatus: TextView

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val msg = if (granted) {
            "Permissão de localização concedida"
        } else {
            "Sem localização o app não funciona"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        txtApiUrl = findViewById(R.id.txtApiUrl)
        editTotal = findViewById(R.id.editTotal)
        editDeliveryEndpoint = findViewById(R.id.editDeliveryEndpoint)
        editDeliveryToken = findViewById(R.id.editDeliveryToken)
        editDeliveryHeader = findViewById(R.id.editDeliveryHeader)
        editDeliveryInterval = findViewById(R.id.editDeliveryInterval)
        spinnerDeliveryMethod = findViewById(R.id.spinnerDeliveryMethod)
        spinnerDeliveryAuth = findViewById(R.id.spinnerDeliveryAuth)
        switchDeliverySync = findViewById(R.id.switchDeliverySync)
        txtDeliveryStatus = findViewById(R.id.txtDeliveryStatus)

        val methods = arrayOf("POST", "PUT", "PATCH")
        val authTypes = arrayOf("Bearer Token", "API Key", "Header personalizado", "Nenhuma")

        spinnerDeliveryMethod.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, methods)
        spinnerDeliveryAuth.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, authTypes)

        spinnerDeliveryMethod.setSelection(
            methods.indexOf(Prefs.getDeliveryMethod(this)).coerceAtLeast(0)
        )
        spinnerDeliveryAuth.setSelection(
            authTypes.indexOf(Prefs.getDeliveryAuthType(this)).coerceAtLeast(0)
        )

        editDeliveryEndpoint.setText(Prefs.getDeliveryEndpoint(this))
        editDeliveryToken.setText(Prefs.getDeliveryToken(this))
        editDeliveryHeader.setText(Prefs.getDeliveryHeaderName(this))
        editDeliveryInterval.setText(Prefs.getDeliveryIntervalSeconds(this).toString())
        switchDeliverySync.isChecked = Prefs.isDeliverySyncEnabled(this)

        findViewById<Button>(R.id.btnLocationPerm).setOnClickListener {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        findViewById<Button>(R.id.btnOverlayPerm).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)
            )
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnSaveTotal).setOnClickListener {
            val value = editTotal.text.toString().replace(",", ".").toDoubleOrNull()
            if (value != null && value >= 0) {
                Prefs.setTotal(this, value)
                Toast.makeText(this, "Odômetro total ajustado", Toast.LENGTH_SHORT).show()
                refreshStatus()
            } else {
                Toast.makeText(this, "Digite um número válido", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnSaveDelivery).setOnClickListener {
            val endpoint = editDeliveryEndpoint.text.toString().trim()
            val enabled = switchDeliverySync.isChecked

            if (enabled && !(endpoint.startsWith("http://") || endpoint.startsWith("https://"))) {
                Toast.makeText(
                    this,
                    "Informe uma URL HTTP/HTTPS válida",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val interval = editDeliveryInterval.text
                    .toString()
                    .toIntOrNull()
                    ?.coerceIn(2, 3600)
                    ?: 5

                saveDeliveryPreferences(endpoint, interval, enabled)
                Toast.makeText(
                    this,
                    "Integração Delivery OS salva",
                    Toast.LENGTH_SHORT
                ).show()
                refreshStatus()
            }
        }

        findViewById<Button>(R.id.btnTestDelivery).setOnClickListener {
            val endpoint = editDeliveryEndpoint.text.toString().trim()

            if (!(endpoint.startsWith("http://") || endpoint.startsWith("https://"))) {
                Toast.makeText(
                    this,
                    "Informe e salve uma URL válida primeiro",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val interval = editDeliveryInterval.text.toString().toIntOrNull() ?: 5
                saveDeliveryPreferences(endpoint, interval, switchDeliverySync.isChecked)

                txtDeliveryStatus.text = "Testando conexão..."
                val client = DeliveryOsClient(applicationContext)
                client.testConnection { result ->
                    runOnUiThread {
                        txtDeliveryStatus.text = "Teste: " + result
                        client.shutdown()
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener { startBubble() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopBubble() }
    }

    private fun saveDeliveryPreferences(endpoint: String, interval: Int, enabled: Boolean) {
        Prefs.setDeliveryEndpoint(this, endpoint)
        Prefs.setDeliveryToken(this, editDeliveryToken.text.toString().trim())
        Prefs.setDeliveryMethod(this, spinnerDeliveryMethod.selectedItem.toString())
        Prefs.setDeliveryAuthType(this, spinnerDeliveryAuth.selectedItem.toString())
        Prefs.setDeliveryHeaderName(this, editDeliveryHeader.text.toString().trim())
        Prefs.setDeliveryIntervalSeconds(this, interval)
        Prefs.setDeliverySyncEnabled(this, enabled)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun startBubble() {
        if (!hasLocationPermission()) {
            Toast.makeText(
                this,
                "Permita a localização primeiro (passo 1)",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Permita exibir sobre outros apps primeiro (passo 2)",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Toast.makeText(
            this,
            "Bolha iniciada — toque nela para expandir",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun stopBubble() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(intent)
    }

    private fun refreshStatus() {
        val locOk = if (hasLocationPermission()) "OK" else "pendente"
        val overlayOk = if (Settings.canDrawOverlays(this)) "OK" else "pendente"

        txtStatus.text =
            "Localização: " + locOk +
            "\nSobrepor apps: " + overlayOk +
            "\nOdômetro total salvo: " + "%.2f km".format(Prefs.getTotal(this))

        editTotal.hint = "%.1f".format(Prefs.getTotal(this))
        txtDeliveryStatus.text =
            "Último envio: " + Prefs.getDeliveryLastResult(this)

        val ip = getLocalIpAddress()
        txtApiUrl.text = if (ip != null) {
            "http://" + ip + ":" + OverlayService.API_PORT
        } else {
            "conecte numa Wi-Fi para ver o endereço"
        }
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val wifiManager =
                applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress

            if (ipInt == 0) {
                null
            } else {
                String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
