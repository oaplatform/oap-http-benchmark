package oap.httpbenchmark;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by igor.petrenko on 09/10/2019.
 */
public class Statistics {
    public final AtomicLong count = new AtomicLong();
    public final AtomicLong warmup = new AtomicLong();
    public final ConcurrentHashMap<Integer, AtomicLong> code = new ConcurrentHashMap<>();
    public final AtomicLong time = new AtomicLong();
    public final AtomicLong[] histogram;

    private final Configuration configuration;

    public Statistics(Configuration configuration) {
        histogram = new AtomicLong[configuration.histogram.length];
        for (var i = 0; i < histogram.length; i++) histogram[i] = new AtomicLong();

        this.configuration = configuration;
    }

    public void print(long duration) {
        var c = count.get();
        System.out.println();
        System.out.println("Requests: " + c);
        System.out.printf("Qps: %.2f\n", c * 1000.d / duration);
        System.out.printf("Avg: %.3fms\n", (double) time.get() / count.get());
        System.out.println("Response histogram:");
        for (var i = configuration.histogram.length - 1; i >= 0; i--) {
            var count = 0L;
            for (var k = i; k < configuration.histogram.length; k++) {
                count += histogram[k].get();
            }

            var percent = count * 100d / c;

            System.out.printf("  >%5dms - %5d (%.2f%%)\n", configuration.histogram[i], count, percent);
        }
        System.out.println("Response code:");
        code.forEach((code, codeCount) -> {
            var percent = codeCount.get() * 100d / c;
            String codeStr;
            if (code == -1) {
                codeStr = "UER";
            } else if (code == -2) {
                codeStr = "CTE";
            } else {
                codeStr = String.valueOf(code);
            }
            System.out.printf("  %s - %d (%.2f%%)\n", codeStr, codeCount.get(), percent);
        });
    }

    public boolean isWarmUpMode() {
        return warmup.get() < configuration.warmup;
    }

    public void addTime(long duration) {
        time.addAndGet(duration);
        var index = Arrays.binarySearch(configuration.histogram, duration);
        if (index < 0) index = -index - 2;
        histogram[index].incrementAndGet();
    }
}
