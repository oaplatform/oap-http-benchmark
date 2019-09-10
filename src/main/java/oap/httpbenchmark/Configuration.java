package oap.httpbenchmark;

import com.google.common.base.Preconditions;

import java.util.stream.LongStream;

import static java.util.stream.Collectors.joining;

/**
 * Created by igor.petrenko on 09/02/2019.
 */
public class Configuration {
    public long[] histogram;
    public int threads = 1;
    public int qps = -1;
    public HttpMethod method = HttpMethod.GET;
    public long time = 10 * 1000;
    public String url;
    public boolean keepalive = true;
    public String userAgent = null;
    public long connectionTimeout = 10 * 1000;
    public long timeout = 500;
    public int warmup = 10;
    public String inFile;

    public Configuration() {
        histogram = new long[]{10L, 30L, 50L, 100L, 150L, 200L, 300L, 500L, 1000L};
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("  threads = " + threads + "\n");
        if (qps > 0) sb.append("  qps = " + qps + "\n");
        sb.append("  method = " + method.name() + "\n");
        sb.append("  time = " + time + "ms\n");
        if (userAgent != null) sb.append("  userAgent = " + userAgent + "\n");
        sb.append("  connection timeout = " + connectionTimeout + "ms\n");
        sb.append("  request timeout = " + timeout + "ms\n");
        sb.append("  warmup = " + warmup + "\n");
        sb.append("  keepalive = " + keepalive + "\n");
        if (inFile != null) sb.append("  in file = " + inFile + "\n");
        sb.append("  histogram = " + LongStream.of(histogram).mapToObj(String::valueOf).collect(joining(",")) + "\n");
        sb.append("  url = " + url + "\n");

        return sb.toString();
    }

    public void verify() {
        Preconditions.checkArgument(method == HttpMethod.POST || inFile == null);
    }
}
