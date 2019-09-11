package oap.httpbenchmark;

import org.joda.time.Period;
import org.joda.time.format.PeriodFormatterBuilder;

/**
 * Created by igor.petrenko on 09/11/2019.
 */
public class Utils {
    public static String durationToString(long duration) {
        var period = new Period(duration);
        var formatter = new PeriodFormatterBuilder()
                .appendHours().appendSuffix("h").appendSeparator(" ")
                .appendMinutes().appendSuffix("m").appendSeparator(" ")
                .appendSecondsWithOptionalMillis().appendSuffix("s").toFormatter();
        return formatter.print(period);
    }
}
