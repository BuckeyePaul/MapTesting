package com.example.maptesting

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Mapbox Navigation entry point. There should only be one instance of this object for the app.
 * You can use [MapboxNavigationProvider] to help create and obtain that instance.
 */
private lateinit var mapboxNavigation: MapboxNavigation

private val locationObserver = object : LocationObserver {
    /**
     * Invoked as soon as the [Location] is available.
     */
    override fun onNewRawLocation(rawLocation: Location) {

    }

    //When new location data comes in this is where it is handled
    @SuppressLint("SetTextI18n")
    override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
        //Variable for the speed limit in MPH
        var speedLimitMPH = lastSpeedLimit
        //Get current time in milliseconds
        val currentTime = System.currentTimeMillis()
        //Get speed limit of road if possible in KPH
        val speedLimitKPH = locationMatcherResult.speedLimit?.speedKmph
        if (speedLimitKPH != null) {
            //Convert speed limit to MPH if possible
            speedLimitMPH = (speedLimitKPH.times(0.621371)).roundToInt()
        }
        //Get speed that should not be exceeded
        val speedingLimit = speedLimitMPH + 5
        //Get current speed from GPS
        val currentSpeed = locationMatcherResult.enhancedLocation.speed*2.23694
        //Floor speed to nearest multiple of 5
        val roundSpeed = floor(currentSpeed/5).times(5).toInt()
        //Get difference in time between now and last reading
        val timeChange = currentTime - locationLastUpdate
        //Get change in speed from current to last reading
        val speedChange = currentSpeed - lastSpeed
        //Calculate acceleration based on change in speed over change in time
        val calcAcceleration = speedChange/timeChange

        //If the accelerometer has spiked with an acceleration above 8 MPH/s
        if (accSpike) {
            //See if speed has reduced by at least 4 MPH
            //Some leeway given here to reflect fact that location updates are not continuous
            if (speedChange < -4) {
                //If speed has reduced by at least 4 MPH then a hard stop has occurred
                hardStops++
            //Otherwise see if speed has increased by at least 4 MPH
            } else if (speedChange > 4) {
                //If speed has increased by at least 4 MPH then a rapid acceleration has occurred
                rapidAcc++
            }
            //Set flag for accelerometer spiking to false
            accSpike = false
        }

        //If calculated Acceleration is less than -8 MPH/s a hard stop has occurred
        if (calcAcceleration <= -8) {
            hardStops++
        //If calculated acceleration is greater than 8 MPH/s a rapid acceleration has occurred
        } else if (calcAcceleration >= 8) {
            rapidAcc++
        }

        //If current speed is over the speeding threshold
        if (currentSpeed > speedingLimit) {
            //Add time between checks of speed to speeding timer
            timeSpeeding += timeChange

        }

        //If rounded speed is above previous max speed
        if (roundSpeed > maxSpeed) {
            //Set new max speed
            maxSpeed = roundSpeed
            //Set time at max speed to time between checks
            maxSpeedTime = timeChange
        } else if (roundSpeed == maxSpeed) {
            //Increment time at max speed to time between checks
            maxSpeedTime += timeChange
        }

        //Set text view with information for debugging
        textView.text = speedLimitMPH.toString() + "\n" + currentSpeed.toString() + "\n" +
                (currentTime - locationLastUpdate).toString() + "\n"

        if(speedLimitMPH > 5) {
            speedLimDisp.text = "$speedLimitMPH mph"
        } else {
            speedLimDisp.text = "Not found"
        }

        currSpeedDisp.text = "$currentSpeed mph"
        if (currentSpeed > speedingLimit) {
            currSpeedDisp.setTextColor(Color.parseColor("#FF0000"))
        } else {
            currSpeedDisp.setTextColor(Color.parseColor("#000000"))
        }

        speedingDisp.text = timeSpeeding.toString()
        stopDisp.text = hardStops.toString()
        accelDisp.text = rapidAcc.toString()

        //Update last update time to the time most recently recorded
        locationLastUpdate = currentTime
        //Update last speed to the speed most recently recorded
        lastSpeed = currentSpeed
        //Update last speed limit to most recently recorded speed limit
        lastSpeedLimit = speedLimitMPH
    }

}

/** GLOBAL VARIABLES **/
//TextView for location data and speed limit
@SuppressLint("StaticFieldLeak")
private lateinit var textView: TextView
//TextView for acceleration data
@SuppressLint("StaticFieldLeak")
private lateinit var accText: TextView

@SuppressLint("StaticFieldLeak")
private lateinit var speedLimDisp: TextView

@SuppressLint("StaticFieldLeak")
private lateinit var currSpeedDisp: TextView

@SuppressLint("StaticFieldLeak")
private lateinit var speedingDisp: TextView

@SuppressLint("StaticFieldLeak")
private lateinit var stopDisp: TextView

@SuppressLint("StaticFieldLeak")
private lateinit var accelDisp: TextView
@SuppressLint("StaticFieldLeak")
private lateinit var homeButton: ImageButton
@SuppressLint("StaticFieldLeak")
private lateinit var reportsButton: ImageButton
@SuppressLint("StaticFieldLeak")
private lateinit var scoreButton: ImageButton
@SuppressLint("StaticFieldLeak")
private lateinit var startButton: ImageButton
@SuppressLint("StaticFieldLeak")
private lateinit var stopButton: ImageButton

//Sensor Manager and Sensor Event Listener for accelerometer
private lateinit var sensorManager: SensorManager
private lateinit var sensorEventListener: SensorEventListener

//Long variables tracking the last updates of the accelerometer and location
private var accLastUpdate: Long = System.currentTimeMillis()
private var locationLastUpdate: Long = System.currentTimeMillis()

//Boolean flag for when an acceleration spike has been detected
private var accSpike: Boolean = false

//Integers containing the number of hard stops and rapid accelerations
private var hardStops: Int = 0
private var rapidAcc: Int = 0

//Variables with the last known speed and speed limit
private var lastSpeed: Double = 0.0
private var lastSpeedLimit: Int = 0

//Variable containing the amount of estimated time spent over the speed threshold in ms
private var timeSpeeding: Long = 0

//Variables with max speed traveled (in increments of 5 MPH) and time at that speed
private var maxSpeed: Int = 0
private var maxSpeedTime: Long = 0

class MainActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_DENIED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PackageManager.PERMISSION_GRANTED
            )
        }

        homeButton = findViewById(R.id.homeButton)
        reportsButton = findViewById(R.id.reportsButton)
        scoreButton = findViewById(R.id.scoreButton)

        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        stopButton.visibility = View.GONE
        stopButton.isEnabled = false

        textView = findViewById(R.id.testText)
        accText = findViewById(R.id.accText)

        // Set text color to white to make debugging easy to access by commenting out these lines
        textView.setTextColor(Color.parseColor("#FFFFFF"))
        accText.setTextColor(Color.parseColor("#FFFFFF"))

        speedLimDisp = findViewById(R.id.speedLimDisp)
        currSpeedDisp = findViewById(R.id.currSpeedDisp)
        speedingDisp = findViewById(R.id.speedingDisp)
        stopDisp = findViewById(R.id.stopDisp)
        accelDisp = findViewById(R.id.accelDisp)


        // initialize Mapbox Navigation
        mapboxNavigation = if (MapboxNavigationProvider.isCreated()) {
            MapboxNavigationProvider.retrieve()
        } else {
            MapboxNavigationProvider.create(
                NavigationOptions.Builder(this.applicationContext)
                    .accessToken(getString(R.string.mapbox_access_token))
                    .build()
            )
        }
        //stop trip to make sure no false values are recorded
        mapboxNavigation.stopTripSession()
        // register location observer that tracks location
        mapboxNavigation.registerLocationObserver(locationObserver)

        // Initialize sensor manager and sensor event listener
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorEventListener = eventListener

        //register accelerometer
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        }
        //Implement listeners for buttons
        implementListeners()


    }

    private val eventListener: SensorEventListener = object : SensorEventListener {
        @SuppressLint("SetTextI18n")
        override fun onSensorChanged(sensorEvent: SensorEvent) {
            if (sensorEvent.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                //Get array of values from accelerometer
                val values = sensorEvent.values
                //Separate array into x, y, and z components of accelerometer reading
                val x = values[0]
                val y = values[1]
                val z = values[2]

                //Get total acceleration not including gravity in m/s^2
                val accSqrt = (x * x + y * y + z * z) / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH)
                //Get total acceleration not including gravity in MPH/s
                val accMph = accSqrt*2.23694
                //Get current system time
                val currentTime = System.currentTimeMillis()
                //Check that at least 200 ms have passed between checks
                if (currentTime - accLastUpdate < 250) {
                    return
                }
                //Update TextView with acceleration and time difference for debugging
                accText.text = accMph.toString() + "\n" + (currentTime - accLastUpdate).toString()
                //Update last update time to current time
                accLastUpdate = currentTime
                /*If acceleration not including gravity is greater than 8 MPH/s throw flag to check
                if speed has increased or decreased by at least 7 MPH. This will help compensate
                for any accidental shifts in acceleration like the phone moving within the vehicle
                or the phone being dropped while the car is in motion */
                if (accMph >= 8) {
                    accSpike = true
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, i: Int) {}
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun implementListeners() {
        startButton.setOnClickListener(View.OnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PackageManager.PERMISSION_GRANTED
                )
            }
            // Begin recording location updates
            mapboxNavigation.startTripSession()

            // Change button visibility
            startButton.visibility = View.GONE
            startButton.isEnabled = false

            stopButton.visibility = View.VISIBLE
            stopButton.isEnabled = true
        })

        stopButton.setOnClickListener(View.OnClickListener {
            // Stop recording location updates
            mapboxNavigation.stopTripSession()

            // Change button visibility
            stopButton.visibility = View.GONE
            stopButton.isEnabled = false

            startButton.visibility = View.VISIBLE
            startButton.isEnabled = true

            // Revert Displays
//        speedLimDisp.text = "Waiting to start"
//        currSpeedDisp.text = "Waiting to start"
//        speedingDisp.text = "Waiting to start"
//        stopDisp.text = "Waiting to start"
//        accelDisp.text = "Waiting to start"

        })
    }

}