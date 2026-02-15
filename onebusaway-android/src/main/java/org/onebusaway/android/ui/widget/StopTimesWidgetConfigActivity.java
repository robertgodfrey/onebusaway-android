package org.onebusaway.android.ui.widget;

import com.google.android.material.chip.ChipGroup;

import org.onebusaway.android.R;

import android.app.TimePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import androidx.appcompat.app.AppCompatActivity;

public class StopTimesWidgetConfigActivity extends AppCompatActivity {

    private static final String TAG = "StopTimesWidgetConfigActivity";

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private LinearLayout activeTimeRow;
    private TextView activeTimeValue;

    private LocalTime startTime = null;
    private LocalTime endTime = null;
    private String selectedStopId = null;
    private TextView selectedStopText;
    private Button selectStopButton;

    private final DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("h:mm a");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        // If user backs out, widget is not added
        setResult(RESULT_CANCELED);

        setContentView(R.layout.stop_times_widget_config);

        activeTimeRow = findViewById(R.id.active_time_row);
        activeTimeValue = findViewById(R.id.active_time_value);

        // default to "All day"
        activeTimeValue.setText("All day");

        Log.d(TAG, "appWidgetId: [%d]".formatted(appWidgetId));

        appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        Log.d(TAG, "new appWidgetId: [%d]".formatted(appWidgetId));

//        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
//            finish();
//            return;
//        }

        setupUi();
    }

    private void setupUi() {
        activeTimeRow.setOnClickListener(v -> pickStartTime());
        findViewById(R.id.save_button).setOnClickListener(v -> onSave());

        selectedStopText = findViewById(R.id.selected_stop);
        selectStopButton = findViewById(R.id.select_stop);

        selectStopButton.setOnClickListener(v -> {
            // launch stop selection UI (list/dialog)
//            Stop stop = showStopPickerDialog(); // your method
//            if (stop != null) {
//                selectedStopId = stop.getId();
//                selectedStopText.setText(stop.getName());
//            }
//            updateSaveButtonState();
        });

        ChipGroup chipGroup = findViewById(R.id.route_chips);

//        for (Route route : routesForStop) {
//            Chip chip = new Chip(this);
//            chip.setText(route.getShortName());
//            chip.setCheckable(true);
//            chip.setChecked(true); // default: all routes
//            chipGroup.addView(chip);
//        }

    }

    private void pickStartTime() {
        LocalTime initial = startTime != null ? startTime : LocalTime.of(0, 0);

        new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    startTime = LocalTime.of(hourOfDay, minute);
                    pickEndTime(); // immediately open end picker
                },
                initial.getHour(),
                initial.getMinute(),
                false // 12-hour format
        ).show();
    }

    private void pickEndTime() {
        LocalTime initial = endTime != null ? endTime : LocalTime.of(23, 59);

        new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    endTime = LocalTime.of(hourOfDay, minute);
                    updateActiveTimeLabel();
                },
                initial.getHour(),
                initial.getMinute(),
                false
        ).show();
    }

    private void updateActiveTimeLabel() {
        if (startTime != null && endTime != null) {
            // handle "All day" automatically
            if (startTime.equals(LocalTime.MIDNIGHT) && endTime.equals(LocalTime.of(23, 59))) {
                activeTimeValue.setText("All day");
            } else {
                activeTimeValue.setText(
                        displayFormatter.format(startTime) + " – " + displayFormatter.format(endTime)
                );
            }
        }
    }

    private void onSave() {
        WidgetConfig config = new WidgetConfig("1_19930", "SW Avalon Way & SW Yancy St (N)", Set.of(), new ActiveTime(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT));
        WidgetPrefs.saveConfig(this, appWidgetId, config);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        StopTimesWidget.renderWidget(this, appWidgetManager, appWidgetId);

        Log.d(TAG, "onSave!!!");

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }
}
