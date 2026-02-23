package org.onebusaway.android.ui.widget;

import org.onebusaway.android.R;
import org.onebusaway.android.ui.ArrivalsListActivity;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class StopTimesWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH_WIDGET = "org.onebusaway.android.ui.ACTION_REFRESH_WIDGET";
    public static final String ACTION_UPDATE_RELATIVE_TIMES = "org.onebusaway.android.ui.ACTION_UPDATE_WIDGET_RELATIVE_TIMES";
    public static final String ACTION_OPEN_CONFIG = "org.onebusaway.android.ui.ACTION_OPEN_WIDGET_CONFIG";
    public static final String EXTRA_STOP_ID = "stop_id";
    public static final String EXTRA_STOP_NAME = "stop_name";
    private static final String TAG = "StopTimesWidget";

    private static final int[] ROUTE_TITLE_IDS = {
            R.id.route_1_name,
            R.id.route_2_name,
            R.id.route_3_name
    };

    private static final int[] ROW_IDS = {
            R.id.route_1,
            R.id.route_2,
            R.id.route_3
    };

    private static final int[][] ETA_IDS = {
            {
                    R.id.route_1_eta_1,
                    R.id.route_1_eta_2,
                    R.id.route_1_eta_3
            },
            {
                    R.id.route_2_eta_1,
                    R.id.route_2_eta_2,
                    R.id.route_2_eta_3
            },
            {
                    R.id.route_3_eta_1,
                    R.id.route_3_eta_2,
                    R.id.route_3_eta_3
            }
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        Log.d(TAG, "onReceive!");

        if (ACTION_REFRESH_WIDGET.equals(intent.getAction())) {
            Log.d(TAG, "action is refresh widget!");
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            renderWidget(context, mgr, widgetId);
        } else if (ACTION_UPDATE_RELATIVE_TIMES.equals(intent.getAction())) {
            Log.d(TAG, "action is refresh footer");
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            updateArrivals(context, widgetId);
        } else if (ACTION_OPEN_CONFIG.equals(intent.getAction())) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            String stopId = intent.getStringExtra(EXTRA_STOP_ID);
            String stopName = intent.getStringExtra(EXTRA_STOP_NAME);
            Log.d(TAG, "action is open config, widgetId=%d stopId=%s".formatted(widgetId, stopId));
            Intent configIntent = StopTimesWidgetConfigActivity.newIntent(context, widgetId, stopId, stopName);
            configIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(configIntent);
        }
    }

    @Override
    public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
        Log.d(TAG, "onUpdate!");
        // There may be multiple widgets active, so update all of them
        for (final int appWidgetId : appWidgetIds) {
            renderWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        for (int id : appWidgetIds) {
            Intent relativeTimesIntent = new Intent(context, StopTimesWidget.class);
            relativeTimesIntent.setAction(ACTION_UPDATE_RELATIVE_TIMES);
            alarmManager.cancel(PendingIntent.getBroadcast(
                    context, id, relativeTimesIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            Intent apiRefreshIntent = new Intent(context, StopTimesWidget.class);
            apiRefreshIntent.setAction(ACTION_REFRESH_WIDGET);
            apiRefreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            alarmManager.cancel(PendingIntent.getBroadcast(
                    context, id, apiRefreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            WidgetPrefs.delete(context, id);
        }
    }

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context,
                                          AppWidgetManager appWidgetManager,
                                          int appWidgetId,
                                          Bundle newOptions) {
        renderWidget(context, appWidgetManager, appWidgetId);
    }

    static void renderWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.stop_times_widget);
        Log.d(TAG, "renderWidget widgetId: %d".formatted(appWidgetId));

        // --- Size handling ---
        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
        int minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        applySizeVisibility(views, minWidthDp, minHeightDp);

        // --- Stop / arrivals ---
        WidgetConfig widgetConfig = WidgetPrefs.loadConfig(context, appWidgetId);
        if (widgetConfig == null) {
            views.setTextViewText(R.id.title, "Not configured");
        } else {
            views.setTextViewText(R.id.stop_times_widget_title, widgetConfig.getWidgetName());
            bindStopIntent(context, views, appWidgetId, widgetConfig.getStopId());
            applyLoadingState(views);
            WidgetArrivalWorker.enqueue(context, appWidgetId);
        }

        bindRefreshIntent(context, views, appWidgetId);
        scheduleUpdateRelativeTimes(context, appWidgetId);
        schedulePeriodicApiRefresh(context, appWidgetId);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static void bindStopIntent(Context context, RemoteViews views, int appWidgetId, String stopId) {
        Intent intent = new ArrivalsListActivity.Builder(context, stopId).getIntent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
    }

    private static void applyLoadingState(RemoteViews views) {
        views.setViewVisibility(R.id.loading, View.VISIBLE);
        for (int rowId : ROW_IDS) {
            views.setViewVisibility(rowId, View.GONE);
        }
    }

    private static void bindRefreshIntent(Context context, RemoteViews views, int appWidgetId) {
        Log.d(TAG, "Binding refresh intent...");
        Intent refreshIntent = new Intent(context, StopTimesWidget.class);
        refreshIntent.setAction(ACTION_REFRESH_WIDGET);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(R.id.refresh, refreshPendingIntent);
        views.setOnClickPendingIntent(R.id.refresh_header, refreshPendingIntent);
    }

    private static void applySizeVisibility(final RemoteViews views, final int minWidthDp, final int minHeightDp) {
        // Hide 2nd and 3rd ETA columns for all routes when too narrow
        if (minWidthDp < 300) {
            for (int[] rowEtas : ETA_IDS) {
                views.setViewVisibility(rowEtas[2], View.GONE); // 3rd ETA col
            }
        }
        if (minWidthDp < 180) {
            for (int[] rowEtas : ETA_IDS) {
                views.setViewVisibility(rowEtas[1], View.GONE); // 2nd ETA col
            }
        }

        // Hide bottom rows when too short
        if (minHeightDp < 150) {
            views.setViewVisibility(R.id.route_3, View.GONE);
        }
        if (minHeightDp < 120) {
            views.setViewVisibility(R.id.route_2, View.GONE);
        }

        // At the smallest height, hide the footer and swap the OBA logo for a refresh button in the header
        if (minHeightDp < 100) {
            views.setViewVisibility(R.id.footer, View.GONE);
            views.setViewVisibility(R.id.oba_logo, View.GONE);
            views.setViewVisibility(R.id.refresh_header, View.VISIBLE);
        }
    }

    static void scheduleUpdateRelativeTimes(Context context, int appWidgetId) {
        Log.d(TAG, "Scheduling update relative times...");
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, StopTimesWidget.class);
        intent.setAction(ACTION_UPDATE_RELATIVE_TIMES);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long interval = TimeUnit.MINUTES.toMillis(1);

        alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + interval,
                interval,
                pi
        );
    }

    static void schedulePeriodicApiRefresh(Context context, int appWidgetId) {
        Log.d(TAG, "Scheduling periodic API refresh...");
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, StopTimesWidget.class);
        intent.setAction(ACTION_REFRESH_WIDGET);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long interval = TimeUnit.MINUTES.toMillis(5);

        alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + interval,
                interval,
                pi
        );
    }

    static void updateArrivals(Context context, int widgetId) {
        Log.d(TAG, "updateArrivals");

        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.stop_times_widget);
        final WidgetArrivalSnapshot arrivalSnapshot = WidgetPrefs.loadSnapshot(context, widgetId);
        final WidgetConfig config = WidgetPrefs.loadConfig(context, widgetId);

        if (config != null) {
            views.setTextViewText(R.id.stop_times_widget_title, config.getWidgetName());
        }

        if (arrivalSnapshot == null) {
            return;
        }
        
        final List<WidgetArrivalSnapshot.Route> routes = arrivalSnapshot.getRoutes();

        for (int rowIndex = 0; rowIndex < ROUTE_TITLE_IDS.length; rowIndex++) {

            if (rowIndex >= routes.size()) {
                views.setViewVisibility(ROW_IDS[rowIndex], View.GONE);
                continue;
            }

            views.setViewVisibility(ROW_IDS[rowIndex], View.VISIBLE);

            WidgetArrivalSnapshot.Route route = routes.get(rowIndex);

            views.setTextViewText(ROUTE_TITLE_IDS[rowIndex], route.getShortName());
            views.setViewVisibility(ROUTE_TITLE_IDS[rowIndex], View.VISIBLE);

            // Filter out arrivals that are more than 2 minutes in the past
            final List<WidgetArrivalSnapshot.Arrival> validArrivals = new ArrayList<>();
            for (WidgetArrivalSnapshot.Arrival a : route.getArrivals()) {
                if (!isStale(a.getEffectiveArrivalTimeMs())) {
                    validArrivals.add(a);
                }
            }

            final int[] etaViewIds = ETA_IDS[rowIndex];

            if (validArrivals.isEmpty()) {
                views.setTextViewText(etaViewIds[0], "N/A"); // todo localize
                views.setInt(etaViewIds[0], "setBackgroundResource", R.drawable.widget_eta_bg_scheduled);
                views.setViewVisibility(etaViewIds[0], View.VISIBLE);
                for (int i = 1; i < etaViewIds.length; i++) {
                    views.setViewVisibility(etaViewIds[i], View.GONE);
                }
            } else {
                for (int etaIndex = 0; etaIndex < etaViewIds.length; etaIndex++) {
                    int etaViewId = etaViewIds[etaIndex];

                    if (etaIndex >= validArrivals.size()) {
                        views.setViewVisibility(etaViewId, View.GONE);
                        continue;
                    }

                    final WidgetArrivalSnapshot.Arrival arrival = validArrivals.get(etaIndex);
                    views.setTextViewText(etaViewId, formatMinutesAway(arrival.getEffectiveArrivalTimeMs()));
                    views.setInt(etaViewId, "setBackgroundResource", getEtaBackgroundResource(
                            arrival.getPredictedArrivalTimeMs(), arrival.getScheduledArrivalTimeMs()));
                    views.setViewVisibility(etaViewId, View.VISIBLE);
                }
            }
        }
        CharSequence relative = DateUtils.getRelativeTimeSpanString(
                arrivalSnapshot.getFetchedAtMs(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
        );

        views.setTextViewText(R.id.last_updated, "Updated %s".formatted(relative)); // todo localize

        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        Bundle options = mgr.getAppWidgetOptions(widgetId);
        int minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        applySizeVisibility(views, minWidthDp, minHeightDp);

        if (config != null) {
            bindStopIntent(context, views, widgetId, config.getStopId());
        }
        bindRefreshIntent(context, views, widgetId);

        mgr.updateAppWidget(widgetId, views);
    }

    static boolean isStale(long arrivalTimeMs) {
        return arrivalTimeMs < System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2);
    }

    // TODO match this logic with the logic in the arrival list (doesn't match perfectly - use their method?)
    static String formatMinutesAway(long arrivalTimeMs) {
        long now = System.currentTimeMillis();
        long diffMs = arrivalTimeMs - now;

        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs);

        if (minutes == 0) { // todo localize
            return "Now";
        } else {
            return "%d min".formatted(minutes);
        }
    }

    static int getEtaBackgroundResource(final long predictedArrivalTime, final long scheduledArrivalTime) {
        if (predictedArrivalTime <= 0) {
            return R.drawable.widget_eta_bg_scheduled;
        }
        long diffMinutes = TimeUnit.MILLISECONDS.toMinutes(predictedArrivalTime - scheduledArrivalTime);

        if (diffMinutes >= 2) {
            return R.drawable.widget_eta_bg_late;
        }
        if (diffMinutes <= -2) {
            return R.drawable.widget_eta_bg_early;
        }
        return R.drawable.widget_eta_bg_on_time;
    }
}
