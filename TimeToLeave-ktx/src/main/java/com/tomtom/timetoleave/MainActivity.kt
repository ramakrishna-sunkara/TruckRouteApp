package com.tomtom.timetoleave

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationRequest
import com.tomtom.online.sdk.common.location.LatLng
import com.tomtom.online.sdk.common.permission.AndroidPermissionChecker
import com.tomtom.online.sdk.location.FusedLocationSource
import com.tomtom.online.sdk.location.LocationSource
import com.tomtom.online.sdk.location.LocationUpdateListener
import com.tomtom.online.sdk.routing.route.description.TravelMode
import com.tomtom.online.sdk.search.OnlineSearchApi
import com.tomtom.online.sdk.search.SearchApi
import com.tomtom.online.sdk.search.data.fuzzy.FuzzySearchQueryBuilder
import com.tomtom.online.sdk.search.data.fuzzy.FuzzySearchResponse
import com.tomtom.online.sdk.search.data.reversegeocoder.ReverseGeocoderFullAddress
import com.tomtom.online.sdk.search.data.reversegeocoder.ReverseGeocoderSearchQueryBuilder
import com.tomtom.online.sdk.search.data.reversegeocoder.ReverseGeocoderSearchResponse
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.util.*

class MainActivity : AppCompatActivity(), LocationUpdateListener {

    companion object {
        private const val PREPARATION_FIRST_OPT = 0
        private const val PREPARATION_SECOND_OPT = 5
        private const val PREPARATION_THIRD_OPT = 10
        private val DEFAULT_DEPARTURE_LATLNG = LatLng(52.376368, 4.908113)
        private val DEFAULT_DESTINATION_LATLNG = LatLng(52.3076865, 4.767424099999971)
        private const val TIME_PICKER_DIALOG_TAG = "TimePicker"
        private const val ARRIVE_TIME_AHEAD_HOURS = 5
        private const val AUTOCOMPLETE_SEARCH_DELAY_MILLIS = 600
        private const val AUTOCOMPLETE_SEARCH_THRESHOLD = 3
        private const val TIME_24H_FORMAT = "HH:mm"
        private const val TIME_12H_FORMAT = "hh:mm"
        private const val SEARCH_FUZZY_LVL_MIN = 2
        private const val PERMISSION_REQUEST_LOCATION = 0
    }

    var calArriveAt: Calendar = Calendar.getInstance().apply {
        this.add(Calendar.HOUR, ARRIVE_TIME_AHEAD_HOURS)
    }
    private lateinit var searchApi: SearchApi
    private lateinit var locationSource: LocationSource
    private lateinit var searchAdapter: ArrayAdapter<String>
    private lateinit var searchAutocompleteList: MutableList<String>
    private lateinit var searchResultsMap: MutableMap<String, LatLng>
    private val searchTimerHandler = Handler()
    private var searchRunnable: Runnable? = null
    private var travelModeSelected = TravelMode.CAR
    private var arrivalTimeInMillis: Long = 0
    private var latLngCurrentPosition: LatLng? = null
    private var latLngDeparture = DEFAULT_DEPARTURE_LATLNG
    private var latLngDestination = DEFAULT_DESTINATION_LATLNG
    private var preparationTimeSelected = PREPARATION_FIRST_OPT

    private val userPreferredHourPattern: String
        get() = if (DateFormat.is24HourFormat(applicationContext)) TIME_24H_FORMAT else TIME_12H_FORMAT

    private val currentTimeInMillis: Long
        get() {
            val calendar = Calendar.getInstance()
            return calendar.timeInMillis
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        showHelpOnFirstRun()
        initTomTomServices()
        initToolbarSettings()
        initSearchFieldsWithDefaultValues()
        initWhereSection()
        initByWhenSection()
        initByWhatSection()
        initPreparationSection()
        initStartSection()
    }

    override fun onResume() {
        super.onResume()
        resetDaysInArriveAt()
        val checker = AndroidPermissionChecker.createLocationChecker(this)
        if (!checker.ifNotAllPermissionGranted()) {
            locationSource.activate()
        }
    }

    override fun onLocationChanged(location: Location) {
        if (latLngCurrentPosition == null) {
            latLngCurrentPosition = LatLng(location)
            locationSource.deactivate()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val selectedItem = item.itemId
        if (selectedItem == R.id.toolbar_menu_help) {
            showHelpActivity()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    private fun initTomTomServices() {
        searchApi = OnlineSearchApi.create(this, BuildConfig.SEARCH_API_KEY)
    }

    private fun initToolbarSettings() {
        setSupportActionBar(custom_toolbar as Toolbar)
        supportActionBar?.apply {
            this.setDisplayHomeAsUpEnabled(false)
            this.setDisplayShowHomeEnabled(false)
            this.setDisplayShowTitleEnabled(false)
        }
    }

    private fun initSearchFieldsWithDefaultValues() {
        initLocationSource()
        initDepartureWithDefaultValue()
        initDestinationWithDefaultValue()
    }

    private fun initLocationSource() {
        val permissionChecker = AndroidPermissionChecker.createLocationChecker(this)
        if (permissionChecker.ifNotAllPermissionGranted()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_LOCATION)
        }
        locationSource = FusedLocationSource(this, LocationRequest.create())
        locationSource.addLocationUpdateListener(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.size >= 2 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                locationSource.activate()
            } else {
                Toast.makeText(this, R.string.location_permissions_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initDepartureWithDefaultValue() {
        latLngDeparture = DEFAULT_DEPARTURE_LATLNG
        setAddressForLocation(latLngDeparture, atv_main_departure_location)
    }

    private fun initDestinationWithDefaultValue() {
        latLngDestination = DEFAULT_DESTINATION_LATLNG
        setAddressForLocation(latLngDestination, atv_main_destination_location)
    }

    private fun showHelpOnFirstRun() {
        val sharedPreferenceName = getString(R.string.shared_preference_name)
        val sharedPreferenceIsFirstRun = getString(R.string.shared_preference_first_run)
        val isFirstRun = getSharedPreferences(sharedPreferenceName, Context.MODE_PRIVATE)
                .getBoolean(sharedPreferenceIsFirstRun, true)
        if (isFirstRun) {
            showHelpActivity()
            getSharedPreferences(sharedPreferenceName, Context.MODE_PRIVATE).edit()
                    .putBoolean(sharedPreferenceIsFirstRun, false).apply()
        }
    }

    private fun initWhereSection() {
        searchAutocompleteList = ArrayList()
        searchResultsMap = HashMap()
        searchAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, searchAutocompleteList)

        setTextWatcherToAutoCompleteField(atv_main_departure_location, button_departure_clear)
        setClearButtonToAutocompleteField(atv_main_departure_location, button_departure_clear)
        setTextWatcherToAutoCompleteField(atv_main_destination_location, button_destination_clear)
        setClearButtonToAutocompleteField(atv_main_destination_location, button_destination_clear)
    }

    private fun setTextWatcherToAutoCompleteField(autoCompleteTextView: AutoCompleteTextView, imageButton: ImageButton) {
        autoCompleteTextView.setAdapter(searchAdapter)
        autoCompleteTextView.addTextChangedListener(object : BaseTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                searchRunnable?.let {
                    searchTimerHandler.removeCallbacks(it)
                }
            }

            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    imageButton.visibility = View.VISIBLE
                    if (s.length >= AUTOCOMPLETE_SEARCH_THRESHOLD) {
                        searchRunnable = Runnable { searchAddress(s.toString(), autoCompleteTextView) }
                        searchAdapter.clear()
                        searchTimerHandler.postDelayed(searchRunnable!!, AUTOCOMPLETE_SEARCH_DELAY_MILLIS.toLong())
                    }
                } else {
                    imageButton.visibility = View.INVISIBLE
                }
            }
        })
        autoCompleteTextView.setOnItemClickListener { parent, view, position, _ ->
            val item = parent.getItemAtPosition(position) as String
            if (autoCompleteTextView === atv_main_departure_location) {
                latLngDeparture = searchResultsMap[item]!!
            } else if (autoCompleteTextView === atv_main_destination_location) {
                latLngDestination = searchResultsMap[item]!!
            }
            hideKeyboard(view)
        }
    }

    private fun searchAddress(searchWord: String, autoCompleteTextView: AutoCompleteTextView) {
        searchApi.search(FuzzySearchQueryBuilder(searchWord)
                .withLanguage(Locale.getDefault().toLanguageTag())
                .withTypeAhead(true)
                .withMinFuzzyLevel(SEARCH_FUZZY_LVL_MIN).build())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : DisposableSingleObserver<FuzzySearchResponse>() {
                    override fun onSuccess(fuzzySearchResponse: FuzzySearchResponse) {
                        if (!fuzzySearchResponse.results.isEmpty()) {
                            searchAutocompleteList.clear()
                            searchResultsMap.clear()
                            if (autoCompleteTextView === atv_main_departure_location && latLngCurrentPosition != null) {
                                val currentLocationTitle = getString(R.string.main_current_position)
                                searchAutocompleteList.add(currentLocationTitle)
                                searchResultsMap[currentLocationTitle] = latLngCurrentPosition!!
                            }
                            for (result in fuzzySearchResponse.results) {
                                val addressString = result.address.freeformAddress
                                searchAutocompleteList.add(addressString)
                                searchResultsMap[addressString] = result.position
                            }
                            searchAdapter.apply {
                                this.clear()
                                this.addAll(searchAutocompleteList)
                                this.filter.filter("")
                            }
                        }
                    }

                    override fun onError(e: Throwable) {
                        Toast.makeText(this@MainActivity, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                })
    }

    private fun setAddressForLocation(location: LatLng, autoCompleteTextView: AutoCompleteTextView?) {
        searchApi.reverseGeocoding(ReverseGeocoderSearchQueryBuilder(location.latitude, location.longitude).build())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : DisposableSingleObserver<ReverseGeocoderSearchResponse>() {
                    override fun onSuccess(reverseGeocoderSearchResponse: ReverseGeocoderSearchResponse) {
                        val addressesList = reverseGeocoderSearchResponse.addresses
                        if (!addressesList.isEmpty()) {
                            val address = (addressesList[0] as ReverseGeocoderFullAddress).address.freeformAddress
                            autoCompleteTextView?.apply {
                                this.setText(address)
                                this.dismissDropDown()
                            }
                        }
                    }

                    override fun onError(e: Throwable) {
                        Toast.makeText(this@MainActivity, getString(R.string.toast_error_message_error_getting_location, e.localizedMessage), Toast.LENGTH_LONG).show()
                        Timber.e(e, getString(R.string.toast_error_message_error_getting_location, e.localizedMessage))
                    }
                })
    }

    private fun setClearButtonToAutocompleteField(autoCompleteTextView: AutoCompleteTextView, imageButton: ImageButton) {
        imageButton.setOnClickListener {
            autoCompleteTextView.apply {
                this.setText("")
                this.requestFocus()
            }
            imageButton.visibility = View.GONE
        }
    }

    private fun initByWhenSection() {
        setTimerDisplay()
        text_view_main_arrive_at_hour.setOnClickListener {
            TimePickerFragment().apply {
                this.show(supportFragmentManager, TIME_PICKER_DIALOG_TAG)
            }
        }
    }


    fun setTimerDisplay() {
        val tvArriveAtHourString = DateFormat.format(userPreferredHourPattern, calArriveAt.timeInMillis) as String
        text_view_main_arrive_at_hour.text = tvArriveAtHourString
        setTvArriveAtAmPm(DateFormat.is24HourFormat(applicationContext), calArriveAt.get(Calendar.AM_PM))
    }

    private fun setTvArriveAtAmPm(is24HourFormat: Boolean, indicator: Int) {
        if (is24HourFormat) {
            text_view_main_arrive_at_am_pm.visibility = View.INVISIBLE
        } else {
            text_view_main_arrive_at_am_pm.visibility = View.VISIBLE
            val strAmPm = if (indicator == Calendar.AM) getString(R.string.main_am_value) else getString(R.string.main_pm_value)
            text_view_main_arrive_at_am_pm.text = strAmPm
        }
    }

    private fun initByWhatSection() {
        button_main_car.isSelected = true
        button_main_car.setOnClickListener(setByWhatButtonListener(TravelMode.CAR))
        button_main_taxi.setOnClickListener(setByWhatButtonListener(TravelMode.TAXI))
        button_main_on_foot.setOnClickListener(setByWhatButtonListener(TravelMode.PEDESTRIAN))
    }

    private fun setByWhatButtonListener(travelMode: TravelMode): View.OnClickListener {
        return View.OnClickListener {
            this@MainActivity.deselectByWhatButtons()
            it.isSelected = true
            travelModeSelected = travelMode
        }
    }

    private fun deselectByWhatButtons() {
        button_main_car.isSelected = false
        button_main_taxi.isSelected = false
        button_main_on_foot.isSelected = false
    }

    private fun initPreparationSection() {
        deselectPreparationButtons()
        selectPreparationButton(button_main_preparation_first)

        button_main_preparation_first.setOnClickListener(setPreparationButtonListener(PREPARATION_FIRST_OPT))
        button_main_preparation_second.setOnClickListener(setPreparationButtonListener(PREPARATION_SECOND_OPT))
        button_main_preparation_third.setOnClickListener(setPreparationButtonListener(PREPARATION_THIRD_OPT))
    }

    private fun setPreparationButtonListener(preparationTimeInMinutes: Int): View.OnClickListener {
        return View.OnClickListener {
            deselectPreparationButtons()
            selectPreparationButton(it)
            preparationTimeSelected = preparationTimeInMinutes
        }
    }

    private fun deselectPreparationButtons() {
        val elevationButtonNormal = resources.getDimension(R.dimen.main_elevation_button_normal)
        button_main_preparation_first.apply {
            this.isSelected = false
            this.elevation = elevationButtonNormal
        }
        button_main_preparation_second.apply {
            this.isSelected = false
            this.elevation = elevationButtonNormal
        }
        button_main_preparation_third.apply {
            this.isSelected = false
            this.elevation = elevationButtonNormal
        }
    }

    private fun selectPreparationButton(preparationButton: View) {
        preparationButton.isSelected = true
        val elevationButtonPressed = resources.getDimension(R.dimen.main_elevation_button_pressed)
        preparationButton.elevation = elevationButtonPressed
    }

    private fun initStartSection() {
        button_main_start.setOnClickListener {
            val currentTimeInMillis = currentTimeInMillis
            arrivalTimeInMillis = getArrivalTimeInMillis()

            if (departureFiledIsEmpty()) {
                initDepartureWithDefaultValue()
            } else if (destinationFieldIsEmpty()) {
                initDestinationWithDefaultValue()
            }

            if (currentTimeInMillis >= arrivalTimeInMillis) {
                calArriveAt.add(Calendar.DAY_OF_MONTH, 1)
                arrivalTimeInMillis = getArrivalTimeInMillis()
            }

            val intent = CountdownActivity.prepareIntent(this@MainActivity, latLngDeparture, latLngDestination,
                    travelModeSelected, arrivalTimeInMillis, preparationTimeSelected)
            startActivity(intent)
        }
    }

    private fun textViewIsEmpty(textView: AutoCompleteTextView): Boolean {
        return textView.text.toString().isEmpty()
    }

    private fun departureFiledIsEmpty(): Boolean {
        return textViewIsEmpty(atv_main_departure_location)
    }

    private fun destinationFieldIsEmpty(): Boolean {
        return textViewIsEmpty(atv_main_destination_location)
    }

    private fun getArrivalTimeInMillis(): Long {
        return calArriveAt.timeInMillis
    }

    private fun resetDaysInArriveAt() {
        val calendar = Calendar.getInstance()
        calArriveAt.set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH))
    }

    private fun hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.applicationWindowToken, 0)
    }

    private fun showHelpActivity() {
        val helpIntent = Intent(this@MainActivity, HelpActivity::class.java)
        startActivity(helpIntent)
    }

    private abstract inner class BaseTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
    }
}
