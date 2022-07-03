package jp.ac.titech.itpro.sdl.furui.tsuuch

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import java.util.*


class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private val TAG: String = this::class.java.simpleName
        private val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
        const val channelID = "channel1"
        const val REQ_PERMISSIONS = 1234
    }
    private lateinit var infoView: TextView
    private lateinit var responseView: TextView
    private lateinit var map: GoogleMap
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var request: LocationRequest
    private lateinit var callback: LocationCallback
    private lateinit var openIntent: PendingIntent
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationManagerService: NotificationManager
    private var ll: LatLng? = null


    private var isOnService = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        setContentView(R.layout.activity_main)
        Places.initialize(this, BuildConfig.GOOGLE_MAPS_API_KEY, Locale.JAPANESE)
        infoView = findViewById(R.id.info_view)
        responseView = findViewById(R.id.current_response_content)

        val fragment =
            supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment?
        if (fragment != null) {
            Log.d(TAG, "onCreate: getMapAsync")
            fragment.getMapAsync(this)
        }
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        request = LocationRequest.create().apply {
            interval = 10000L
            fastestInterval = 5000L
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
        callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!isOnService) {
                    Log.d(TAG, "onLocationResult")

                    locationResult.lastLocation?.let{
                        ll = LatLng(it.latitude, it.longitude)
                    }
                    infoView.text = getString(R.string.latlng_format, ll!!.latitude, ll!!.longitude)
                }
            }
        }
        openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val foreButton = findViewById<Button>(R.id.fore_button)
        foreButton.setOnClickListener {
            val intent = Intent(this, LocationService::class.java)
            if (!isOnService){
                if(checkPermission(true)){
                    isOnService=true
                    startForegroundService(intent)
                }
            } else {
                isOnService=false
                stopService(intent)
            }
        }
    }


    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        startLocationUpdate(true)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        stopLocationUpdate()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    override fun onMapReady(map: GoogleMap) {
        Log.d(TAG, "onMapReady")
        map.moveCamera(CameraUpdateFactory.zoomTo(15f))
        this.map = map
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        if(isOnService){
            val intent = Intent(this, LocationService::class.java)
            stopService(intent)
        }
        super.onDestroy()
    }
    @SuppressLint("MissingPermission")
    private fun startLocationUpdate(reqPermission: Boolean) {
        Log.d(TAG, "startLocationUpdate")
        if (checkPermission(reqPermission))
            locationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }


    override fun onRequestPermissionsResult(
        reqCode: Int,
        permissions: Array<String>,
        grants: IntArray
    ) {
        Log.d(TAG, "onRequestPermissionsResult")
        if (reqCode == REQ_PERMISSIONS) {
            startLocationUpdate(false)
        } else {
            super.onRequestPermissionsResult(reqCode, permissions, grants)
        }
    }

    private fun stopLocationUpdate() {
        Log.d(TAG, "stopLocationUpdate")
        locationClient.removeLocationUpdates(callback)
    }

    private fun checkPermission(reqPermission: Boolean):Boolean {
        for (permission in arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (reqPermission) {
                    ActivityCompat.requestPermissions(this,
                        PERMISSIONS,
                        REQ_PERMISSIONS
                    )
                } else {
                    val text = getString(R.string.toast_requires_permission_format, permission)
                    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                }
                return false
            }
        }
        if (ActivityCompat
                .checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            PackageManager.PERMISSION_GRANTED){
            if(reqPermission){
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    REQ_PERMISSIONS
                )
            }else {
                val text = getString(R.string.toast_requires_permission_format, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
            }
            return false

        }
        return true
    }

    private fun createNotificationChannel() {
        val name = "Tsuuch"
        val desc = "現在いる駅を通知する"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelID, name, importance)
        channel.description = desc
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotificationChannelLocationService() {
        val name = "Tsuuch Service"
        val desc = "Foreground Service"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(LocationService.channelID, name, importance)
        channel.description = desc
        notificationManagerService = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManagerService.createNotificationChannel(channel)
    }



}
