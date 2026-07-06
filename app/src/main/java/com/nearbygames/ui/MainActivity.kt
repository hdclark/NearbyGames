package com.nearbygames.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var nearbyStarted = false

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
    }

    override fun onStart() {
        super.onStart()
        if (allPermissionsGranted()) {
            startNearby()
        } else {
            requestPermissions()
        }
    }

    override fun onStop() {
        super.onStop()
        NearbyConnectionsManager.stop()
        nearbyStarted = false
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
                startNearby()
            } else {
                Toast.makeText(
                    this,
                    "Some permissions were denied.  Nearby features may not work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---- Nearby Connections -----------------------------------------------------------------

    private fun startNearby() {
        if (nearbyStarted) return
        nearbyStarted = true
        NearbyConnectionsManager.start()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 42
    }
}
