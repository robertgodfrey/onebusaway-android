package org.onebusaway.android.ui.widget;

import com.google.gson.Gson;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

public class WidgetPrefs {
    private static final Gson GSON = new Gson();
    private static final String STOP_WIDGET_PREF_NAME = "stop_widgets";

    private WidgetPrefs() {}

    public static void saveConfig(final Context context, final int widgetId, final WidgetConfig config) {
        final String json = GSON.toJson(config);
        stopWidgetPrefs(context).edit()
                .putString(configKey(widgetId), json)
                .apply();
    }

    public static void saveSnapshot(final Context context, final int widgetId, final WidgetArrivalSnapshot snapshot) {
        final String json = GSON.toJson(snapshot);
        stopWidgetPrefs(context).edit()
                .putString(snapshotKey(widgetId), json)
                .apply();
    }

    @Nullable
    public static WidgetConfig loadConfig(final Context context, final int widgetId) {
        final String json = stopWidgetPrefs(context).getString(configKey(widgetId), null);
        return json != null
                ? GSON.fromJson(json, WidgetConfig.class)
                : null;
    }

    @Nullable
    public static WidgetArrivalSnapshot loadSnapshot(final Context context, final int widgetId) {
        final String json = stopWidgetPrefs(context).getString(snapshotKey(widgetId), null);
        return json != null
                ? GSON.fromJson(json, WidgetArrivalSnapshot.class)
                : null;
    }

    public static void delete(final Context context, final int widgetId) {
        stopWidgetPrefs(context).edit()
                .remove(configKey(widgetId))
                .remove(snapshotKey(widgetId))
                .apply();
    }

    private static SharedPreferences stopWidgetPrefs(final Context context) {
        return context.getSharedPreferences(STOP_WIDGET_PREF_NAME, Context.MODE_PRIVATE);
    }

    private static String configKey(final int widgetId) {
        return "widget_%d_config".formatted(widgetId);
    }

    private static String snapshotKey(final int widgetId) {
        return "widget_%d_snapshot".formatted(widgetId);
    }
}
