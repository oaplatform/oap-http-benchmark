package oap.httpbenchmark;

import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by igor.petrenko on 09/02/2019.
 */
public class Task implements Runnable {
    private static final ThreadLocal<Context> threadLocalValue = new ThreadLocal<>();

    private final Configuration configuration;
    private final Statistics statistics;

    public Task(Configuration configuration, Statistics statistics) {
        this.configuration = configuration;
        this.statistics = statistics;
    }

    @Override
    public void run() {
        var warmUpMode = statistics.isWarmUpMode();
        try {
            var context = threadLocalValue.get();
            if (context == null) {
                threadLocalValue.set(context = new Context(configuration));
            }

            var start = System.currentTimeMillis();
            var response = context.client.execute(context.request);

            var statusLine = response.getStatusLine();
            var entity = response.getEntity();
            if (entity != null) EntityUtils.consume(entity);
            var end = System.currentTimeMillis();

            if (!warmUpMode) {
                statistics.addTime(end - start);
                statistics.code.computeIfAbsent(statusLine.getStatusCode(), k -> new AtomicLong()).incrementAndGet();
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            if (!warmUpMode) {
                statistics.code.computeIfAbsent(-1, k -> new AtomicLong()).incrementAndGet();
            }
        } finally {
            if (!warmUpMode)
                statistics.count.incrementAndGet();
            else
                statistics.warmup.incrementAndGet();
        }
    }
}
