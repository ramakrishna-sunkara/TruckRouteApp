package com.tomtom.timetoleave

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.tomtom.online.sdk.common.location.LatLng
import com.tomtom.online.sdk.common.util.DateFormatter
import com.tomtom.online.sdk.map.*
import com.tomtom.online.sdk.routing.OnlineRoutingApi
import com.tomtom.online.sdk.routing.RoutingApi
import com.tomtom.online.sdk.routing.RoutingException
import com.tomtom.online.sdk.routing.route.*
import com.tomtom.online.sdk.routing.route.description.TravelMode
import kotlinx.android.synthetic.main.activity_countdown.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class CountdownActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val BUNDLE_SETTINGS = "SETTINGS"
        private const val BUNDLE_DEPARTURE_LAT = "DEPARTURE_LAT"
        private const val BUNDLE_DEPARTURE_LNG = "DEPARTURE_LNG"
        private const val BUNDLE_DESTINATION_LAT = "DESTINATION_LAT"
        private const val BUNDLE_DESTINATION_LNG = "DESTINATION_LNG"
        private const val BUNDLE_BY_WHAT = "BY_WHAT"
        private const val BUNDLE_ARRIVE_AT = "ARRIVE_AT"
        private const val BUNDLE_PREPARATION_TIME = "PREPARATION_TIME"
        private const val COUNTDOWN_MODE_PREPARATION = "countdown_mode_preparation"
        private const val COUNTDOWN_MODE_FINISHED = "countdown_mode_finished"
        private const val ONE_MINUTE_IN_MILLIS = 60000
        private const val ONE_SECOND_IN_MILLIS = 1000
        private const val ROUTE_RECALCULATION_DELAY = ONE_MINUTE_IN_MILLIS

        fun prepareIntent(context: Context, departure: LatLng, destination: LatLng, strByWhat: TravelMode, arriveAtMillis: Long, preparationTime: Int): Intent {
            val settings = Bundle().apply {
                this.putDouble(BUNDLE_DEPARTURE_LAT, departure.latitude)
                this.putDouble(BUNDLE_DEPARTURE_LNG, departure.longitude)
                this.putDouble(BUNDLE_DESTINATION_LAT, destination.latitude)
                this.putDouble(BUNDLE_DESTINATION_LNG, destination.longitude)
                this.putString(BUNDLE_BY_WHAT, strByWhat.toString())
                this.putLong(BUNDLE_ARRIVE_AT, arriveAtMillis)
                this.putInt(BUNDLE_PREPARATION_TIME, preparationTime)
            }
            val intent = Intent(context, CountdownActivity::class.java)
            intent.putExtra(BUNDLE_SETTINGS, settings)

            return intent
        }
    }

    private var preparationTime: Int = 0
    private var previousTravelTime: Int = 0
    private var isPreparationMode = false
    private var isInPauseMode = false
    private var arriveAt: Date? = null
    private lateinit var infoSnackbar: CustomSnackbar
    private lateinit var warningSnackbar: CustomSnackbar
    private lateinit var dialogInProgress: AlertDialog
    private var dialogInfo: AlertDialog? = null
    private lateinit var tomtomMap: TomtomMap
    private lateinit var routingApi: RoutingApi
    private lateinit var departureIcon: Icon
    private lateinit var destinationIcon: Icon
    private var travelMode: TravelMode? = null
    private var destination: LatLng? = null
    private var departure: LatLng? = null
    private var countDownTimer: CountDownTimer? = null
    private val timerHandler = Handler()

    private val requestRouteRunnable = Runnable { requestRoute(departure, destination, travelMode, arriveAt) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown)

        initTomTomServices()
        initToolbarSettings()
        initActivitySettings(intent.getBundleExtra(BUNDLE_SETTINGS))
    }

    override fun onPause() {
        super.onPause()
        isInPauseMode = true
    }

    override fun onResume() {
        super.onResume()
        if (!dialogIsShowing(dialogInfo)) {
            isInPauseMode = false
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        timerHandler.removeCallbacks(requestRouteRunnable)
        countDownTimer?.cancel()
    }

    override fun onMapReady(tomtomMap: TomtomMap) {
        this.tomtomMap = tomtomMap
        tomtomMap.apply {
            this.isMyLocationEnabled = true
            this.clear()
        }
        showDialog(dialogInProgress)
        requestRoute(departure, destination, travelMode, arriveAt)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        this.tomtomMap.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun requestRoute(departure: LatLng?, destination: LatLng?, byWhat: TravelMode?, arriveAt: Date?) {
        if (!isInPauseMode) {
            val routeDescriptor = RouteDescriptor.Builder()
                    .routeType(com.tomtom.online.sdk.routing.route.description.RouteType.FASTEST)
                    .considerTraffic(true)
                    .travelMode(byWhat!!)
                    .build()

            val routeCalculationDescriptor = RouteCalculationDescriptor.Builder()
                    .routeDescription(routeDescriptor)
                    .arriveAt(arriveAt!!)
                    .build()

            val routeSpecification = RouteSpecification.Builder(departure!!, destination!!)
                    .routeCalculationDescriptor(routeCalculationDescriptor)
                    .build()

            routingApi.planRoute(routeSpecification, object : RouteCallback {
                override fun onSuccess(routePlan: RoutePlan) {
                    hideDialog(dialogInProgress)
                    if (routePlan.routes.isNotEmpty()) {
                        val fullRoute = routePlan.routes.first()
                        val currentTravelTime = fullRoute.summary.travelTimeInSeconds
                        if (previousTravelTime != currentTravelTime) {
                            val travelDifference = previousTravelTime - currentTravelTime
                            if (previousTravelTime != 0) {
                                showWarningSnackbar(prepareWarningMessage(travelDifference))
                            }
                            previousTravelTime = currentTravelTime
                            displayRouteOnMap(fullRoute.getCoordinates())
                            val departureTimeString = fullRoute.summary.departureTime
                            setupCountDownTimer(DateFormatter().formatWithTimeZone(departureTimeString).toDate())
                        } else {
                            infoSnackbar.show()

                        }
                    }
                    timerHandler.removeCallbacks(requestRouteRunnable)
                    timerHandler.postDelayed(requestRouteRunnable, ROUTE_RECALCULATION_DELAY.toLong())
                }

                override fun onError(error: RoutingException) {
                    hideDialog(dialogInProgress)
                    Toast.makeText(this@CountdownActivity, getString(R.string.toast_error_message_cannot_find_route), Toast.LENGTH_LONG).show()
                    this@CountdownActivity.finish()
                }

                private fun setupCountDownTimer(departure: Date) {
                    countDownTimer?.cancel()

                    val now = Calendar.getInstance().time
                    val preparationTimeMillis = preparationTime * ONE_MINUTE_IN_MILLIS
                    val timeToLeave = departure.time - now.time
                    countDownTimer = object : CountDownTimer(timeToLeave, ONE_SECOND_IN_MILLIS.toLong()) {
                        override fun onTick(millisUntilFinished: Long) {
                            updateCountdownTimerTextViews(millisUntilFinished)
                            if (!isPreparationMode && millisUntilFinished <= preparationTimeMillis) {
                                isPreparationMode = true
                                setCountdownTimerColor(COUNTDOWN_MODE_PREPARATION)
                                if (!isInPauseMode) {
                                    showPreparationInfoDialog()
                                }
                            }
                        }

                        override fun onFinish() {
                            timerHandler.removeCallbacks(requestRouteRunnable)
                            setCountdownTimerColor(COUNTDOWN_MODE_FINISHED)
                            if (!isInPauseMode) {
                                createDialogWithCustomButtons()
                            }
                        }
                    }.start()
                    text_countdown_travel_time.text = getString(R.string.travel_time_text, formatTimeFromSecondsDisplayWithoutSeconds(previousTravelTime.toLong()))
                }

                private fun prepareWarningMessage(travelDifference: Int): String {
                    val travelTimeDifference = formatTimeFromSecondsDisplayWithSeconds(travelDifference.toLong())
                    return getString(R.string.dialog_recalculation_info, getTimeInfoWithPrefix(travelDifference, travelTimeDifference))
                }

                private fun showWarningSnackbar(warningMessage: String) {
                    warningSnackbar.apply {
                        this.setText(warningMessage)
                        this.show()
                    }
                }

                private fun getTimeInfoWithPrefix(travelDifference: Int, travelTimeDifference: String): String {
                    val prefix = if (travelDifference < 0) "-" else "+"
                    return prefix + travelTimeDifference
                }

                private fun createDialogWithCustomButtons() {
                    val builder = AlertDialog.Builder(this@CountdownActivity)
                    builder.setMessage(getString(R.string.dialog_time_to_leave))
                            .setPositiveButton(R.string.dialog_on_my_way) { _, _ ->
                                hideDialog(dialogInfo)
                                val intent = Intent(this@CountdownActivity, SafeTravelsActivity::class.java)
                                startActivity(intent)
                            }
                            .setNegativeButton(R.string.dialog_whatever) { _, _ -> createDialogWithCustomLayout() }
                    hideDialog(dialogInfo)
                    dialogInfo = builder.create().apply {
                        this.setCanceledOnTouchOutside(false)
                    }
                    showDialog(dialogInfo)
                }

                private fun createDialogWithCustomLayout() {
                    val builder = AlertDialog.Builder(this@CountdownActivity)
                    val inflater = layoutInflater

                    builder.setView(inflater.inflate(R.layout.dialog_you_are_over_time, null))
                            .setPositiveButton(R.string.dialog_next_time_ill_do_better) { _, _ ->
                                hideDialog(dialogInfo)
                                val intent = Intent(this@CountdownActivity, MainActivity::class.java)
                                startActivity(intent)
                            }
                    hideDialog(dialogInfo)
                    dialogInfo = builder.create().apply {
                        this.setCanceledOnTouchOutside(false)
                    }
                    showDialog(dialogInfo)
                }

                private fun showPreparationInfoDialog() {
                    dialogInfo = createSimpleAlertDialog(getString(R.string.dialog_start_preparation_text, preparationTime))
                    showDialog(dialogInfo)
                }

                private fun createSimpleAlertDialog(message: String): AlertDialog {
                    val builder = AlertDialog.Builder(this@CountdownActivity)
                    builder.setMessage(message)
                    return builder.create()
                }

                private fun setCountdownTimerColor(state: String) {
                    val color = when (state) {
                        COUNTDOWN_MODE_PREPARATION -> R.color.color_countdown_mode_preparation
                        COUNTDOWN_MODE_FINISHED -> R.color.color_countdown_mode_finished
                        else -> R.color.color_all_text
                    }
                    val resolvedColor = ContextCompat.getColor(this@CountdownActivity, color)
                    text_view_countdown_timer_hour.setTextColor(resolvedColor)
                    text_view_countdown_timer_minute.setTextColor(resolvedColor)
                    text_view_countdown_timer_second.setTextColor(resolvedColor)
                }

                private fun updateCountdownTimerTextViews(millis: Long) {
                    val hours = TimeUnit.MILLISECONDS.toHours(millis)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes)
                    text_view_countdown_timer_hour.text = getString(R.string.countdown_timer_hour, hours)
                    text_view_countdown_timer_minute.text = getString(R.string.countdown_timer_min, minutes)
                    text_view_countdown_timer_second.text = getString(R.string.countdown_timer_sec, seconds)
                }

                private fun displayRouteOnMap(coordinates: List<LatLng>) {
                    val routeBuilder = RouteBuilder(coordinates)
                            .startIcon(departureIcon)
                            .endIcon(destinationIcon)
                    tomtomMap.clear()
                    tomtomMap.addRoute(routeBuilder)
                    tomtomMap.displayRoutesOverview()
                }

                private fun formatTimeFromSecondsDisplayWithSeconds(secondsTotal: Long): String {
                    return formatTimeFromSeconds(secondsTotal, true)
                }

                private fun formatTimeFromSecondsDisplayWithoutSeconds(secondsTotal: Long): String {
                    return formatTimeFromSeconds(secondsTotal, false)
                }

                private fun formatTimeFromSeconds(secondsTotal: Long, showSeconds: Boolean): String {
                    var secondsTotal = secondsTotal
                    val timeFormatHoursMinutes = "H'h' m'min'"
                    val timeFormatMinutes = "m'min'"
                    val timeFormatSeconds = " s'sec'"

                    val hours = TimeUnit.SECONDS.toHours(secondsTotal)
                    val minutes = TimeUnit.SECONDS.toMinutes(secondsTotal) - TimeUnit.HOURS.toMinutes(hours)
                    var timeFormat = ""

                    if (hours != 0L) {
                        timeFormat = timeFormatHoursMinutes
                    } else {
                        if (minutes != 0L) {
                            timeFormat = timeFormatMinutes
                        }
                    }

                    if (showSeconds) {
                        timeFormat += timeFormatSeconds
                    }
                    secondsTotal = abs(secondsTotal)
                    return DateFormat.format(timeFormat, TimeUnit.SECONDS.toMillis(secondsTotal)) as String
                }
            })
        } else {
            timerHandler.removeCallbacks(requestRouteRunnable)
            timerHandler.postDelayed(requestRouteRunnable, ROUTE_RECALCULATION_DELAY.toLong())
        }
    }

    private fun initTomTomServices() {
        routingApi = OnlineRoutingApi.create(applicationContext, BuildConfig.ROUTING_API_KEY)
        val mapKeys = mapOf(ApiKeyType.MAPS_API_KEY to BuildConfig.MAPS_API_KEY)
        val mapProperties = MapProperties.Builder().keys(mapKeys).build()
        val mapFragment = MapFragment.newInstance(mapProperties)
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.mapFragment, mapFragment)
                .commit()
        mapFragment.getAsyncMap(this)
    }

    private fun initToolbarSettings() {
        setSupportActionBar(custom_toolbar as Toolbar)
        supportActionBar?.apply {
            this.setDisplayHomeAsUpEnabled(true)
            this.setDisplayShowHomeEnabled(true)
            this.setDisplayShowTitleEnabled(false)
        }
    }

    private fun initActivitySettings(settings: Bundle?) {
        departureIcon = Icon.Factory.fromResources(this@CountdownActivity, R.drawable.ic_map_route_departure)
        destinationIcon = Icon.Factory.fromResources(this@CountdownActivity, R.drawable.ic_map_route_destination)

        initBundleSettings(settings)
        img_countdown_by_what.setImageResource(getTravelModeIcon(travelMode!!))
        text_countdown_preparation.text = getString(R.string.preparation_indicator_info, preparationTime)
        previousTravelTime = 0

        createWarningSnackBar()
        createInfoSnackBar()
        createDialogInProgress()
    }

    private fun initBundleSettings(settings: Bundle?) {
        val calendar = Calendar.getInstance()
        settings?.let {
            val arriveAtMillis = settings.getLong(BUNDLE_ARRIVE_AT)
            calendar.timeInMillis = arriveAtMillis
            arriveAt = calendar.time
            departure = LatLng(settings.getDouble(BUNDLE_DEPARTURE_LAT), settings.getDouble(BUNDLE_DEPARTURE_LNG))
            destination = LatLng(settings.getDouble(BUNDLE_DESTINATION_LAT), settings.getDouble(BUNDLE_DESTINATION_LNG))
            travelMode = TravelMode.valueOf(settings.getString(BUNDLE_BY_WHAT)!!.toUpperCase(Locale.getDefault()))
            preparationTime = settings.getInt(BUNDLE_PREPARATION_TIME)
        }
    }

    private fun getTravelModeIcon(selectedTravelMode: TravelMode): Int {
        return when (selectedTravelMode) {
            TravelMode.TAXI -> R.drawable.button_main_travel_mode_cab
            TravelMode.PEDESTRIAN -> R.drawable.button_main_travel_mode_by_foot
            TravelMode.CAR -> R.drawable.button_main_travel_mode_car
            else -> R.drawable.button_main_travel_mode_car
        }
    }

    private fun createWarningSnackBar() {
        val view = findViewById<ViewGroup>(android.R.id.content)
        warningSnackbar = CustomSnackbar.make(view, BaseTransientBottomBar.LENGTH_INDEFINITE, R.layout.snackbar_recalculation_warning).apply {
            this.setAction(getString(R.string.button_ok)) { warningSnackbar.dismiss() }
        }
        setCustomSnackbar(warningSnackbar)
    }

    private fun createInfoSnackBar() {
        val view = findViewById<ViewGroup>(android.R.id.content)
        infoSnackbar = CustomSnackbar.make(view, BaseTransientBottomBar.LENGTH_LONG, R.layout.snackbar_recalculation_info).apply {
            this.setText(getString(R.string.dialog_recalculation_no_changes))
        }
        setCustomSnackbar(infoSnackbar)
    }

    private fun createDialogInProgress() {
        val inflater = layoutInflater
        val builder = AlertDialog.Builder(this@CountdownActivity).apply {
            this.setView(inflater.inflate(R.layout.dialog_in_progress, null))
        }
        dialogInProgress = builder.create().apply {
            this.setCanceledOnTouchOutside(false)
        }
    }

    private fun setCustomSnackbar(snackbar: CustomSnackbar) {
        val transparentColor = ContextCompat.getColor(this@CountdownActivity, R.color.transparent)
        snackbar.view.setBackgroundColor(transparentColor)
        val paddingSnackbar = resources.getDimension(R.dimen.padding_snackbar).toInt()
        snackbar.view.setPadding(paddingSnackbar, paddingSnackbar, paddingSnackbar, paddingSnackbar)
    }

    private fun hideDialog(dialog: Dialog?) {
        if (dialogIsShowing(dialog)) {
            dialog?.dismiss()
        }
    }

    private fun showDialog(dialog: Dialog?) {
        if (!dialogIsShowing(dialog)) {
            dialog?.show()
        }
    }

    private fun dialogIsShowing(dialog: Dialog?): Boolean {
        return dialog != null && dialog.isShowing
    }
}
