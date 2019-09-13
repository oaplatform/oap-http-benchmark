package oap.httpbenchmark;

import com.google.common.base.Preconditions;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.DefaultClientConnectionReuseStrategy;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.codecs.DefaultHttpRequestWriterFactory;
import org.apache.http.impl.nio.codecs.DefaultHttpResponseParserFactory;
import org.apache.http.impl.nio.conn.ManagedNHttpClientConnectionFactory;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.conn.ManagedNHttpClientConnection;
import org.apache.http.nio.conn.NoopIOSessionStrategy;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.protocol.HttpContext;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * Created by igor.petrenko on 09/02/2019.
 */
public class Context {
    public final CloseableHttpAsyncClient client;
    public final HttpRequestBase request;
    public final HttpHost target;

    public Context(Configuration configuration, Statistics statistics) throws IOException {
        var ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(Runtime.getRuntime().availableProcessors())
                .setConnectTimeout((int) configuration.connectionTimeout)
                .setSoTimeout((int) configuration.timeout)
                .setSoReuseAddress(true)
                .setTcpNoDelay(true)
                .build();

        var ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);

        var connFactory = new ManagedNHttpClientConnectionFactory(
                new DefaultHttpRequestWriterFactory(), new DefaultHttpResponseParserFactory(), HeapByteBufferAllocator.INSTANCE) {
            @Override
            public ManagedNHttpClientConnection create(IOSession iosession, ConnectionConfig config) {
                statistics.connection.incrementAndGet();
                return super.create(iosession, config);
            }
        };

        var sessionStrategyRegistry = RegistryBuilder.<SchemeIOSessionStrategy>create()
                .register("http", NoopIOSessionStrategy.INSTANCE)
                .register("https", SSLIOSessionStrategy.getDefaultStrategy())
                .build();
        var connManager = new PoolingNHttpClientConnectionManager(
                ioReactor, connFactory, sessionStrategyRegistry, new SystemDefaultDnsResolver());

        connManager.setMaxTotal(configuration.threads);
        connManager.setDefaultMaxPerRoute(configuration.threads);

        var builder = HttpAsyncClients
                .custom()
                .setRedirectStrategy(new RedirectStrategy() {
                    @Override
                    public boolean isRedirected(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws ProtocolException {
                        return false;
                    }

                    @Override
                    public HttpUriRequest getRedirect(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws ProtocolException {
                        return null;
                    }
                })
                .setConnectionManager(connManager);
        if (configuration.keepalive)
            builder.setConnectionReuseStrategy(DefaultClientConnectionReuseStrategy.INSTANCE);
        else builder.setConnectionReuseStrategy(NoConnectionReuseStrategy.INSTANCE);

        if (configuration.userAgent != null) builder.setUserAgent(configuration.userAgent);

        client = builder.build();

        request = switch (configuration.method) {
            case GET -> new HttpGet(configuration.url);
            case POST -> new HttpPost(configuration.url);
        };

        URI requestURI = request.getURI();
        target = URIUtils.extractHost(requestURI);


        if (configuration.inFile != null) {
            Preconditions.checkArgument(request instanceof HttpEntityEnclosingRequestBase);
            ((HttpEntityEnclosingRequestBase) request).setEntity(new FileEntity(new File(configuration.inFile)));
        }
    }
}
