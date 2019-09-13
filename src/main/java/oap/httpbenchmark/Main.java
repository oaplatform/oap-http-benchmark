package oap.httpbenchmark;

import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static oap.httpbenchmark.Utils.*;

/**
 * Created by igor.petrenko on 09/02/2019.
 */
public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        var configuration = new Configuration();

        for (int i = 0; i < args.length; i++) {
            var arg = args[i].trim();

            var index = arg.indexOf('=');
            if (index <= 0) {
                switch (arg) {
                    case "-dk" -> configuration.keepalive = false;
                    default -> configuration.url = arg;
                }
            } else {
                var name = arg.substring(0, index);
                var value = arg.substring(index + 1);

                switch (name) {
                    case "-t" -> configuration.threads = Integer.parseInt(value);
                    case "-qps" -> configuration.qps = Integer.parseInt(value);
                    case "-m" -> configuration.method = HttpMethod.valueOf(value);
                    case "-time" -> configuration.time = timeToMS(value);
                    case "-ua" -> configuration.userAgent = value;
                    case "-ct" -> configuration.connectionTimeout = timeToMS(value);
                    case "-rt" -> configuration.timeout = timeToMS(value);
                    case "-in" -> configuration.inFile = value;
                    case "-wu" -> configuration.warmup = Integer.parseInt(value);
                    case "-hm" -> configuration.histogram = Stream.of(("0," + value).split(","))
                            .mapToLong(v -> Long.parseLong(v.trim()))
                            .sorted()
                            .toArray();
                    case "-url" -> configuration.url = value;
                    default -> {
                        System.out.println("Unknown arg " + name);
                        printHelpAndExit();
                    }
                }

            }
        }

        if (configuration.url == null) printHelpAndExit();

        configuration.verify();

        System.out.println();
        System.out.println("configuration: \n" + configuration);

        run(configuration);

        System.exit(1);
    }

    private static void run(Configuration configuration) throws IOException, InterruptedException {
        System.setProperty("jdk.httpclient.connectionPoolSize", "1");
        System.setProperty("jdk.httpclient.keepalive.timeout", "99999999");

        var statistics = new Statistics(configuration);


        var start = -1L;

        var context = new Context(configuration, statistics);
        var semaphore = new Semaphore(configuration.qps);
        context.client.start();

        while (start == -1L || System.currentTimeMillis() < start + configuration.time) {
            if (!statistics.isWarmUpMode() && start == -1) {
                start = System.currentTimeMillis();
            }

            semaphore.acquire();
//            System.out.println("execute " + statistics.count + " / " + statistics.warmup + " / " + statistics.isWarmUpMode());
            var startTime = System.currentTimeMillis();
            context.client.execute(context.request, new FutureCallback<>() {
                public void completed(HttpResponse response) {
                    var entity = response.getEntity();
                    try {
                        EntityUtils.consume(entity);
                    } catch (IOException ignored) {
                    }
                    var warmUpMode = statistics.isWarmUpMode();
                    done(semaphore, warmUpMode, statistics);
                    if (!warmUpMode) {
                        statistics.addTime(System.currentTimeMillis() - startTime);
                        statistics.code.computeIfAbsent(response.getStatusLine().getStatusCode(), k -> new AtomicLong()).incrementAndGet();
                    }
                }

                public void failed(final Exception e) {
                    var warmUpMode = statistics.isWarmUpMode();
                    done(semaphore, warmUpMode, statistics);

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
                }

                public void cancelled() {
                    done(semaphore, statistics.isWarmUpMode(), statistics);
                }

            });
        }

        statistics.print(System.currentTimeMillis() - start);
    }

    private static void done(Semaphore semaphore, boolean warmUpMode, Statistics statistics) {
        if (!warmUpMode)
            statistics.count.incrementAndGet();
        else
            statistics.warmup.incrementAndGet();

        semaphore.release();
    }

    private static long timeToMS(String value) {
        value = value.trim();
        if (value.endsWith("s")) return timeToLong(value) * 1000L;
        else if (value.endsWith("m")) return timeToLong(value) * 60 * 1000L;
        else if (value.endsWith("h")) return timeToLong(value) * 60 * 60 * 1000L;
        return printHelpAndExit();
    }

    private static long timeToLong(String value) {
        return Long.parseLong(value.substring(0, value.length() - 1));
    }

    private static <T> T printHelpAndExit() {
        System.out.println("Usage java -jar oaphttpb.jar [OPTION]...");
        System.out.println("  -url=<URL>");
        System.out.println("  -t=THREADS");
        System.out.println("  -qps=QPS");
        System.out.println("  -m=<GET|POST>");
        System.out.println("  -time=TIME<s|m|h>");
        System.out.println("  -ct=<Connection timeout><s|m|h>");
        System.out.println("  -rt=<Request timeout><s|m|h>");
        System.out.println("  -ua=<USER AGENT>");
        System.out.println("  -in=<file path>");
        System.out.println("  -wu=<warm up>");
        System.out.println("  -hm=<duration1,duration2,...>");

        System.exit(1);
        throw new RuntimeException();
    }
}
