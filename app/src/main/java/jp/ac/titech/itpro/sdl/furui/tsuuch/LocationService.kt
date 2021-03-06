package jp.ac.titech.itpro.sdl.furui.tsuuch

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.location.Location

import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.ktx.api.net.awaitFindCurrentPlace
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class LocationService : Service() {
    companion object {
        private val TAG: String = this::class.java.simpleName
        private const val notificationID = 1
        private const val notificationIDService = 2
        const val channelID = "channel2"
        const val stopVelocity = 4.0
        const val moveVelocity = 10.0
    }

    private lateinit var request: LocationRequest
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var callback: LocationCallback
    private lateinit var placesClient: PlacesClient
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationManagerService: NotificationManager

    private var previouslyNotifiedStation: Place? = null
    private var lastStation: Place? = null
    private var notifyFlag = false
    private var prvVelocity = 0.0
    private var prvTime = System.currentTimeMillis()
    private var prvLatLng: LatLng? = null
    private var nowStopping = false


    override fun onCreate() {
        super.onCreate()
        Places.initialize(this, BuildConfig.GOOGLE_MAPS_API_KEY, Locale.JAPANESE)

        placesClient = Places.createClient(this)

        request = LocationRequest.create().apply {
            interval = 5000L
            fastestInterval = 5000L
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
        createNotificationChannel()
        createNotificationChannelLocationService()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        callback = object : LocationCallback() {
            @SuppressLint("MissingPermission")
            override fun onLocationResult(locationResult: LocationResult) {

                locationResult.lastLocation?.let{
                    Log.d(TAG, "onLocationResult: ${it.latitude} , ${it.longitude}")
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    val currentTime = System.currentTimeMillis()
                    prvLatLng?.let {
                        val distance = SphericalUtil.computeDistanceBetween(currentLatLng, prvLatLng)
                        val diffTime = currentTime-prvTime
                        val velocity = distance/diffTime*1000
                        Log.d(TAG, "distance, velocity diffTime: $distance $velocity $diffTime")
                        if (!nowStopping && velocity < stopVelocity) {
                            Log.d(TAG, "now stopping")
                            nowStopping = true
                            findCurrentPlace()
                        }
                        if(nowStopping && velocity > moveVelocity) {
                            Log.d(TAG, "not stopping")
                            nowStopping=false
                        }
                        prvVelocity=velocity

                    }
                    prvTime=currentTime
                    prvLatLng = currentLatLng
                }
            }
        }


        val openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 3333, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(this, channelID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("????????????\uD83D\uDDFE")
            .setContentText("????????????????????????\uD83D\uDDFE")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent)
            .build()

        startForeground(notificationIDService, notification)

        startLocationUpdate()

        return START_STICKY
    }


    override fun onBind(p0: Intent?): IBinder? {
        Log.d(TAG, "onBind")
        return null
    }

    override fun stopService(name: Intent?): Boolean {
        Log.d(TAG, "stopService")
        stopLocationUpdate()
        return super.stopService(name)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        stopLocationUpdate()
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdate() {
        Log.d(TAG, "startLocationUpdate")
        fusedLocationClient.requestLocationUpdates(request, callback,null)
    }

    private fun stopLocationUpdate() {
        Log.d(TAG, "stopLocationUpdate")
        fusedLocationClient.removeLocationUpdates(callback)
    }


    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE])
    fun findCurrentPlace() {

        val placeFields: List<Place.Field> =
            listOf(Place.Field.NAME, Place.Field.ID, Place.Field.ADDRESS, Place.Field.LAT_LNG, Place.Field.TYPES)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Log.d(TAG,"place client called")
                val response = placesClient.awaitFindCurrentPlace(placeFields)
                val station = response.findStation()

                notifyFlag = station != null && (previouslyNotifiedStation?.id != station.id)

                lastStation = station
                stationNotification()
                if (station != null) {
                    Log.d(TAG, stringify(station))
                } else {
                    Log.d(TAG, "No station")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d(TAG, e.message!!)
            }
        }
    }

    private fun evalFlag():Boolean {
        val tmp = notifyFlag
        notifyFlag = false
        return tmp
    }



    private fun createNotificationChannel() {
        val name = "Tsuuch"
        val desc = "??????????????????????????????"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(MainActivity.channelID, name, importance)
        channel.description = desc
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotificationChannelLocationService() {
        val name = "Tsuuch Service"
        val desc = "Foreground Service"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelID, name, importance)
        channel.description = desc
        notificationManagerService = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManagerService.createNotificationChannel(channel)
    }

    fun stationNotification(){
        Log.d(TAG, "stationNotification: $notifyFlag")
        if (evalFlag()) {
            previouslyNotifiedStation = lastStation

            val openIntent = Intent(this, MainActivity::class.java).let {
                PendingIntent.getActivity(this, 3334, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            val notification = NotificationCompat.Builder(this, MainActivity.channelID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("??????\uD83D\uDE8B??????\uD83D\uDE89???????????????")
                .setContentText("???????????????"+ lastStation!!.name+"\uD83D\uDE89??????")
                .setContentIntent(openIntent)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            notificationManager.notify(notificationID, notification)
        }
    }

}
fun stringify(place: Place): String {
    return (place.name!!
            + " ("
            + place.id!!
            + ")"
            + " is located at "
            + place.latLng!!.latitude
            + ", "
            + place.latLng!!.longitude
            + "\n"
            + place.types)
}

fun FindCurrentPlaceResponse.findStation(): Place? {
    for (placeLikelihood in this.placeLikelihoods) {
        if(placeLikelihood.place.types!!.contains(Place.Type.TRAIN_STATION)) {
            return placeLikelihood.place
        }
    }
    return null
}
