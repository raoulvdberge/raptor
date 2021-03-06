package com.raoulvdberge.raptor.util;

import java.time.LocalDateTime;

public class TimeUtil {
    public static LocalDateTime time(int hour, int minute) {
        return LocalDateTime.of(2019, 8, 27, hour, minute, 0, 0);
    }

    public static LocalDateTime nullTime() {
        return LocalDateTime.MIN;
    }
}
