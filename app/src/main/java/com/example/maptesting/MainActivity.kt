package com.example.maptesting

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.*
import kotlin.math.*

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
        //set default speeding limit to 30 mph
        var speedingLimit = 30
        //Get speed that should not be exceeded if there exists a speed limit
        if (speedLimitMPH != 0) {
            speedingLimit = speedLimitMPH + 5
        }

        //Get current speed from GPS
        val currentSpeed = locationMatcherResult.enhancedLocation.speed*2.23694
        //Floor speed to nearest multiple of 5
        val roundSpeed = floor(currentSpeed/5).times(5).toInt()
        //Get difference in time between now and last reading
        val timeChange = currentTime - locationLastUpdate
        //Get change in speed from current to last reading
        val speedChange = currentSpeed - lastSpeed
        //Calculate acceleration based on change in speed over change in time in seconds
        val calcAcceleration = speedChange/timeChange * 1000

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

        //If calculated Acceleration is less than -7 MPH/s a hard stop has occurred
        if (calcAcceleration <= -7) {
            hardStops++
        //If calculated acceleration is greater than 7 MPH/s a rapid acceleration has occurred
        } else if (calcAcceleration >= 7) {
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
        textView.text = "CA: " + calcAcceleration.toString() + "\n" +
                "SC: " + speedChange.toString() + "\n" +
                "TC: " + timeChange.toString() + "\n"

        //If there is a speed limit found
        if(speedLimitMPH > 5) {
            //Display the speed limit
            speedLimDisp.text = "$speedLimitMPH mph"
        } else {
            //Display not found
            speedLimDisp.text = "Not found"
        }

        //Display the current speed of the vehicle rounded to a whole number
        currSpeedDisp.text = "${currentSpeed.roundToLong()} mph"
        //If the current speed is over 5mph over the speed limit
        if (currentSpeed > speedingLimit) {
            //Set the color of the speed to red
            currSpeedDisp.setTextColor(Color.parseColor("#FF0000"))
        } else {
            //Set the color of the speed to black
            currSpeedDisp.setTextColor(Color.parseColor("#000000"))
        }
        val timeSpeedSec = String.format("%02d", timeSpeeding%1000)
        val timeSpeedMin = String.format("%02d", timeSpeeding%(60*1000))
        //If time speeding is greater than an hour
        if (timeSpeeding > 60*60*1000) {
            //Display time speeding in hours, minutes, and seconds
            speedingDisp.text = "${timeSpeeding % (60*60*1000)}:${timeSpeedMin}:${timeSpeedSec}"
        } else {
            //Display time speeding in minutes and seconds
            speedingDisp.text = "${timeSpeedSec}:${timeSpeedSec}"
        }
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

// Variables for determining start and end time of trip
private var startTime: Long = 0
private var stopTime: Long = 0

// Variables for user scores
private var totScore: Double = 0.0
private var braScore: Double = 0.0
private var accScore: Double = 0.0
private var spdScore: Double = 0.0

@RequiresApi(VERSION_CODES.O)
private val weekOf: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

class MainActivity : AppCompatActivity() {

    @RequiresApi(VERSION_CODES.M)
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

        reportsButton = findViewById(R.id.reportsButton)
        scoreButton = findViewById(R.id.scoreButton)

        //Start and stop buttons with stop being invisible to start
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        stopButton.visibility = View.GONE
        stopButton.isEnabled = false

        //Debugging Text Boxes
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

                //Get total squared acceleration not including gravity in m/s^2
                val accSqrd = (x*x+y*y+z*z)/(SensorManager.GRAVITY_EARTH*SensorManager.GRAVITY_EARTH)
                //Get total acceleration not including gravity in m/s^2
                val accSqrt = sqrt(accSqrd)
                //Get total acceleration not including gravity in MPH/s
                val accMph = accSqrt*2.23694
                //Get current system time
                val currentTime = System.currentTimeMillis()
                //Check that at least 200 ms have passed between checks
                if (currentTime - accLastUpdate < 100) {
                    return
                }
                //Update TextView with acceleration and time difference for debugging
                accText.text = accMph.toString() + "\n" + (currentTime - accLastUpdate).toString()
                //Update last update time to current time
                accLastUpdate = currentTime
                /*If acceleration not including gravity is greater than 8 MPH/s throw flag to check
                if speed has increased or decreased by at least 4 MPH. This will help compensate
                for any accidental shifts in acceleration like the phone moving within the vehicle
                or the phone being dropped while the car is in motion */
                if (accSqrt > 2.68) {
                    accSpike = true
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, i: Int) {}
    }

    @RequiresApi(VERSION_CODES.M)
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

            reportsButton.isEnabled = false
            scoreButton.isEnabled = false

            startTime = System.currentTimeMillis();
        })

        stopButton.setOnClickListener(View.OnClickListener {
            val alertDialog: AlertDialog = this.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setTitle(R.string.confirmation)
                    setPositiveButton(R.string.ok,
                        DialogInterface.OnClickListener { _, _ ->
                            // User clicked OK button
                            // Stop recording location updates
                            mapboxNavigation.stopTripSession()

                            // Change button visibility
                            stopButton.visibility = View.GONE
                            stopButton.isEnabled = false

                            startButton.visibility = View.VISIBLE
                            startButton.isEnabled = true

                            reportsButton.isEnabled = true
                            scoreButton.isEnabled = true

                            stopTime = System.currentTimeMillis()

                            // Total time spent driving in ms
                            val totalTimeMills: Long = stopTime - startTime

                            // Save data to internal storage

                            // File for report naming and path
                            val fileName: String = "$weekOf"
                            val file: File = File(applicationContext.filesDir, fileName)
                            var fileData: String

                            // File report does exist => need to update cumulative data
                            if(file.exists()) {
                                Log.d("FILE", "FILE FOUND")
                                // Variables that will have previous data added to them to become new stored parameter
                                var updatedTotalTimeMills: Long = totalTimeMills
                                var updatedTimeSpeeding: Long = timeSpeeding
                                var updatedHardStops: Int = hardStops
                                var updatedRapidAcc: Int = rapidAcc
                                var updatedMaxSpeed: Int = maxSpeed
                                var currFileData: String = ""
                                var updatedtotScore: Double = totScore
                                var updatedbraScore: Double = braScore
                                var updatedaccScore: Double = accScore
                                var updatedspdScore: Double = spdScore

                                // Read the existing file
                                try {
                                    val fis = openFileInput(file.name)
                                    val InputRead = InputStreamReader(fis)

                                    // Read report contents 10 characters at a time
                                    val inputBuffer = CharArray(50)
                                    var charRead: Int
                                    while (InputRead.read(inputBuffer).also { charRead = it } > 0) {
                                        val readString = String(inputBuffer, 0, charRead)
                                        currFileData += readString
                                    }

                                    // Close input stream
                                    InputRead.close()
                                    fis.close()

                                } catch (e: FileNotFoundException) {
                                    e.printStackTrace()
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                }

                                // Parse old file line by line and generate updated values
                                var lineNum: Int = 0
                                val scanner = Scanner(currFileData)
                                while (scanner.hasNextLine()) {
                                    val line: String = scanner.nextLine()
                                    if(lineNum == 1) {
                                        Log.d("EXISTING FILE DATA", "OLD TIME DRIVING WAS $line")
                                        // Convert line to long
                                        val oldTime: Long? = line.toLong()
                                        if (oldTime != null) {
                                            updatedTotalTimeMills += oldTime
                                        }
                                    } else if(lineNum == 4){
                                        Log.d("EXISTING FILE DATA", "OLD TIME SPEEDING WAS $line")
                                        // Convert line to long
                                        val oldTimeSpeeding: Long? = line.toLong()
                                        if (oldTimeSpeeding != null) {
                                            updatedTimeSpeeding += oldTimeSpeeding
                                        }
                                    } else if(lineNum == 7) {
                                        Log.d("EXISTING FILE DATA", "OLD HARD STOPS WAS $line")
                                        // Convert line to int
                                        val oldHardStop: Int? = line.toInt()
                                        if (oldHardStop != null) {
                                            updatedHardStops += oldHardStop
                                        }
                                    } else if (lineNum == 10) {
                                        Log.d("EXISTING FILE DATA", "OLD RAPID ACC WAS $line")
                                        // Convert line to int
                                        val oldRapidAcc: Int? = line.toInt()
                                        if (oldRapidAcc != null) {
                                            updatedRapidAcc += oldRapidAcc
                                        }
                                    } else if (lineNum == 13) {
                                        Log.d("EXISTING FILE DATA", "OLD MAX SPEED WAS $line")
                                        // Convert line to int
                                        val oldMaxSpeed: Int? = line.toInt()
                                        if(oldMaxSpeed!! > maxSpeed) {
                                            updatedMaxSpeed = oldMaxSpeed
                                        }
                                    }
                                    lineNum++

                                }
                                scanner.close()

                                var hoursDriven: Double = totalTimeMills.toDouble() / 1000.0 / 60.0 / 60.0

                                if(hoursDriven < 1)  hoursDriven = 1.0

                                // Calculate for updated scores
                                updatedbraScore = max(0.0, (100 - 10*updatedHardStops * updatedHardStops / hoursDriven))
                                updatedaccScore = (max(0.0, 100 - 10*updatedRapidAcc * updatedRapidAcc / hoursDriven))
                                updatedspdScore = min(100.0, max(0.toDouble(), (100 - 100 * ((updatedTimeSpeeding.toDouble() / updatedTotalTimeMills.toDouble() - 0.09)) - 20 * max(0,(updatedMaxSpeed - 75)/5))))
                                updatedtotScore = (updatedbraScore + updatedaccScore + updatedspdScore) / 3.0

                                fileData =
                                    "Total time driving:\n$updatedTotalTimeMills\n\nTotal time speeding:\n$updatedTimeSpeeding\n\nHard stops:\n$updatedHardStops\n\nRapid accelerations:\n$updatedRapidAcc\n\nTop speed (mph):\n$updatedMaxSpeed\n\nTotal Score:\n$updatedtotScore\n\nBraking Score:\n$updatedbraScore\n\nAcceleration Score:\n$updatedaccScore\n\nSpeeding Score:\n$updatedspdScore\n\n"

                                if(updatedbraScore < 65 || updatedaccScore < 65 || updatedspdScore < 65 ) {
                                    fileData += "Tips:\n"
                                }

                                // Give tips
                                if (updatedaccScore < 65) {
                                    fileData += "\nGradually accelerate your vehicle.\n"
                                }
                                if (updatedbraScore < 65) {
                                    fileData += "\nLeave a safe following distance.\nMore following distance should be given at higher speeds.\n"
                                    fileData += "Begin breaking earlier for a more gradual stop.\n"

                                }
                                if (updatedspdScore < 65) {
                                    fileData += "\nSpeeding thrills but kills.\n"

                                }
                            } else {
                                // File for report does not exist => write data from this session directly
                                Log.d("FILE", "FILE NOT FOUND")

                                val hoursDriven: Double = totalTimeMills.toDouble() / 1000.0 / 60.0 / 60.0

                                // Calculate for scores
                                braScore = max(0.0, (100 - 10 * hardStops * hardStops / hoursDriven))
                                accScore = max(0.0, (100 - 10 * rapidAcc * rapidAcc / hoursDriven))
                                spdScore = min(100.0, max(0.toDouble(), (100 - 100*((timeSpeeding.toDouble() / totalTimeMills.toDouble() - 0.09)) - 20 * max(0,(maxSpeed - 75)/5))))
                                totScore = (braScore + accScore + spdScore) / 3.0


                                fileData =
                                    "Total time driving:\n$totalTimeMills\n\nTotal time speeding:\n$timeSpeeding\n\nHard stops:\n$hardStops\n\nRapid accelerations:\n$rapidAcc\n\nTop speed (mph):\n$maxSpeed\n\nTotal score:\n$totScore\n\nHard brake scores:\n$braScore\n\nRapid acceleration score:\n$accScore\n\nSpeeding score:\n$spdScore\n\n"

                                if (braScore < 65 || accScore < 65 || spdScore < 65) {
                                    fileData += "Tips:\n"
                                }
                                // Give tips
                                if (accScore < 65) {
                                    fileData += "Gradually accelerate your vehicle\n"
                                }
                                if (braScore < 65) {
                                    fileData += "Leave a safe following distance. More following distance should be given at higher speeds.\n"
                                    fileData += "Begin breaking earlier for a more gradual stop\n"

                                }
                                if (spdScore < 65) {
                                    fileData += "Speeding thrills but kills"

                                }
                            }

                            // Write file data
                            try {
                                val fos = openFileOutput(file.name, MODE_PRIVATE)
                                fos.write(fileData.toByteArray())
                                fos.close()
                            } catch (e: FileNotFoundException) {
                                e.printStackTrace()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }

                            // File for report does exist
                            // Have to read each line and edit the values. Then rewrite the file (copy/paste from above)
                        })
                    setNegativeButton(R.string.cancel,
                        DialogInterface.OnClickListener { _, _ ->
                            // User cancelled the dialog
                            return@OnClickListener
                        })
                    show()
                }
                // Create the AlertDialog
                builder.create()
            }

            // Revert Displays
//        speedLimDisp.text = "Waiting to start"
//        currSpeedDisp.text = "Waiting to start"
//        speedingDisp.text = "Waiting to start"
//        stopDisp.text = "Waiting to start"
//        accelDisp.text = "Waiting to start"

        })

        reportsButton.setOnClickListener(View.OnClickListener {
            val reports = Intent(this, Reports::class.java)
            startActivity(reports)
        })

        scoreButton.setOnClickListener(View.OnClickListener {
            val score = Intent(this, OverallScore::class.java)
            startActivity(score)
        })
    }

}