package com.tomtom.timetoleave;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.gms.location.LocationRequest;
import com.tomtom.online.sdk.common.location.LatLng;
import com.tomtom.online.sdk.common.permission.AndroidPermissionChecker;
import com.tomtom.online.sdk.common.permission.PermissionChecker;
import com.tomtom.online.sdk.location.FusedLocationSource;
import com.tomtom.online.sdk.location.LocationSource;
import com.tomtom.online.sdk.location.LocationUpdateListener;
import com.tomtom.online.sdk.routing.data.TravelMode;
import com.tomtom.online.sdk.search.OnlineSearchApi;
import com.tomtom.online.sdk.search.SearchApi;
import com.tomtom.online.sdk.search.data.fuzzy.FuzzySearchQueryBuilder;
import com.tomtom.online.sdk.search.data.fuzzy.FuzzySearchResponse;
import com.tomtom.online.sdk.search.data.fuzzy.FuzzySearchResult;
import com.tomtom.online.sdk.search.data.reversegeocoder.ReverseGeocoderFullAddress;
import com.tomtom.online.sdk.search.data.reversegeocoder.ReverseGeocoderSearchQueryBuilder;
import com.tomtom.online.sdk.search.data.reversegeocoder.ReverseGeocoderSearchResponse;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements LocationUpdateListener {

    private static final int PREPARATION_FIRST_OPT = 0;
    private static final int PREPARATION_SECOND_OPT = 5;
    private static final int PREPARATION_THIRD_OPT = 10;

    private static final LatLng DEFAULT_DEPARTURE_LATLNG = new LatLng(52.376368, 4.908113);
    private static final LatLng DEFAULT_DESTINATION_LATLNG = new LatLng(52.3076865, 4.767424099999971);
    private static final String TIME_PICKER_DIALOG_TAG = "TimePicker";
    private static final String LOG_TAG = "MainActivity";
    private static final int ARRIVE_TIME_AHEAD_HOURS = 5;
    private static final int AUTOCOMPLETE_SEARCH_DELAY_MILLIS = 600;
    private static final int AUTOCOMPLETE_SEARCH_THRESHOLD = 3;
    private static final String TIME_24H_FORMAT = "HH:mm";
    private static final String TIME_12H_FORMAT = "hh:mm";
    private static final int SEARCH_FUZZY_LVL_MIN = 2;
    private static final int PERMISSION_REQUEST_LOCATION = 0;

    private Calendar calArriveAt;
    private SearchApi searchApi;
    private LocationSource locationSource;
    private TextView textViewArriveAtHour;
    private TextView textViewArriveAtAmPm;
    private TravelMode travelModeSelected = TravelMode.CAR;
    private long arrivalTimeInMillis;
    private AutoCompleteTextView atvDepartureLocation;
    private AutoCompleteTextView atvDestinationLocation;
    private final Handler searchTimerHandler = new Handler();
    private Runnable searchRunnable;
    private ArrayAdapter<String> searchAdapter;
    private List<String> searchAutocompleteList;
    private Map<String, LatLng> searchResultsMap;
    private LatLng latLngCurrentPosition;
    private LatLng latLngDeparture;
    private LatLng latLngDestination;
    private ImageButton buttonByWhatTaxi;
    private ImageButton buttonByWhatCar;
    private ImageButton buttonByWhatOnFoot;
    private Button buttonPreparationFirst;
    private Button buttonPreparationSecond;
    private Button buttonPreparationThird;
    private int preparationTimeSelected = PREPARATION_FIRST_OPT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        showHelpOnFirstRun();
        initTomTomServices();
        initToolbarSettings();
        initSearchFieldsWithDefaultValues();
        initWhereSection();
        initByWhenSection();
        initByWhatSection();
        initPreparationSection();
        initStartSection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetDaysInArriveAt();
        PermissionChecker checker = AndroidPermissionChecker.createLocationChecker(this);
        if(!checker.ifNotAllPermissionGranted()) {
            locationSource.activate();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (latLngCurrentPosition == null) {
            latLngCurrentPosition = new LatLng(location);
            locationSource.deactivate();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int selectedItem = item.getItemId();
        if (selectedItem == R.id.toolbar_menu_help) {
            showHelpActivity();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public Calendar getCalArriveAt() {
        return calArriveAt;
    }

    private void initTomTomServices() {
        searchApi = OnlineSearchApi.create(this);
    }

    private void initToolbarSettings() {
        Toolbar toolbar = findViewById(R.id.custom_toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayShowTitleEnabled(false);
        }
    }

    private void initSearchFieldsWithDefaultValues() {
        atvDepartureLocation = findViewById(R.id.atv_main_departure_location);
        atvDestinationLocation = findViewById(R.id.atv_main_destination_location);
        initLocationSource();
        initDepartureWithDefaultValue();
        initDestinationWithDefaultValue();
    }

    private void initLocationSource() {
        PermissionChecker permissionChecker = AndroidPermissionChecker.createLocationChecker(this);
        if(permissionChecker.ifNotAllPermissionGranted()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_LOCATION);
        }
        locationSource = new FusedLocationSource(this, LocationRequest.create());
        locationSource.addLocationUpdateListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.length >= 2 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                locationSource.activate();
            } else {
                Toast.makeText(this, R.string.location_permissions_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initDepartureWithDefaultValue() {
        latLngDeparture = DEFAULT_DEPARTURE_LATLNG;
        setAddressForLocation(latLngDeparture, atvDepartureLocation);
    }

    private void initDestinationWithDefaultValue() {
        latLngDestination = DEFAULT_DESTINATION_LATLNG;
        setAddressForLocation(latLngDestination, atvDestinationLocation);
    }

    private void showHelpOnFirstRun() {
        String sharedPreferenceName = getString(R.string.shared_preference_name);
        String sharedPreferenceIsFirstRun = getString(R.string.shared_preference_first_run);
        boolean isFirstRun = getSharedPreferences(sharedPreferenceName, MODE_PRIVATE)
                .getBoolean(sharedPreferenceIsFirstRun, true);
        if (isFirstRun) {
            showHelpActivity();
            getSharedPreferences(sharedPreferenceName, MODE_PRIVATE).edit()
                    .putBoolean(sharedPreferenceIsFirstRun, false).apply();
        }
    }

    private void initWhereSection() {
        searchAutocompleteList = new ArrayList<>();
        searchResultsMap = new HashMap<>();
        searchAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, searchAutocompleteList);
        ImageButton btnDepartureClear = findViewById(R.id.button_departure_clear);
        ImageButton btnDestinationClear = findViewById(R.id.button_destination_clear);

        setTextWatcherToAutoCompleteField(atvDepartureLocation, btnDepartureClear);
        setClearButtonToAutocompleteField(atvDepartureLocation, btnDepartureClear);
        setTextWatcherToAutoCompleteField(atvDestinationLocation, btnDestinationClear);
        setClearButtonToAutocompleteField(atvDestinationLocation, btnDestinationClear);
    }

    private void setTextWatcherToAutoCompleteField(final AutoCompleteTextView autoCompleteTextView, final ImageButton imageButton) {
        autoCompleteTextView.setAdapter(searchAdapter);
        autoCompleteTextView.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchTimerHandler.removeCallbacks(searchRunnable);
            }

            @Override
            public void afterTextChanged(final Editable s) {
                if (s.length() > 0) {
                    imageButton.setVisibility(View.VISIBLE);
                    if (s.length() >= AUTOCOMPLETE_SEARCH_THRESHOLD) {
                        searchRunnable = () -> searchAddress(s.toString(), autoCompleteTextView);
                        searchAdapter.clear();
                        searchTimerHandler.postDelayed(searchRunnable, AUTOCOMPLETE_SEARCH_DELAY_MILLIS);
                    }
                } else {
                    imageButton.setVisibility(View.INVISIBLE);
                }
            }
        });
        autoCompleteTextView.setOnItemClickListener((parent, view, position, id) -> {
            String item = (String) parent.getItemAtPosition(position);
            if (autoCompleteTextView == atvDepartureLocation) {
                latLngDeparture = searchResultsMap.get(item);
            } else if (autoCompleteTextView == atvDestinationLocation) {
                latLngDestination = searchResultsMap.get(item);
            }
            hideKeyboard(view);
        });
    }

    private void searchAddress(final String searchWord, final AutoCompleteTextView autoCompleteTextView) {
        searchApi.search(new FuzzySearchQueryBuilder(searchWord)
                .withLanguage(Locale.getDefault().toLanguageTag())
                .withTypeAhead(true)
                .withMinFuzzyLevel(SEARCH_FUZZY_LVL_MIN).build())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableSingleObserver<FuzzySearchResponse>() {
                    @Override
                    public void onSuccess(FuzzySearchResponse fuzzySearchResponse) {
                        if (!fuzzySearchResponse.getResults().isEmpty()) {
                            searchAutocompleteList.clear();
                            searchResultsMap.clear();
                            if (autoCompleteTextView == atvDepartureLocation && latLngCurrentPosition != null) {
                                String currentLocationTitle = getString(R.string.main_current_position);
                                searchAutocompleteList.add(currentLocationTitle);
                                searchResultsMap.put(currentLocationTitle, latLngCurrentPosition);
                            }
                            for (FuzzySearchResult result : fuzzySearchResponse.getResults()) {
                                String addressString = result.getAddress().getFreeformAddress();
                                searchAutocompleteList.add(addressString);
                                searchResultsMap.put(addressString, result.getPosition());
                            }
                            searchAdapter.clear();
                            searchAdapter.addAll(searchAutocompleteList);
                            searchAdapter.getFilter().filter("");
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(MainActivity.this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setAddressForLocation(LatLng location, final AutoCompleteTextView autoCompleteTextView) {
        searchApi.reverseGeocoding(new ReverseGeocoderSearchQueryBuilder(location.getLatitude(), location.getLongitude()).build())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableSingleObserver<ReverseGeocoderSearchResponse>() {
                    @Override
                    public void onSuccess(ReverseGeocoderSearchResponse reverseGeocoderSearchResponse) {
                        List addressesList = reverseGeocoderSearchResponse.getAddresses();
                        if (!addressesList.isEmpty()) {
                            String address = ((ReverseGeocoderFullAddress) addressesList.get(0)).getAddress().getFreeformAddress();
                            autoCompleteTextView.setText(address);
                            autoCompleteTextView.dismissDropDown();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(MainActivity.this, getString(R.string.toast_error_message_error_getting_location, e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
                        Timber.e(e, getString(R.string.toast_error_message_error_getting_location, e.getLocalizedMessage()));
                    }
                });
    }

    private void setClearButtonToAutocompleteField(final AutoCompleteTextView autoCompleteTextView, final ImageButton imageButton) {
        imageButton.setOnClickListener(v -> {
            autoCompleteTextView.setText("");
            autoCompleteTextView.requestFocus();
            imageButton.setVisibility(View.GONE);
        });
    }

    private void initByWhenSection() {
        setArriveAtCalendar();
        textViewArriveAtHour = findViewById(R.id.text_view_main_arrive_at_hour);
        textViewArriveAtAmPm = findViewById(R.id.text_view_main_arrive_at_am_pm);
        setTimerDisplay();
        textViewArriveAtHour.setOnClickListener(v -> {
            DialogFragment timePickerFragment = new TimePickerFragment();
            timePickerFragment.show(getSupportFragmentManager(), TIME_PICKER_DIALOG_TAG);
        });
    }

    private void setArriveAtCalendar() {
        calArriveAt = Calendar.getInstance();
        calArriveAt.add(Calendar.HOUR, MainActivity.ARRIVE_TIME_AHEAD_HOURS);
    }

    public void setTimerDisplay() {
        String tvArriveAtHourString = (String) DateFormat.format(getUserPreferredHourPattern(), calArriveAt.getTimeInMillis());
        textViewArriveAtHour.setText(tvArriveAtHourString);
        setTvArriveAtAmPm(DateFormat.is24HourFormat(getApplicationContext()), calArriveAt.get(Calendar.AM_PM));
    }

    private String getUserPreferredHourPattern() {
        return DateFormat.is24HourFormat(getApplicationContext()) ? TIME_24H_FORMAT : TIME_12H_FORMAT;
    }

    private void setTvArriveAtAmPm(boolean is24HourFormat, int indicator) {
        if (is24HourFormat) {
            textViewArriveAtAmPm.setVisibility(View.INVISIBLE);
        } else {
            textViewArriveAtAmPm.setVisibility(View.VISIBLE);
            String strAmPm = (indicator == Calendar.AM) ? getString(R.string.main_am_value) : getString(R.string.main_pm_value);
            textViewArriveAtAmPm.setText(strAmPm);
        }
    }

    private void initByWhatSection() {
        buttonByWhatCar = findViewById(R.id.button_main_car);
        buttonByWhatTaxi = findViewById(R.id.button_main_taxi);
        buttonByWhatOnFoot = findViewById(R.id.button_main_on_foot);
        buttonByWhatCar.setSelected(true);

        buttonByWhatCar.setOnClickListener(setByWhatButtonListener(TravelMode.CAR));
        buttonByWhatTaxi.setOnClickListener(setByWhatButtonListener(TravelMode.TAXI));
        buttonByWhatOnFoot.setOnClickListener(setByWhatButtonListener(TravelMode.PEDESTRIAN));
    }

    private View.OnClickListener setByWhatButtonListener(final TravelMode travelMode) {
        return v -> {
            deselectByWhatButtons();
            v.setSelected(true);
            travelModeSelected = travelMode;
        };
    }

    private void deselectByWhatButtons() {
        buttonByWhatTaxi.setSelected(false);
        buttonByWhatOnFoot.setSelected(false);
        buttonByWhatCar.setSelected(false);
    }

    private void initPreparationSection() {
        buttonPreparationFirst = findViewById(R.id.button_main_preparation_first);
        buttonPreparationSecond = findViewById(R.id.button_main_preparation_second);
        buttonPreparationThird = findViewById(R.id.button_main_preparation_third);
        deselectPreparationButtons();
        selectPreparationButton(buttonPreparationFirst);

        buttonPreparationFirst.setOnClickListener(setPreparationButtonListener(PREPARATION_FIRST_OPT));
        buttonPreparationSecond.setOnClickListener(setPreparationButtonListener(PREPARATION_SECOND_OPT));
        buttonPreparationThird.setOnClickListener(setPreparationButtonListener(PREPARATION_THIRD_OPT));
    }

    private View.OnClickListener setPreparationButtonListener(final int preparationTimeInMinutes) {
        return v -> {
            deselectPreparationButtons();
            selectPreparationButton(v);
            preparationTimeSelected = preparationTimeInMinutes;
        };
    }

    private void deselectPreparationButtons() {
        int elevationButtonNormal = (int) getResources().getDimension(R.dimen.main_elevation_button_normal);
        buttonPreparationFirst.setSelected(false);
        buttonPreparationFirst.setElevation(elevationButtonNormal);
        buttonPreparationSecond.setSelected(false);
        buttonPreparationSecond.setElevation(elevationButtonNormal);
        buttonPreparationThird.setSelected(false);
        buttonPreparationThird.setElevation(elevationButtonNormal);
    }

    private void selectPreparationButton(View preparationButton) {
        preparationButton.setSelected(true);
        int elevationButtonPressed = (int) getResources().getDimension(R.dimen.main_elevation_button_pressed);
        preparationButton.setElevation(elevationButtonPressed);
    }

    private void initStartSection() {
        Button buttonStart = findViewById(R.id.button_main_start);
        buttonStart.setOnClickListener(v -> {
            long currentTimeInMillis = getCurrentTimeInMillis();
            arrivalTimeInMillis = getArrivalTimeInMillis();

            if (departureFiledIsEmpty()) {
                initDepartureWithDefaultValue();
            } else if (destinationFieldIsEmpty()) {
                initDestinationWithDefaultValue();
            }

            if (currentTimeInMillis >= arrivalTimeInMillis) {
                calArriveAt.add(Calendar.DAY_OF_MONTH, 1);
                arrivalTimeInMillis = getArrivalTimeInMillis();
            }

            Intent intent = CountdownActivity.prepareIntent(MainActivity.this,
                    latLngDeparture,
                    latLngDestination,
                    travelModeSelected,
                    arrivalTimeInMillis,
                    preparationTimeSelected);
            startActivity(intent);
        });
    }

    private boolean textViewIsEmpty(AutoCompleteTextView textView) {
        return textView.getText().toString().isEmpty();
    }

    private boolean departureFiledIsEmpty() {
        return textViewIsEmpty(atvDepartureLocation);
    }

    private boolean destinationFieldIsEmpty() {
        return textViewIsEmpty(atvDestinationLocation);
    }

    private long getCurrentTimeInMillis() {
        Calendar calendar = Calendar.getInstance();
        return calendar.getTimeInMillis();
    }

    private long getArrivalTimeInMillis() {
        return calArriveAt.getTimeInMillis();
    }

    private void resetDaysInArriveAt() {
        Calendar calendar = Calendar.getInstance();
        calArriveAt.set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH));
    }

    private void hideKeyboard(View view) {
        InputMethodManager in = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (in != null) {
            in.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);
        }
    }

    private void showHelpActivity() {
        Intent helpIntent = new Intent(MainActivity.this, HelpActivity.class);
        startActivity(helpIntent);
    }

    private abstract class BaseTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }
    }
}
