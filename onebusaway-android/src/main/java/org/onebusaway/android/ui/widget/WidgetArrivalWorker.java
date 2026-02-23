package org.onebusaway.android.ui.widget;

import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class WidgetArrivalWorker extends Worker {
    private static final String TAG = "DERP_WORKER";

    public WidgetArrivalWorker(@NonNull final Context context, @NonNull final WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(final Context context, final int widgetId) {
        Log.d(TAG, "enqueue widgetId: %d".formatted(widgetId));
        final Data data = new Data.Builder()
                .putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .build();

        final OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WidgetArrivalWorker.class)
                .setInputData(data)
                .build();

        WorkManager.getInstance(context).enqueue(request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "DOIN WORK!");
        int widgetId = getInputData().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        Log.d(TAG, "widgetId: %d".formatted(widgetId));

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return Result.failure();
        }

        WidgetConfig config = WidgetPrefs.loadConfig(getApplicationContext(), widgetId);

        if (config == null || config.getStopId() == null) {
            return Result.success();
        }

        Log.d(TAG, config.toString());

        ObaArrivalInfoResponse response = fetchArrivals(config.getStopId());

        if (response == null || response.getArrivalInfo() == null) {
            return Result.retry();
        }

        WidgetArrivalSnapshot snapshot = buildSnapshot(response, config.getRoutes());
        WidgetPrefs.saveSnapshot(getApplicationContext(), widgetId, snapshot);
        StopTimesWidget.updateArrivals(getApplicationContext(), widgetId);

        return Result.success();
    }

    private ObaArrivalInfoResponse fetchArrivals(final String stopId) {
        Log.d(TAG, "FETCHING ARRIVALS");
        int minutesAfter = 65;

        while (minutesAfter <= 1440) {
            ObaArrivalInfoRequest request =
                    ObaArrivalInfoRequest.newRequest(
                            getApplicationContext(),
                            stopId,
                            minutesAfter
                    );

            ObaArrivalInfoResponse response = request.call();

            if (response != null &&
                    response.getArrivalInfo() != null &&
                    response.getArrivalInfo().length > 0) {
                Log.d(TAG, "RESPONSE stopId " + response.getStop());
                for (final ObaArrivalInfo info : response.getArrivalInfo()) {
                    Log.d(TAG, info.toString());
                }
                return response;
            }

            minutesAfter += 60;
        }

        return null;
    }

    private static WidgetArrivalSnapshot buildSnapshot(final ObaArrivalInfoResponse response,
                                                       final Set<String> routeFilter) {
        long now = System.currentTimeMillis();

        final Map<String, List<ObaArrivalInfo>> routeArrivals = new LinkedHashMap<>();

        for (ObaArrivalInfo info : response.getArrivalInfo()) {
            if (routeFilter.isEmpty() || routeFilter.contains(info.getRouteId())) {
                routeArrivals.computeIfAbsent(info.getRouteId(), k -> new ArrayList<>()).add(info);
            }
        }

        final List<WidgetArrivalSnapshot.Route> routes = new ArrayList<>();

        for (Map.Entry<String, List<ObaArrivalInfo>> entry : routeArrivals.entrySet()) {
            final List<ObaArrivalInfo> arrivals = entry.getValue();
            // sort by soonest arrival
            arrivals.sort(Comparator.comparingLong(a -> {
                long t = a.getPredictedArrivalTime();
                return t > 0 ? t : a.getScheduledArrivalTime();
            }));

            routes.add(getRoute(entry, arrivals));
        }

        // sort routes by their soonest arrival
        routes.sort(Comparator.comparingLong(route -> {
            WidgetArrivalSnapshot.Arrival a = route.getArrivals().get(0);
            long t = a.getPredictedArrivalTimeMs();
            return t > 0 ? t : a.getScheduledArrivalTimeMs();
        }));

        return new WidgetArrivalSnapshot(now, routes);
    }

    private static WidgetArrivalSnapshot.Route getRoute(final Map.Entry<String, List<ObaArrivalInfo>> entry,
                                                        final List<ObaArrivalInfo> arrivals) {
        final String shortName = arrivals.get(0).getShortName();
        final List<WidgetArrivalSnapshot.Arrival> snapshotArrivals = new ArrayList<>();

        // limit 3 arrivals per route
        for (int i = 0; i < Math.min(arrivals.size(), 3); i++) {
            ObaArrivalInfo a = arrivals.get(i);
            snapshotArrivals.add(new WidgetArrivalSnapshot.Arrival(a.getPredictedArrivalTime(),
                    a.getScheduledArrivalTime()));
        }

        return new WidgetArrivalSnapshot.Route(entry.getKey(), shortName, snapshotArrivals);
    }
}
