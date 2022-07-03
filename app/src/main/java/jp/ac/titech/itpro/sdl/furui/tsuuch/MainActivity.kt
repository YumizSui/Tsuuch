package jp.ac.titech.itpro.sdl.furui.tsuuch

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.ktx.api.net.awaitFindCurrentPlace
import kotlinx.coroutines.launch
import java.util.*


class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var infoView: TextView
    private lateinit var responseView: TextView
    private lateinit var map: GoogleMap
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private lateinit var request: LocationRequest
    private lateinit var callback: LocationCallback
    private lateinit var notificationManager: NotificationManager
    private var ll: LatLng? = null
    private lateinit var pendingIntent: PendingIntent

//    private var stationMemory: MutableList<Place> = mutableListOf()
    private var previouslyNotifiedStation: Place? = null
    private var lastStation: Place? = null
    private var notifyFlag = false
//    private val threshold = 10

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        setContentView(R.layout.activity_main)
        Places.initialize(this, BuildConfig.GOOGLE_MAPS_API_KEY, Locale.JAPANESE)
        placesClient = Places.createClient(this)

        infoView = findViewById(R.id.info_view)
        responseView = findViewById(R.id.current_response_content)

        val fragment =
            supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment?
        if (fragment != null) {
            Log.d(TAG, "onCreate: getMapAsync")
            fragment.getMapAsync(this)
        }
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        request = LocationRequest.create()
        request.interval = 10000L
        request.fastestInterval = 5000L
        request.priority = Priority.PRIORITY_HIGH_ACCURACY
        callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                Log.d(TAG, "onLocationResult")
                val location = locationResult.lastLocation
                ll = LatLng(location!!.latitude, location!!.longitude)
                infoView.text = getString(R.string.latlng_format, ll!!.latitude, ll!!.longitude)
                checkPermissionThenFindCurrentPlace()
                stationNotification()
            }
        }
        val locateButton = findViewById<Button>(R.id.locate_button)
        val placeButton = findViewById<Button>(R.id.place_button)
        locateButton.setOnClickListener {
            map.animateCamera(
                CameraUpdateFactory.newLatLng(
                    ll!!
                )
            )
        }
        createNotificationChannel()
        val intent = Intent(this, MainActivity::class.java)
        pendingIntent =
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        placeButton.setOnClickListener {
            checkPermissionThenFindCurrentPlace()
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
        Log.d(TAG, "onStop")
        super.onStop()
    }

    override fun onMapReady(map: GoogleMap) {
        Log.d(TAG, "onMapReady")
        map.moveCamera(CameraUpdateFactory.zoomTo(15f))
        this.map = map
    }

    private fun checkPermission(reqPermission: Boolean):Boolean {
        for (permission in PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (reqPermission) {
                    ActivityCompat.requestPermissions(this, PERMISSIONS, REQ_PERMISSIONS)
                } else {
                    val text = getString(R.string.toast_requires_permission_format, permission)
                    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                }
                return false
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdate(reqPermission: Boolean) {
        Log.d(TAG, "startLocationUpdate")
        if (checkPermission(reqPermission))
            locationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission")
    private fun checkPermissionThenFindCurrentPlace() {
        if (checkPermission(true))
            findCurrentPlace()
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

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE])
    private fun findCurrentPlace() {

        val placeFields: List<Place.Field> =
            listOf(Place.Field.NAME, Place.Field.ID, Place.Field.ADDRESS, Place.Field.LAT_LNG, Place.Field.TYPES)
        val request: FindCurrentPlaceRequest = FindCurrentPlaceRequest.newInstance(placeFields)
        if (checkPermission(true)) {
            lifecycleScope.launch {
                try {
                    val response = placesClient.awaitFindCurrentPlace(placeFields)
                    val station = response.findStation()

                    notifyFlag = previouslyNotifiedStation?.id != station?.id
                    lastStation = station
                    if (station != null) {
                        responseView.text = response.prettyPrint()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    responseView.text = e.message
                    Log.d(TAG, e.message!!)
                }
            }
        }
    }

    private fun createNotificationChannel()
    {
        val name = "Tsuuch"
        val desc = "現在いる駅を通知する"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelID, name, importance)
        channel.description = desc
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun evalFlag():Boolean {
        val tmp = notifyFlag
        notifyFlag = false
        return tmp
    }
    private fun stationNotification(){
        Log.d(TAG, "stationNotification"+notifyFlag)
        if (evalFlag()) {
            previouslyNotifiedStation = lastStation
            val notification = NotificationCompat.Builder(this, channelID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("電車は\uD83D\uDE8B 駅\uD83D\uDE89の近くです")
                .setContentText("今いるのは"+ lastStation!!.name+"\uD83D\uDE89です")
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .build()
            notificationManager.notify(notificationID, notification)
        }
    }

    companion object {
        private val TAG: String = MainActivity::class.java.getSimpleName()
        private val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        private const val REQ_PERMISSIONS = 1234
        private const val notificationID = 1
        private const val channelID = "channel1"

    }

}


fun stringify(place: Place): String {
    return (place.name
            + " ("
            + place.id
            + ")"
            + " is located at "
            + place.latLng.latitude
            + ", "
            + place.latLng.longitude
            + "\n"
            + place.types)
}

fun FindCurrentPlaceResponse.prettyPrint(): String {

    val FIELD_SEPARATOR = "\n\t"
    val RESULT_SEPARATOR = "\n---\n\t"
    val response = this
    val builder = StringBuilder()
    var train_place: Place? = null
    builder.append(response.placeLikelihoods.size).append(" Current Place Results:")
    for (placeLikelihood in response.placeLikelihoods) {
        if(placeLikelihood.place.types.contains(Place.Type.TRAIN_STATION)) {
            train_place = placeLikelihood.place
            builder.append(stringify(placeLikelihood.place))
            break
        }
    }
    return builder.toString()
}


fun FindCurrentPlaceResponse.findStation(): Place? {
    for (placeLikelihood in this.placeLikelihoods) {
        if(placeLikelihood.place.types.contains(Place.Type.TRAIN_STATION)) {
            return placeLikelihood.place
        }
    }
    return null
}