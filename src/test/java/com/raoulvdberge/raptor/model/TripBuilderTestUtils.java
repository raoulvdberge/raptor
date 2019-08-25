package com.raoulvdberge.raptor.model;

import java.time.LocalDateTime;

public class TripBuilderTestUtils {
    public static LocalDateTime time(int hour, int minute) {
        return LocalDateTime.now().withHour(hour).withMinute(minute);
    }

    public static LocalDateTime nullTime() {
        return LocalDateTime.MIN;
    }
}