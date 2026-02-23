/*
 * Copyright (C) 2025 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.widget;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class StopTimesWidgetConfigActivity extends AppCompatActivity {

    private static final String TAG = "StopTimesWidgetConfig";

    /** Pass these extras when launching from a stop screen to pre-fill the selected stop. */
    public static final String EXTRA_STOP_ID = "stop_id";
    public static final String EXTRA_STOP_NAME = "stop_name";

    /** Builds an intent to launch config with a stop pre-selected (e.g. from the stop screen). */
    public static Intent newIntent(Context context, int appWidgetId, String stopId, String stopName) {
        Intent intent = new Intent(context, StopTimesWidgetConfigActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.putExtra(EXTRA_STOP_ID, stopId);
        intent.putExtra(EXTRA_STOP_NAME, stopName);
        return intent;
    }

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private TextView selectedStopText;
    private EditText widgetNameInput;
    private Button selectStopButton;
    private Button saveButton;
    private TextView routesLoadingText;
    private ChipGroup chipGroup;

    private String selectedStopId = null;
    private String selectedStopName = null;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> mStopPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedStopId = result.getData().getStringExtra(StopPickerActivity.EXTRA_STOP_ID);
                    selectedStopName = result.getData().getStringExtra(StopPickerActivity.EXTRA_STOP_NAME);
                    Log.d(TAG, "stop selected: id=%s name=%s".formatted(selectedStopId, selectedStopName));
                    selectedStopText.setText(selectedStopName);
                    widgetNameInput.setText(selectedStopName);
                    updateSaveButton();
                    loadRoutesForStop(selectedStopId);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        // If user backs out, widget is not added
        setResult(RESULT_CANCELED);

        setContentView(R.layout.stop_times_widget_config);

        appWidgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        Log.d(TAG, "appWidgetId: [%d]".formatted(appWidgetId));

        // Pre-fill stop if launched from the stop screen
        selectedStopId = getIntent().getStringExtra(EXTRA_STOP_ID);
        selectedStopName = getIntent().getStringExtra(EXTRA_STOP_NAME);

        setupUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void setupUi() {
        selectedStopText = findViewById(R.id.selected_stop);
        widgetNameInput = findViewById(R.id.widget_name_input);
        selectStopButton = findViewById(R.id.select_stop);
        saveButton = findViewById(R.id.save_button);
        routesLoadingText = findViewById(R.id.routes_loading);
        chipGroup = findViewById(R.id.route_chips);

        if (selectedStopName != null) {
            selectedStopText.setText(selectedStopName);
            widgetNameInput.setText(selectedStopName);
        }

        selectStopButton.setOnClickListener(v ->
                mStopPickerLauncher.launch(new Intent(this, StopPickerActivity.class)));

        if (selectedStopId != null) {
            loadRoutesForStop(selectedStopId);
        }

        updateSaveButton();
        saveButton.setOnClickListener(v -> onSave());
    }

    private void loadRoutesForStop(String stopId) {
        chipGroup.removeAllViews();
        routesLoadingText.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            ObaArrivalInfoResponse response =
                    ObaArrivalInfoRequest.newRequest(this, stopId, 1440).call();

            // Collect unique routeId -> shortName in arrival order
            LinkedHashMap<String, String> routes = new LinkedHashMap<>();
            if (response != null && response.getArrivalInfo() != null) {
                for (ObaArrivalInfo info : response.getArrivalInfo()) {
                    routes.putIfAbsent(info.getRouteId(), info.getShortName());
                }
            }

            mainHandler.post(() -> populateRouteChips(routes));
        });
    }

    private void populateRouteChips(Map<String, String> routes) {
        routesLoadingText.setVisibility(View.GONE);
        chipGroup.removeAllViews();

        for (Map.Entry<String, String> entry : routes.entrySet()) {
            Chip chip = new Chip(this);
            chip.setText(entry.getValue());   // short name shown to user
            chip.setTag(entry.getKey());      // route ID stored as tag
            chip.setCheckable(true);
            chip.setChecked(true);            // all routes shown by default
            chipGroup.addView(chip);
        }
    }

    private void updateSaveButton() {
        saveButton.setEnabled(selectedStopId != null);
    }

    /** Returns the selected route IDs, or an empty set if all routes are selected. */
    private Set<String> getSelectedRouteIds() {
        Set<String> selected = new HashSet<>();
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            if (chip.isChecked()) {
                selected.add((String) chip.getTag());
            }
        }
        // Empty set means "all routes" — no need to store every ID explicitly
        if (selected.size() == chipGroup.getChildCount()) {
            return Collections.emptySet();
        }
        return selected;
    }

    private void onSave() {
        if (selectedStopId == null) return;

        String enteredName = widgetNameInput.getText().toString().trim();
        String widgetName = enteredName.isEmpty() ? selectedStopName : enteredName;
        WidgetConfig config = new WidgetConfig(selectedStopId, widgetName, getSelectedRouteIds());
        WidgetPrefs.saveConfig(this, appWidgetId, config);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        StopTimesWidget.renderWidget(this, appWidgetManager, appWidgetId);

        Log.d(TAG, "onSave: stopId=%s routes=%s".formatted(selectedStopId, config.getRoutes()));

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }
}