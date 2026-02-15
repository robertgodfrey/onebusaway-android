package org.onebusaway.android.ui.widget;

import java.time.LocalTime;

public final class ActiveTime {

    private final LocalTime start;
    private final LocalTime end;

    public ActiveTime(LocalTime start, LocalTime end) {
        this.start = start;
        this.end = end;
    }

    public boolean isActiveNow() {
        LocalTime now = LocalTime.now();

        // Normal range (e.g. 06:00 → 22:00)
        if (!start.isAfter(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }

        // Overnight range (e.g. 22:00 → 06:00)
        return !now.isBefore(start) || now.isBefore(end);
    }

    public String serialize() {
        return start.toString() + "/" + end.toString();
    }

    public static ActiveTime deserialize(String raw) {
        if (raw == null) return null;

        String[] parts = raw.split("/");
        if (parts.length != 2) return null;

        return new ActiveTime(
                LocalTime.parse(parts[0]),
                LocalTime.parse(parts[1])
        );
    }
}
