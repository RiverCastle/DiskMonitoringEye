package com.monitoring.disk.util;

import java.time.format.DateTimeFormatter;

public final class MonitoringConstants {

    public static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MonitoringConstants() {}
}
