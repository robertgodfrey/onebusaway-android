package org.onebusaway.android.ui.widget;

import org.onebusaway.android.R;

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

import java.util.List;
import java.util.concurrent.TimeUnit;

public class StopTimesWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH_WIDGET = "org.onebusaway.android.ui.ACTION_REFRESH_WIDGET";
    public static final String ACTION_UPDATE_RELATIVE_TIMES = "org.onebusaway.android.ui.ACTION_UPDATE_WIDGET_RELATIVE_TIMES";
    private static final String TAG = "StopTimesWidget";

    private static final int[] ROUTE_TITLE_IDS = {
            R.id.route_1_name,
            R.id.route_2_name,
            R.id.route_3_name
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
            Intent intent = new Intent(context, StopTimesWidget.class);
            intent.setAction(ACTION_UPDATE_RELATIVE_TIMES);

            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            alarmManager.cancel(pi);

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
        applySizeVisibility(views, minWidthDp);

        // --- Stop / arrivals ---
        WidgetConfig widgetConfig = WidgetPrefs.loadConfig(context, appWidgetId);
        if (widgetConfig == null) {
            views.setTextViewText(R.id.title, "Not configured");
        } else {
            views.setTextViewText(R.id.stop_times_widget_title, widgetConfig.getWidgetName());
            WidgetArrivalWorker.enqueue(context, appWidgetId);
        }

        bindRefreshIntent(context, views, appWidgetId);
        scheduleUpdateRelativeTimes(context, appWidgetId);

        appWidgetManager.updateAppWidget(appWidgetId, views);
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
    }

    private static void applySizeVisibility(final RemoteViews views, final int minWidthDp) {
        boolean showEtas = minWidthDp >= 180;

        int etaVisibility = showEtas ? View.VISIBLE : View.GONE;

        // Route 1
        views.setViewVisibility(R.id.route_1_eta_2, etaVisibility);
        views.setViewVisibility(R.id.route_1_eta_3, etaVisibility);

        // Route 2
        views.setViewVisibility(R.id.route_2_eta_2, etaVisibility);
        views.setViewVisibility(R.id.route_2_eta_3, etaVisibility);
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

        alarmManager.setRepeating(
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
                // Hide entire unused route row
                views.setViewVisibility(ROUTE_TITLE_IDS[rowIndex], View.GONE);
                for (int etaId : ETA_IDS[rowIndex]) {
                    views.setViewVisibility(etaId, View.GONE);
                }
                continue;
            }

            WidgetArrivalSnapshot.Route route = routes.get(rowIndex);

            views.setTextViewText(ROUTE_TITLE_IDS[rowIndex], route.getShortName());
            views.setViewVisibility(ROUTE_TITLE_IDS[rowIndex], View.VISIBLE);

            // Bind ETAs
            for (int etaIndex = 0; etaIndex < ETA_IDS.length; etaIndex++) {
                int etaViewId = ETA_IDS[rowIndex][etaIndex];

                if (etaIndex >= route.getArrivals().size()) {
                    views.setViewVisibility(etaViewId, View.GONE);
                    continue;
                }

                final WidgetArrivalSnapshot.Arrival arrival = route.getArrivals().get(etaIndex);

                long arrivalTime = arrival.getPredictedArrivalTimeMs() > 0
                        ? arrival.getPredictedArrivalTimeMs()
                        : arrival.getScheduledArrivalTimeMs();

                final String etaText = formatMinutesAway(arrivalTime);
                final int backgroundColor = getEtaBackgroundResource(arrival.getPredictedArrivalTimeMs(),
                        arrival.getScheduledArrivalTimeMs());

                views.setTextViewText(etaViewId, etaText);
                views.setInt(etaViewId, "setBackgroundResource", backgroundColor);
                views.setViewVisibility(etaViewId, View.VISIBLE);
            }
        }
        CharSequence relative = DateUtils.getRelativeTimeSpanString(
                arrivalSnapshot.getFetchedAtMs(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
        );

        views.setTextViewText(R.id.last_updated, "Updated %s".formatted(relative)); // todo localize

        bindRefreshIntent(context, views, widgetId);

        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views);
    }

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
