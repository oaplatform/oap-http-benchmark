package oap.httpbenchmark;

import com.google.common.util.concurrent.RateLimiter;

import java.util.stream.Stream;

/**
 * Created by igor.petrenko on 09/02/2019.
 */
public class Main {
    public static void main(String[] args) {
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

    private static void run(Configuration configuration) {
        System.setProperty("jdk.httpclient.connectionPoolSize", "1");
        System.setProperty("jdk.httpclient.keepalive.timeout", "99999999");

        var rateLimiter = RateLimiter.create(configuration.qps);
        var statistics = new Statistics(configuration);

        var pool = new Task[configuration.threads];
        for (var i = 0; i < configuration.threads; i++) {
            var task = new Task(configuration, rateLimiter, statistics);
            pool[i] = task;
        }

        for (var thread : pool) {
            thread.start();
        }

        var start = -1L;

        while (System.currentTimeMillis() < start + configuration.time || start == -1L) {
            if (!statistics.isWarmUpMode() && start == -1) {
                start = System.currentTimeMillis();
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
        }
        for (var thread : pool) {
            thread.done = true;
        }
        for (var thread : pool) {
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
            }
        }
        for (var thread : pool) thread.stop();


        statistics.print(System.currentTimeMillis() - start);
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
