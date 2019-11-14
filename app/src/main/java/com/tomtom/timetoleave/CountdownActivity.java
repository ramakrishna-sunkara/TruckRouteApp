package com.tomtom.timetoleave;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.tomtom.online.sdk.common.location.LatLng;
import com.tomtom.online.sdk.map.Icon;
import com.tomtom.online.sdk.map.MapFragment;
import com.tomtom.online.sdk.map.OnMapReadyCallback;
import com.tomtom.online.sdk.map.RouteBuilder;
import com.tomtom.online.sdk.map.TomtomMap;
import com.tomtom.online.sdk.routing.OnlineRoutingApi;
import com.tomtom.online.sdk.routing.RoutingApi;
import com.tomtom.online.sdk.routing.data.FullRoute;
import com.tomtom.online.sdk.routing.data.RouteQuery;
import com.tomtom.online.sdk.routing.data.RouteQueryBuilder;
import com.tomtom.online.sdk.routing.data.RouteResponse;
import com.tomtom.online.sdk.routing.data.RouteType;
import com.tomtom.online.sdk.routing.data.TravelMode;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

public class CountdownActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String BUNDLE_SETTINGS = "SETTINGS";
    private static final String BUNDLE_DEPARTURE_LAT = "DEPARTURE_LAT";
    private static final String BUNDLE_DEPARTURE_LNG = "DEPARTURE_LNG";
    private static final String BUNDLE_DESTINATION_LAT = "DESTINATION_LAT";
    private static final String BUNDLE_DESTINATION_LNG = "DESTINATION_LNG";
    private static final String BUNDLE_BY_WHAT = "BY_WHAT";
    private static final String BUNDLE_ARRIVE_AT = "ARRIVE_AT";
    private static final String BUNDLE_PREPARATION_TIME = "PREPARATION_TIME";
    private static final String COUNTDOWN_MODE_PREPARATION = "countdown_mode_preparation";
    private static final String COUNTDOWN_MODE_FINISHED = "countdown_mode_finished";
    private static final int ONE_MINUTE_IN_MILLIS = 60000;
    private static final int ONE_SECOND_IN_MILLIS = 1000;
    private static final int ROUTE_RECALCULATION_DELAY = ONE_MINUTE_IN_MILLIS;

    private int preparationTime;
    private int previousTravelTime;
    private boolean isPreparationMode = false;
    private boolean isInPauseMode = false;
    private Date arriveAt;
    private TextView textViewCountDownTimerHour;
    private TextView textViewCountDownTimerMin;
    private TextView textViewCountDownTimerSec;
    private TextView textViewTravelTime;
    private CustomSnackbar infoSnackbar;
    private CustomSnackbar warningSnackbar;
    private AlertDialog dialogInfo;
    private AlertDialog dialogInProgress;
    private TomtomMap tomtomMap;
    private Icon departureIcon;
    private Icon destinationIcon;
    private TravelMode travelMode;
    private LatLng destination;
    private LatLng departure;
    private CountDownTimer countDownTimer;
    private final Handler timerHandler = new Handler();
    private RoutingApi routingApi;

    private final Runnable requestRouteRunnable = new Runnable() {
        @Override
        public void run() {
            requestRoute(departure, destination, travelMode, arriveAt);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_countdown);

        initTomTomServices();
        initToolbarSettings();
        Bundle settings = getIntent().getBundleExtra(BUNDLE_SETTINGS);
        initActivitySettings(settings);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isInPauseMode = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!dialogIsShowing(dialogInfo)) {
            isInPauseMode = false;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        timerHandler.removeCallbacks(requestRouteRunnable);
        countDownTimer.cancel();
    }

    @Override
    public void onMapReady(@NonNull TomtomMap tomtomMap) {
        this.tomtomMap = tomtomMap;
        this.tomtomMap.setMyLocationEnabled(true);
        this.tomtomMap.clear();
        showDialog(dialogInProgress);
        requestRoute(departure, destination, travelMode, arriveAt);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        this.tomtomMap.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void requestRoute(final LatLng departure, final LatLng destination, TravelMode byWhat, Date arriveAt) {
        if (!isInPauseMode) {
            RouteQuery routeQuery = new RouteQueryBuilder(departure, destination)
                    .withRouteType(RouteType.FASTEST)
                    .withConsiderTraffic(true)
                    .withTravelMode(byWhat)
                    .withArriveAt(arriveAt)
                    .build();

            routingApi.planRoute(routeQuery)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new DisposableSingleObserver<RouteResponse>() {
                        @Override
                        public void onSuccess(RouteResponse routeResponse) {
                            hideDialog(dialogInProgress);
                            if (routeResponse.hasResults()) {
                                FullRoute fullRoute = routeResponse.getRoutes().get(0);
                                int currentTravelTime = fullRoute.getSummary().getTravelTimeInSeconds();
                                if (previousTravelTime != currentTravelTime) {
                                    int travelDifference = previousTravelTime - currentTravelTime;
                                    if (previousTravelTime != 0) {
                                        showWarningSnackbar(prepareWarningMessage(travelDifference));
                                    }
                                    previousTravelTime = currentTravelTime;
                                    displayRouteOnMap(fullRoute.getCoordinates());
                                    setupCountDownTimer(fullRoute.getSummary().getDepartureTime());
                                } else {
                                    infoSnackbar.show();
                                }
                            }
                            timerHandler.removeCallbacks(requestRouteRunnable);
                            timerHandler.postDelayed(requestRouteRunnable, ROUTE_RECALCULATION_DELAY);
                        }

                        @Override
                        public void onError(Throwable e) {
                            hideDialog(dialogInProgress);
                            Toast.makeText(CountdownActivity.this, getString(R.string.toast_error_message_cannot_find_route), Toast.LENGTH_LONG).show();
                            CountdownActivity.this.finish();
                        }

                        private void setupCountDownTimer(Date departure) {
                            if (isCountdownTimerSet()) {
                                countDownTimer.cancel();
                            }
                            Date now = Calendar.getInstance().getTime();
                            final int preparationTimeMillis = preparationTime * ONE_MINUTE_IN_MILLIS;
                            long timeToLeave = departure.getTime() - now.getTime();
                            countDownTimer = new CountDownTimer(timeToLeave, ONE_SECOND_IN_MILLIS) {
                                public void onTick(long millisUntilFinished) {
                                    updateCountdownTimerTextViews(millisUntilFinished);
                                    if (!isPreparationMode && millisUntilFinished <= preparationTimeMillis) {
                                        isPreparationMode = true;
                                        setCountdownTimerColor(COUNTDOWN_MODE_PREPARATION);
                                        if (!isInPauseMode) {
                                            showPreparationInfoDialog();
                                        }
                                    }
                                }

                                public void onFinish() {
                                    timerHandler.removeCallbacks(requestRouteRunnable);
                                    setCountdownTimerColor(COUNTDOWN_MODE_FINISHED);
                                    if (!isInPauseMode) {
                                        createDialogWithCustomButtons();
                                    }
                                }
                            }.start();
                            textViewTravelTime.setText(getString(R.string.travel_time_text, formatTimeFromSecondsDisplayWithoutSeconds(previousTravelTime)));
                        }

                        private String prepareWarningMessage(int travelDifference) {
                            String travelTimeDifference = formatTimeFromSecondsDisplayWithSeconds(travelDifference);
                            return getString(R.string.dialog_recalculation_info, getTimeInfoWithPrefix(travelDifference, travelTimeDifference));
                        }

                        private void showWarningSnackbar(String warningMessage) {
                            warningSnackbar.setText(warningMessage);
                            warningSnackbar.show();
                        }

                        private boolean isCountdownTimerSet() {
                            return countDownTimer != null;
                        }

                        private String getTimeInfoWithPrefix(int travelDifference, String travelTimeDifference) {
                            String prefix = (travelDifference < 0) ? "-" : "+";
                            return prefix + travelTimeDifference;
                        }

                        private void createDialogWithCustomButtons() {
                            AlertDialog.Builder builder = new AlertDialog.Builder(CountdownActivity.this);
                            builder.setMessage(getString(R.string.dialog_time_to_leave))
                                    .setPositiveButton(R.string.dialog_on_my_way, (dialog, id) -> {
                                        hideDialog(dialogInfo);
                                        Intent intent = new Intent(CountdownActivity.this, SafeTravelsActivity.class);
                                        startActivity(intent);
                                    })
                                    .setNegativeButton(R.string.dialog_whatever, (dialog, id) -> createDialogWithCustomLayout());
                            hideDialog(dialogInfo);
                            dialogInfo = builder.create();
                            dialogInfo.setCanceledOnTouchOutside(false);
                            showDialog(dialogInfo);
                        }

                        private void createDialogWithCustomLayout() {
                            AlertDialog.Builder builder = new AlertDialog.Builder(CountdownActivity.this);
                            LayoutInflater inflater = getLayoutInflater();

                            builder.setView(inflater.inflate(R.layout.dialog_you_are_over_time, null))
                                    .setPositiveButton(R.string.dialog_next_time_ill_do_better, (dialog, id) -> {
                                        hideDialog(dialogInfo);
                                        Intent intent = new Intent(CountdownActivity.this, MainActivity.class);
                                        startActivity(intent);
                                    });
                            hideDialog(dialogInfo);
                            dialogInfo = builder.create();
                            dialogInfo.setCanceledOnTouchOutside(false);
                            showDialog(dialogInfo);
                        }

                        private void showPreparationInfoDialog() {
                            dialogInfo = createSimpleAlertDialog(getString(R.string.dialog_start_preparation_text, preparationTime));
                            showDialog(dialogInfo);
                        }

                        private AlertDialog createSimpleAlertDialog(String message) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(CountdownActivity.this);
                            builder.setMessage(message);
                            return builder.create();
                        }

                        private void setCountdownTimerColor(String state) {
                            int color;
                            switch (state) {
                                case COUNTDOWN_MODE_PREPARATION:
                                    color = R.color.color_countdown_mode_preparation;
                                    break;
                                case COUNTDOWN_MODE_FINISHED:
                                    color = R.color.color_countdown_mode_finished;
                                    break;
                                default:
                                    color = R.color.color_all_text;
                                    break;
                            }
                            int resolvedColor = ContextCompat.getColor(CountdownActivity.this, color);
                            textViewCountDownTimerHour.setTextColor(resolvedColor);
                            textViewCountDownTimerMin.setTextColor(resolvedColor);
                            textViewCountDownTimerSec.setTextColor(resolvedColor);
                        }

                        private void updateCountdownTimerTextViews(long millis) {
                            long hours = TimeUnit.MILLISECONDS.toHours(millis);
                            long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours);
                            long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes);
                            textViewCountDownTimerHour.setText(getString(R.string.countdown_timer_hour, hours));
                            textViewCountDownTimerMin.setText(getString(R.string.countdown_timer_min, minutes));
                            textViewCountDownTimerSec.setText(getString(R.string.countdown_timer_sec, seconds));
                        }

                        private void displayRouteOnMap(List<LatLng> coordinates) {
                            RouteBuilder routeBuilder = new RouteBuilder(coordinates)
                                    .startIcon(departureIcon)
                                    .endIcon(destinationIcon);
                            tomtomMap.clear();
                            tomtomMap.addRoute(routeBuilder);
                            tomtomMap.displayRoutesOverview();
                        }

                        private String formatTimeFromSecondsDisplayWithSeconds(long secondsTotal) {
                            return formatTimeFromSeconds(secondsTotal, true);
                        }

                        private String formatTimeFromSecondsDisplayWithoutSeconds(long secondsTotal) {
                            return formatTimeFromSeconds(secondsTotal, false);
                        }

                        private String formatTimeFromSeconds(long secondsTotal, boolean showSeconds) {
                            final String TIME_FORMAT_HOURS_MINUTES = "H'h' m'min'";
                            final String TIME_FORMAT_MINUTES = "m'min'";
                            final String TIME_FORMAT_SECONDS = " s'sec'";

                            long hours = TimeUnit.SECONDS.toHours(secondsTotal);
                            long minutes = TimeUnit.SECONDS.toMinutes(secondsTotal) - TimeUnit.HOURS.toMinutes(hours);
                            String timeFormat = "";

                            if (hours != 0) {
                                timeFormat = TIME_FORMAT_HOURS_MINUTES;
                            } else {
                                if (minutes != 0) {
                                    timeFormat = TIME_FORMAT_MINUTES;
                                }
                            }

                            if (showSeconds) {
                                timeFormat += TIME_FORMAT_SECONDS;
                            }
                            secondsTotal = Math.abs(secondsTotal);
                            return (String) DateFormat.format(timeFormat, TimeUnit.SECONDS.toMillis(secondsTotal));
                        }
                    });
        }
        else {
            timerHandler.removeCallbacks(requestRouteRunnable);
            timerHandler.postDelayed(requestRouteRunnable, ROUTE_RECALCULATION_DELAY);
        }
    }

    private void initTomTomServices() {
        routingApi = OnlineRoutingApi.create(getApplicationContext());
        MapFragment mapFragment = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        mapFragment.getAsyncMap(this);
    }

    private void initToolbarSettings() {
        Toolbar toolbar = findViewById(R.id.custom_toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
        }
    }

    private void initActivitySettings(Bundle settings) {
        textViewCountDownTimerHour = findViewById(R.id.text_view_countdown_timer_hour);
        textViewCountDownTimerMin = findViewById(R.id.text_view_countdown_timer_minute);
        textViewCountDownTimerSec = findViewById(R.id.text_view_countdown_timer_second);
        departureIcon = Icon.Factory.fromResources(CountdownActivity.this, R.drawable.ic_map_route_departure);
        destinationIcon = Icon.Factory.fromResources(CountdownActivity.this, R.drawable.ic_map_route_destination);
        ImageView imgTravelingMode = findViewById(R.id.img_countdown_by_what);
        textViewTravelTime = findViewById(R.id.text_countdown_travel_time);
        TextView textPreparation = findViewById(R.id.text_countdown_preparation);

        initBundleSettings(settings);
        imgTravelingMode.setImageResource(getTravelModeIcon(travelMode));
        textPreparation.setText(getString(R.string.preparation_indicator_info, preparationTime));
        previousTravelTime = 0;

        createWarningSnackBar();
        createInfoSnackBar();
        createDialogInProgress();
    }

    private void initBundleSettings(Bundle settings) {
        long arriveAtMillis = settings.getLong(BUNDLE_ARRIVE_AT);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(arriveAtMillis);
        arriveAt = calendar.getTime();
        departure = new LatLng(settings.getDouble(BUNDLE_DEPARTURE_LAT), settings.getDouble(BUNDLE_DEPARTURE_LNG));
        destination = new LatLng(settings.getDouble(BUNDLE_DESTINATION_LAT), settings.getDouble(BUNDLE_DESTINATION_LNG));
        travelMode = TravelMode.valueOf(settings.getString(BUNDLE_BY_WHAT).toUpperCase());
        preparationTime = settings.getInt(BUNDLE_PREPARATION_TIME);
    }

    private int getTravelModeIcon(TravelMode selectedTravelMode) {
        int iconResource;
        switch (selectedTravelMode) {
            case TAXI:
                iconResource = R.drawable.button_main_travel_mode_cab;
                break;
            case PEDESTRIAN:
                iconResource = R.drawable.button_main_travel_mode_by_foot;
                break;
            case CAR:
            default:
                iconResource = R.drawable.button_main_travel_mode_car;
                break;
        }
        return iconResource;
    }

    private void createWarningSnackBar() {
        ViewGroup view = findViewById(android.R.id.content);
        warningSnackbar = CustomSnackbar.make(view, CustomSnackbar.LENGTH_INDEFINITE, R.layout.snackbar_recalculation_warning);
        warningSnackbar.setAction(getString(R.string.button_ok), v -> warningSnackbar.dismiss());
        setCustomSnackbar(warningSnackbar);
    }

    private void createInfoSnackBar() {
        ViewGroup view = findViewById(android.R.id.content);
        infoSnackbar = CustomSnackbar.make(view, CustomSnackbar.LENGTH_LONG, R.layout.snackbar_recalculation_info);
        infoSnackbar.setText(getString(R.string.dialog_recalculation_no_changes));
        setCustomSnackbar(infoSnackbar);
    }

    private void createDialogInProgress() {
        AlertDialog.Builder builder = new AlertDialog.Builder(CountdownActivity.this);
        LayoutInflater inflater = getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.dialog_in_progress, null));
        dialogInProgress = builder.create();
        dialogInProgress.setCanceledOnTouchOutside(false);
    }

    private void setCustomSnackbar(CustomSnackbar snackbar) {
        int transparentColor = ContextCompat.getColor(CountdownActivity.this, R.color.transparent);
        snackbar.getView().setBackgroundColor(transparentColor);
        int paddingSnackbar = (int) getResources().getDimension(R.dimen.padding_snackbar);
        snackbar.getView().setPadding(paddingSnackbar, paddingSnackbar, paddingSnackbar, paddingSnackbar);
    }

    private void hideDialog(Dialog dialog) {
        if (dialogIsShowing(dialog)) {
            dialog.dismiss();
        }
    }

    private void showDialog(Dialog dialog) {
        if (!dialogIsShowing(dialog)) {
            dialog.show();
        }
    }

    private boolean dialogIsShowing(Dialog dialog) {
        return dialog != null && dialog.isShowing();
    }

    public static Intent prepareIntent(Context context, LatLng departure, LatLng destination, TravelMode strByWhat, long arriveAtMillis, int preparationTime) {
        Bundle settings = new Bundle();
        settings.putDouble(BUNDLE_DEPARTURE_LAT, departure.getLatitude());
        settings.putDouble(BUNDLE_DEPARTURE_LNG, departure.getLongitude());
        settings.putDouble(BUNDLE_DESTINATION_LAT, destination.getLatitude());
        settings.putDouble(BUNDLE_DESTINATION_LNG, destination.getLongitude());
        settings.putString(BUNDLE_BY_WHAT, strByWhat.toString());
        settings.putLong(BUNDLE_ARRIVE_AT, arriveAtMillis);
        settings.putInt(BUNDLE_PREPARATION_TIME, preparationTime);
        Intent intent = new Intent(context, CountdownActivity.class);
        intent.putExtra(BUNDLE_SETTINGS, settings);

        return intent;
    }
}
