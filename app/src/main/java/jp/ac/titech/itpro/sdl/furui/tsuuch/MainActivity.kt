package jp.ac.titech.itpro.sdl.furui.tsuuch

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import com.google.codelabs.maps.placesdemo.StringUtil
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var infoView: TextView
    private lateinit var responseView: TextView
    private lateinit var map: GoogleMap
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private lateinit var request: LocationRequest
    private lateinit var callback: LocationCallback
    private var ll: LatLng? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        setContentView(R.layout.activity_main)
        Places.initialize(this, BuildConfig.GOOGLE_MAPS_API_KEY)
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

    companion object {
        private val TAG: String = MainActivity::class.java.getSimpleName()
        private val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        private const val REQ_PERMISSIONS = 1234
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE])
    private fun findCurrentPlace() {
        val placeFields: List<Place.Field> =
            listOf(Place.Field.NAME, Place.Field.ID, Place.Field.ADDRESS, Place.Field.LAT_LNG)
        val request: FindCurrentPlaceRequest = FindCurrentPlaceRequest.newInstance(placeFields)
        if (checkPermission(true)) {
            lifecycleScope.launch {
                try {
                    val response = placesClient.awaitFindCurrentPlace(placeFields)
                    responseView.text = response.prettyPrint()
                    Log.d(TAG, "okdoke")
                    Log.d(TAG, response.prettyPrint())
                    // Enable scrolling on the long list of likely places
                    val movementMethod = ScrollingMovementMethod()
                    responseView.movementMethod = movementMethod
                } catch (e: Exception) {
                    e.printStackTrace()
                    responseView.text = e.message
                    Log.d(TAG, "damedoke")
                    Log.d(TAG, e.message!!)
                }
            }
        }
    }

}
fun FindCurrentPlaceResponse.prettyPrint(): String {
    return StringUtil.stringify(this, false)
}