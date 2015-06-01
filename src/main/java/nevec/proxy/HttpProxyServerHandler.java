package com.yahoo.nevec.proxy;

import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.RequestBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The http proxy server handler to handle http request.
 */
public class HttpProxyServerHandler extends SimpleChannelInboundHandler<Object> {

    /** The http request. */
    private HttpRequest request;
    /** The http client request builder. */
    private RequestBuilder requestBuilder;

    /** The httpClient. */
    private static AsyncHttpClient httpClient;
    /** how long a connection in idle state before been closed, in ms. */
    private static final int IDLE_CONNECTION_TIMEOUT = 10000;
    /** the maximum number of retry, if request failed . */
    private static final int MAX_RETRY = 5;
    /** the host header string. */
    private static final String HOST_HEADER = "HOST";
//    /** yca header string. */
//    private static final String YCA_AUTH_HEADER = "Yahoo-App-Auth";
    /** logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProxyServerHandler.class);
//    /** yca db. */
//    private static CertDatabase cdb = null;

    static  {
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
                .setFollowRedirect(false)
                .setAllowPoolingConnections(true)
                .setPooledConnectionIdleTimeout(IDLE_CONNECTION_TIMEOUT)
                .setMaxRequestRetry(MAX_RETRY)
                .build();
        httpClient = new AsyncHttpClient(config);

//        try {
//            cdb = new CertDatabase();
//        } catch (YCAException e) {
//            LOGGER.error("yca initial failed: " + e.getMessage());
//            throw new RuntimeException("yca initial failed");
//        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            LOGGER.debug("HttpRequest received");
            this.request = (HttpRequest) msg;
            if (HttpHeaders.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }

            requestBuilder = new RequestBuilder();
            requestBuilder.setMethod(request.getMethod().name());

            LOGGER.debug("============ request headers ===============");
            HttpHeaders headers = request.headers();
            if (!headers.isEmpty()) {
                for (Map.Entry<String, String> h : headers) {
                    String key = h.getKey();
                    String value = h.getValue();
                    requestBuilder.addHeader(key, value);
                    LOGGER.debug("[" + key + "] : [" + value + "]");
                }
            }
            LOGGER.debug("========= request headers done ===============");

            try {
                requestBuilder.setUrl(getUrl());
            } catch (MalformedURLException e) {
                sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST, e.getMessage());
                return;
            }
        }
        if (msg instanceof HttpContent) {
            LOGGER.debug("xxxxxxxxxxxxxxxxxxxxxxx");
            if (msg instanceof LastHttpContent) {
                try {
                    writeResponse(ctx);
                } catch (IOException e) {
                    sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST, e.getMessage());
                }
            }
        }
    }

    /**
     * Gets url.
     *
     * @return the url
     * @throws MalformedURLException the malformed uRL exception
     */
    private String getUrl() throws MalformedURLException {
        String host = request.headers().get(HOST_HEADER);
        String uri = request.getUri();
        LOGGER.debug("HOST: [" + host + "]");
        LOGGER.debug("URI: [" + uri + "]");
        if (host == null) {
            LOGGER.debug("URL: [" + uri + "]");
            return uri;
        }

        URL url = new URL(uri); // the uri part may contain full URL
        StringBuffer buffer = new StringBuffer();
        buffer.append(url.getProtocol()).append("://").append(host).append(url.getPath());
        if (url.getQuery() != null) {
            buffer.append('?').append(url.getQuery());
        }

        LOGGER.debug("URL: [" + buffer.toString() + "]");
        return buffer.toString();
    }

    /**
     * send request to backend to return response to client.
     *
     * @param ctx the ctx
     * @throws IOException the iO exception
     */
    private void writeResponse(ChannelHandlerContext ctx) throws IOException {
        boolean keepAlive = HttpHeaders.isKeepAlive(request);
        // Build the response object.

        final HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
//        requestBuilder.addHeader(YCA_AUTH_HEADER, cdb.getCert(""));
        httpClient.executeRequest(requestBuilder.build(), new AsyncHandler<String>() {
            @Override
            public void onThrowable(Throwable throwable) {
                LOGGER.warn("http client got exception, " + throwable.getMessage());
                sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST, throwable.getMessage());
            }

            @Override
            public STATE onBodyPartReceived(HttpResponseBodyPart httpResponseBodyPart)
                    throws Exception {
                byte[] bytes = httpResponseBodyPart.getBodyPartBytes();
                LOGGER.debug("http client body pard received, length:" + String.valueOf(bytes.length));
                ByteBuf buf = Unpooled.copiedBuffer(bytes);
                ctx.write(new DefaultHttpContent(buf));
                return STATE.CONTINUE;
            }

            @Override
            public STATE onStatusReceived(HttpResponseStatus httpResponseStatus) throws Exception {
                LOGGER.debug("http client staus received");
                HttpVersion version = new HttpVersion(httpResponseStatus.getProtocolName(),
                        httpResponseStatus.getProtocolMajorVersion(), httpResponseStatus
                        .getProtocolMinorVersion(), false);
                response.setProtocolVersion(version);

                io.netty.handler.codec.http.HttpResponseStatus status =
                        new io.netty.handler.codec.http.HttpResponseStatus(httpResponseStatus.getStatusCode(),
                                httpResponseStatus.getStatusText());
                response.setStatus(status);
                return STATE.CONTINUE;
            }

            @Override
            public STATE onHeadersReceived(HttpResponseHeaders httpResponseHeaders)
                    throws Exception {
                LOGGER.debug("http client headers received");
                Set<Map.Entry<String, List<String>>> headers =
                        httpResponseHeaders.getHeaders().entrySet();
                headers.stream().forEach(entry ->
                        entry.getValue().stream().forEach(value ->
                                response.headers().add(entry.getKey(), value)));

                ctx.write(response);
                return STATE.CONTINUE;
            }

            @Override
            public String onCompleted() throws Exception {
                LOGGER.debug("http client request comoleted");
                if (!keepAlive) {
                    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
                } else {
                    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                }
                return "Done";
            }
        });
    }

    /**
     * Send error response.
     *
     * @param ctx the {@link ChannelHandlerContext}
     * @param status the http status
     * @param message message to show
     */
    private static void sendError(ChannelHandlerContext ctx,
                                  io.netty.handler.codec.http.HttpResponseStatus status,
                                  String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status,
                Unpooled.copiedBuffer("Failure: " + status + ", " + message + "\r\n", CharsetUtil.UTF_8));
        response.headers().set("Content-type", "text/plain; charset=UTF-8");

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Send 100 continue.
     *
     * @param ctx the {@link ChannelHandlerContext}
     */
    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
        ctx.write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.warn("exception caught: " + cause.getMessage());
        sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST, cause.getMessage());
        ctx.close();
    }
}
