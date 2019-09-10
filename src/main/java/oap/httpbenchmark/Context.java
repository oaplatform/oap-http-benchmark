package oap.httpbenchmark;

import com.google.common.base.Preconditions;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultClientConnectionReuseStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;

import java.io.File;
import java.io.IOException;

/**
 * Created by igor.petrenko on 09/02/2019.
 */
public class Context {
    public final CloseableHttpClient client;
    public final HttpRequestBase request;

    public Context(Configuration configuration) throws IOException {
        var socketConfig = SocketConfig
                .custom()
                .setTcpNoDelay(true)
                .setSoReuseAddress(true)
                .build();

        var defaultRequestConfig = RequestConfig.custom()
                .setExpectContinueEnabled(true)
                .build();

        var requestConfig = RequestConfig.copy(defaultRequestConfig)
                .setSocketTimeout((int) configuration.timeout)
                .setConnectTimeout((int) configuration.connectionTimeout)
                .setConnectionRequestTimeout((int) configuration.timeout)
                .build();

        var builder = HttpClients
                .custom()
                .setRetryHandler((e, i, httpContext) -> false)
                .setConnectionManager(new BasicHttpClientConnectionManager())
                .setMaxConnPerRoute(1)
                .setMaxConnTotal(1)
                .disableRedirectHandling()
                .setDefaultSocketConfig(socketConfig);
        if (configuration.keepalive)
            builder.setConnectionReuseStrategy(DefaultClientConnectionReuseStrategy.INSTANCE);
        else builder.setConnectionReuseStrategy(NoConnectionReuseStrategy.INSTANCE);

        if (configuration.userAgent != null) builder.setUserAgent(configuration.userAgent);

        client = builder.build();

        request = switch (configuration.method) {
            case GET -> new HttpGet(configuration.url);
            case POST -> new HttpPost(configuration.url);
        };

        if (configuration.inFile != null) {
            Preconditions.checkArgument(request instanceof HttpEntityEnclosingRequestBase);
            ((HttpEntityEnclosingRequestBase) request).setEntity(new FileEntity(new File(configuration.inFile)));
        }

        request.setConfig(requestConfig);
    }
}
