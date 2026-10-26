package com.bankingsystem.app.util;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

// Границы календарного месяца для подсчета месячных расходов.
// Месяц считается в том же часовом поясе, что и переданное время
public final class MonthPeriodUtils {

    private MonthPeriodUtils() {
    }

    // начало месяца, в который попадает time (включительно)
    public static OffsetDateTime startOfMonth(OffsetDateTime time) {
        return time.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    }

    // начало следующего месяца (не включительно)
    public static OffsetDateTime startOfNextMonth(OffsetDateTime time) {
        return startOfMonth(time).plusMonths(1);
    }
}
