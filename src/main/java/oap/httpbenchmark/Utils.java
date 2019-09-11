package oap.httpbenchmark;

import org.joda.time.Period;
import org.joda.time.format.PeriodFormatterBuilder;

/**
 * Created by igor.petrenko on 09/11/2019.
 */
public class Utils {
    public static final int SOCKET_TIMEOUT_EXCEPTION = -2;
    public static final int CONNECT_TIMEOUT_EXCEPTION = -3;
    public static final int HTTP_HOST_CONNECT_EXCEPTION = -4;

    public static String durationToString(long duration) {
        var period = new Period(duration);
        var formatter = new PeriodFormatterBuilder()
                .appendHours().appendSuffix("h").appendSeparator(" ")
                .appendMinutes().appendSuffix("m").appendSeparator(" ")
                .appendSecondsWithOptionalMillis().appendSuffix("s").toFormatter();
        return formatter.print(period);
    }
}
