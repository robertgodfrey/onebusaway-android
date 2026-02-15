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
    private final ActiveTime activeTime;

    public WidgetConfig(final String stopId,
                        final String widgetName,
                        final Set<String> routes,
                        final ActiveTime activeTime) {
        this.stopId = stopId;
        this.widgetName = widgetName;
        this.routes = routes;
        this.activeTime = activeTime;
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

    public boolean isActiveNow() {
        return activeTime == null || activeTime.isActiveNow();
    }

    @NonNull
    @Override
    public String toString() {
        return "stopId: %s\nwidgetName: %s\nroutes: %s\nactive: %s".formatted(stopId, widgetName, routes, isActiveNow());
    }
}