package com.nearbygames.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.nearbygames.R
import com.nearbygames.databinding.ActivityMainBinding
import com.nearbygames.nearby.NearbyConnectionsManager
import com.nearbygames.utils.DeviceIdManager

class MainActivity : AppCompatActivity(), NearbyConnectionsManager.ConnectionStateListener,
    NearbyConnectionsManager.RadioStateListener {

    private lateinit var binding: ActivityMainBinding

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    // ---- Permission list (varies by API level) ----------------------------------------------

    @Suppress("DEPRECATION")
    private fun buildRequiredPermissions(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // BLUETOOTH / BLUETOOTH_ADMIN are deprecated on API 31+ but required below
            perms += listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return perms.toTypedArray()
    }

    // ---- Lifecycle --------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up Navigation
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigationView.setupWithNavController(navController)

        // Initialise the Nearby singleton once
        NearbyConnectionsManager.init(this)

        Log.d(TAG, "Device ID: ${DeviceIdManager.getDeviceId(this)}")
        Log.d(TAG, "Device name: ${DeviceIdManager.getDeviceName(this)}")

        binding.btnAdvertise.setOnClickListener { onAdvertiseClicked() }
        binding.btnDiscover.setOnClickListener { onDiscoverClicked() }
        binding.btnDisconnect.setOnClickListener { onDisconnectClicked() }

        updateStatusText()
    }

    override fun onStart() {
        super.onStart()
        NearbyConnectionsManager.addConnectionListener(this)
        NearbyConnectionsManager.addRadioStateListener(this)
        if (!allPermissionsGranted()) {
            requestPermissions()
        }
        updateStatusText()
    }

    override fun onStop() {
        super.onStop()
        NearbyConnectionsManager.removeConnectionListener(this)
        NearbyConnectionsManager.removeRadioStateListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelCountdown()
        // Closing the app disconnects everything and stops advertising/discovery.
        NearbyConnectionsManager.disconnectAll()
    }

    // ---- Permission handling ----------------------------------------------------------------

    private fun allPermissionsGranted(): Boolean {
        return buildRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val missing = buildRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                Toast.makeText(this, "All permissions granted.", Toast.LENGTH_SHORT).show()
                pendingAction?.let { it() }
            } else {
                Toast.makeText(
                    this,
                    "Some permissions were denied.  Nearby features may not work.",
                    Toast.LENGTH_LONG
                ).show()
            }
            pendingAction = null
        }
    }

    /** Action to run once permissions are granted, if the user tapped a button before granting. */
    private var pendingAction: (() -> Unit)? = null

    /** Runs [action] immediately if permissions are already granted, otherwise requests them first. */
    private fun runWithPermissions(action: () -> Unit) {
        if (allPermissionsGranted()) {
            action()
        } else {
            pendingAction = action
            requestPermissions()
        }
    }

    // ---- Nearby Connections -----------------------------------------------------------------

    private fun onAdvertiseClicked() {
        runWithPermissions {
            NearbyConnectionsManager.advertiseFor(ADVERTISE_DURATION_MS)
            startCountdown(ADVERTISE_DURATION_MS, "Advertising")
        }
    }

    private fun onDiscoverClicked() {
        runWithPermissions {
            NearbyConnectionsManager.discoverFor(DISCOVER_DURATION_MS)
            startCountdown(DISCOVER_DURATION_MS, "Discovering")
        }
    }

    private fun onDisconnectClicked() {
        cancelCountdown()
        NearbyConnectionsManager.disconnectAll()
        updateStatusText()
    }

    private fun startCountdown(durationMs: Long, label: String) {
        cancelCountdown()
        val endAt = System.currentTimeMillis() + durationMs
        val tick = object : Runnable {
            override fun run() {
                val remainingMs = endAt - System.currentTimeMillis()
                if (remainingMs <= 0) {
                    countdownRunnable = null
                    updateStatusText()
                    return
                }
                val remainingSec = (remainingMs + 999) / 1000
                binding.tvGlobalStatus.text = "$label… ${remainingSec}s remaining"
                countdownRunnable = this
                countdownHandler.postDelayed(this, 500)
            }
        }
        countdownRunnable = tick
        countdownHandler.post(tick)
    }

    private fun cancelCountdown() {
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        countdownRunnable = null
    }

    private fun updateStatusText() {
        val count = NearbyConnectionsManager.getConnectedEndpoints().size
        binding.tvGlobalStatus.text = when {
            count > 0 -> "$count device(s) connected"
            NearbyConnectionsManager.isAdvertising() -> "Advertising…"
            NearbyConnectionsManager.isDiscovering() -> "Discovering…"
            else -> "Not connected"
        }
    }

    // ---- NearbyConnectionsManager.ConnectionStateListener ------------------------------------

    override fun onConnected(endpointId: String, endpointName: String) {
        runOnUiThread {
            cancelCountdown()
            updateStatusText()
        }
    }

    override fun onDisconnected(endpointId: String) {
        runOnUiThread { updateStatusText() }
    }

    // ---- NearbyConnectionsManager.RadioStateListener ----------------------------------------

    override fun onAdvertisingStateChanged(isAdvertising: Boolean) {
        if (!isAdvertising) runOnUiThread { updateStatusText() }
    }

    override fun onDiscoveryStateChanged(isDiscovering: Boolean) {
        if (!isDiscovering) runOnUiThread { updateStatusText() }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 42
        private const val ADVERTISE_DURATION_MS = 60_000L
        private const val DISCOVER_DURATION_MS = 20_000L
    }
}
