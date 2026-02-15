package org.onebusaway.android.ui.widget;

import java.util.List;

/**
 * Arrival data fetched from OBA API to be persisted in SharedPrefs. This is saved so we can update
 * the widget periodically with a new relative time ("5 min" -> "4 min" -> "3 min" etc.) without
 * making additional API calls.
 */
public final class WidgetArrivalSnapshot {
    private final long fetchedAtMs;
    private final List<Route> routes;

    public WidgetArrivalSnapshot(final long fetchedAtMs,
                                 final List<Route> routes) {
        this.fetchedAtMs = fetchedAtMs;
        this.routes = routes;
    }

    public long getFetchedAtMs() {
        return fetchedAtMs;
    }

    public List<Route> getRoutes() {
        return routes;
    }

    public static final class Route {

        private final String routeId; // TODO do we need this?
        private final String shortName;
        private final List<Arrival> arrivals;

        public Route(final String routeId, final String shortName, final List<Arrival> arrivals) {
            this.routeId = routeId;
            this.shortName = shortName;
            this.arrivals = arrivals;
        }

        public String getRouteId() {
            return routeId;
        }

        public String getShortName() {
            return shortName;
        }

        public List<Arrival> getArrivals() {
            return arrivals;
        }
    }

    public static final class Arrival {

        /** Unix ms, or 0 if unavailable */
        private final long predictedArrivalTimeMs;

        /** Unix ms (always present) */
        private final long scheduledArrivalTimeMs;

        public Arrival(final long predictedArrivalTimeMs, final long scheduledArrivalTimeMs) {
            this.predictedArrivalTimeMs = predictedArrivalTimeMs;
            this.scheduledArrivalTimeMs = scheduledArrivalTimeMs;
        }

        public long getPredictedArrivalTimeMs() {
            return predictedArrivalTimeMs;
        }

        public long getScheduledArrivalTimeMs() {
            return scheduledArrivalTimeMs;
        }

        /** Convenience: best arrival time */
        public long getEffectiveArrivalTimeMs() {
            return predictedArrivalTimeMs > 0 ? predictedArrivalTimeMs : scheduledArrivalTimeMs;
        }
    }
}