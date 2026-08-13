package com.example.vpnapp

import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.StringReader

class MainActivity : AppCompatActivity() {

    private lateinit var backend: Backend
    private lateinit var tunnel: SimpleTunnel
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button

    private var isConnected = false

    private val SERVER_CONFIG = """
        [Interface]
        PrivateKey = <YOUR_DEVICE_PRIVATE_KEY>
        Address = 10.0.0.2/32
        DNS = 1.1.1.1

        [Peer]
        PublicKey = <YOUR_SERVER_PUBLIC_KEY>
        Endpoint = <YOUR_SERVER_IP_OR_HOST>:51820
        AllowedIPs = 0.0.0.0/0, ::/0
    """.trimIndent()

    private val vpnPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                doConnect()
            } else {
                statusText.text = getString(R.string.status_disconnected)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.connectButton)

        backend = GoBackend(applicationContext)
        tunnel = SimpleTunnel("main") { state ->
            runOnUiThread { onTunnelStateChanged(state) }
        }

        connectButton.setOnClickListener {
            if (isConnected) disconnect() else requestPermissionThenConnect()
        }

        setupAdBanner()
    }

    private fun setupAdBanner() {
        MobileAds.initialize(this)
        val adView = AdView(this).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = getString(R.string.admob_banner_ad_unit_id)
        }
        findViewById<FrameLayout>(R.id.adContainer).addView(adView)
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun requestPermissionThenConnect() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            doConnect()
        }
    }

    private fun doConnect() {
        statusText.text = getString(R.string.status_connecting)
        Thread {
            try {
                val config = Config.parse(StringReader(SERVER_CONFIG))
                backend.setState(tunnel, Tunnel.State.UP, config)
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "خطأ: ${e.message}"
                }
            }
        }.start()
    }

    private fun disconnect() {
        Thread {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun onTunnelStateChanged(state: Tunnel.State) {
        isConnected = state == Tunnel.State.UP
        statusText.text = if (isConnected) getString(R.string.status_connected) else getString(R.string.status_disconnected)
        connectButton.text = if (isConnected) getString(R.string.btn_disconnect) else getString(R.string.btn_connect)
    }

    private class SimpleTunnel(
        private val tunnelName: String,
        private val onStateChange: (Tunnel.State) -> Unit
    ) : Tunnel {
        override fun getName(): String = tunnelName
        override fun onStateChange(newState: Tunnel.State) = onStateChange.invoke(newState)
    }
}
