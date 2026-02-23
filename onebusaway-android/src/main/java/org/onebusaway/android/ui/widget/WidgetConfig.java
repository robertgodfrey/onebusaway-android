package org.onebusaway.android.ui.widget;

import java.util.Set;

import androidx.annotation.NonNull;

/**
 * User settings for widget
 */
public final class WidgetConfig {
    private final String stopId;
    private final String widgetName; // default is stop name but can be modified
    private final Set<String> routes;

    public WidgetConfig(final String stopId,
                        final String widgetName,
                        final Set<String> routes) {
        this.stopId = stopId;
        this.widgetName = widgetName;
        this.routes = routes;
    }

    public String getStopId() {
        return stopId;
    }

    public String getWidgetName() {
        return widgetName;
    }

    public Set<String> getRoutes() {
        return routes;
    }

    @NonNull
    @Override
    public String toString() {
        return "stopId: %s\nwidgetName: %s\nroutes: %s".formatted(stopId, widgetName, routes);
    }
}