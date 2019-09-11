package oap.httpbenchmark;

import com.google.common.util.concurrent.RateLimiter;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static oap.httpbenchmark.Utils.*;

/**
 * Created by igor.petrenko on 09/02/2019.
 */
public class Task extends Thread {
    private final Configuration configuration;
    private final RateLimiter rateLimiter;
    private final Statistics statistics;
    private final byte[] bytes = new byte[1024 * 8];
    public boolean done = false;
    private Context context;

    public Task(Configuration configuration, RateLimiter rateLimiter, Statistics statistics) {
        this.configuration = configuration;
        this.rateLimiter = rateLimiter;
        this.statistics = statistics;
    }

    @Override
    public void run() {
        while (!done) {
            var warmUpMode = statistics.isWarmUpMode();
            try {
                if (context == null)
                    context = new Context(configuration, statistics);

                rateLimiter.acquire();
                var start = System.currentTimeMillis();
                var response = context.client.execute(context.request);

                var statusLine = response.getStatusLine();
                var entity = response.getEntity();
                if (entity != null) {
                    try (var is = entity.getContent()) {
                        while (is.read(bytes) >= 0) ;
                    }
                    EntityUtils.consume(entity);
                }
                var end = System.currentTimeMillis();

                if (!warmUpMode) {
                    statistics.addTime(end - start);
                    statistics.code.computeIfAbsent(statusLine.getStatusCode(), k -> new AtomicLong()).incrementAndGet();
                }
            } catch (IOException e) {
                if (!warmUpMode) {
                    var code = -1;
                    if (e instanceof SocketTimeoutException) {
                        code = SOCKET_TIMEOUT_EXCEPTION;
                    } else if (e instanceof ConnectTimeoutException) {
                        code = CONNECT_TIMEOUT_EXCEPTION;
                    } else if (e instanceof HttpHostConnectException) {
                        code = HTTP_HOST_CONNECT_EXCEPTION;
                    } else {
                        System.err.println(e.getClass());
                    }

                    statistics.code.computeIfAbsent(code, k -> new AtomicLong()).incrementAndGet();
                }
            } finally {
                if (!warmUpMode)
                    statistics.count.incrementAndGet();
                else
                    statistics.warmup.incrementAndGet();
            }
        }
    }
}
