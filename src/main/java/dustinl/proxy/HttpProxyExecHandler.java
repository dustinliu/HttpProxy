package dustinl.proxy;

import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

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
public class HttpProxyExecHandler extends SimpleChannelInboundHandler<Object> {

    /** The http request. */
    private HttpRequest request;
    /** The http client request builder. */
    private RequestBuilder requestBuilder;
    /** tmp file to store the large content. */
    private File tmpContent;

    /** The httpClient. */
    private static AsyncHttpClient httpClient;
    /** how long a connection in idle state before been closed, in ms. */
    private static final int IDLE_CONNECTION_TIMEOUT = 10000;
    /** the maximum number of retry, if request failed . */
    private static final int MAX_RETRY = 5;
    /** the host header string. */
    private static final String HOST_HEADER = "HOST";
    /** the prefix of tmp file. */
    private static final String TMP_PREFIX = "http_proxy";
    /** logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProxyExecHandler.class);

    static  {
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
                .setFollowRedirect(false)
                .setAllowPoolingConnections(true)
                .setPooledConnectionIdleTimeout(IDLE_CONNECTION_TIMEOUT)
                .setMaxRequestRetry(MAX_RETRY)
                .build();
        httpClient = new AsyncHttpClient(config);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            this.request = (HttpRequest) msg;
            handlerHttpRequest(ctx);
        } else if (msg instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) msg;
            handleHttpContent(ctx, httpContent);
        }
    }

    /**
     * Handle {@link HttpContent} message.
     *
     * @param ctx the {@link ChannelHandlerContext}
     * @param httpContent the message to handle
     */
    private void handleHttpContent(ChannelHandlerContext ctx, HttpContent httpContent) {
        ByteBuf content = httpContent.content();
        try {
            writeContent(content);
        } catch (IOException e) {
            sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage());
            return;
        }
        if (httpContent instanceof LastHttpContent) {
            try {
                writeResponse(ctx);
            } catch (IOException e) {
                sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST, e.getMessage());
            }
        }
    }

    /**
     * Handler {@link HttpRequest} message.
     *
     * @param ctx the {@link ChannelHandlerContext}
     */
    private void handlerHttpRequest(ChannelHandlerContext ctx) {
        LOGGER.debug("HttpRequest received");
        if (HttpHeaders.is100ContinueExpected(request)) {
            send100Continue(ctx);
        }

        requestBuilder = new RequestBuilder();
        LOGGER.debug("METHOD: " + request.getMethod().name());
        requestBuilder.setMethod(request.getMethod().name());

        LOGGER.debug("============ request headers ===============");
        HttpHeaders headers = request.headers();
        for (Map.Entry<String, String> h : headers) {
            String key = h.getKey();
            if (key.equalsIgnoreCase("Proxy-Connection]")) {
                continue;
            }
            String value = h.getValue();
            requestBuilder.addHeader(key, value);
            LOGGER.debug("[" + key + "] : [" + value + "]");
        }
        LOGGER.debug("============================================");

        try {
            requestBuilder.setUrl(getUrl());
        } catch (MalformedURLException e) {
            sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST,
                    e.getMessage());
        }

        try {
            tmpContent = File.createTempFile(TMP_PREFIX, null);
        } catch (IOException e) {
            sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    e.getMessage());
        }
    }

    /**
     * Write post/put content to tmp file.
     *
     * @param content the content to write
     * @throws IOException when write failed
     */
    private void writeContent(ByteBuf content) throws IOException {
        if (!content.isReadable()) {
            return;
        }

        try (FileOutputStream outputStream = new FileOutputStream(tmpContent, true)) {
            outputStream.write(content.array());
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
        StringBuilder buffer = new StringBuilder();
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
        if (tmpContent.length() != 0) {
            requestBuilder.setBody(tmpContent);
        }

        Future<FullHttpResponse> future =
                httpClient.executeRequest(requestBuilder.build(), new AsyncHandler<FullHttpResponse>() {

            private ByteBuf buf = ctx.alloc().buffer();
            private HttpResponse httpResponse = new DefaultHttpResponse(HTTP_1_1, OK);

            @Override
            public void onThrowable(Throwable throwable) {
                LOGGER.warn("http client got exception, " + throwable.getMessage());
                sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST, throwable.getMessage());
            }

            @Override
            public STATE onBodyPartReceived(HttpResponseBodyPart httpResponseBodyPart)
                    throws Exception {
                byte[] bytes = httpResponseBodyPart.getBodyPartBytes();
                LOGGER.debug("http client body part received, length:" + String.valueOf(bytes.length));
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(new String(bytes));
                }
                buf.writeBytes(bytes);
                return STATE.CONTINUE;
            }

            @Override
            public STATE onStatusReceived(HttpResponseStatus httpResponseStatus) throws Exception {
                HttpVersion version = new HttpVersion(httpResponseStatus.getProtocolName(),
                        httpResponseStatus.getProtocolMajorVersion(), httpResponseStatus
                        .getProtocolMinorVersion(), false);
                httpResponse.setProtocolVersion(version);

                io.netty.handler.codec.http.HttpResponseStatus status =
                        new io.netty.handler.codec.http.HttpResponseStatus(httpResponseStatus.getStatusCode(),
                                httpResponseStatus.getStatusText());
                httpResponse.setStatus(status);
                LOGGER.debug("http client status received: " + status.toString());
                return STATE.CONTINUE;
            }

            @Override
            public STATE onHeadersReceived(HttpResponseHeaders httpResponseHeaders)
                    throws Exception {
                LOGGER.debug("http client headers received");
                Set<Map.Entry<String, List<String>>> headers = httpResponseHeaders.getHeaders().entrySet();
                headers.stream().forEach(entry -> entry.getValue().stream().forEach(value ->
                        httpResponse.headers().add(entry.getKey(), value)));

                LOGGER.debug("============== response headers ===================");
                for (Map.Entry<String, List<String>> entry : headers) {
                    String key = entry.getKey();
                    for (String value : entry.getValue()) {
                        httpResponse.headers().add(key, value);
                        LOGGER.debug("[" + key + "] : [" + value + "]");
                    }
                }
                LOGGER.debug("===================================================");


                return STATE.CONTINUE;
            }

            @Override
            public FullHttpResponse onCompleted() throws Exception {
                LOGGER.debug("http client request completed");
                tmpContent.delete();
                FullHttpResponse fullHttpResponse =
                        new DefaultFullHttpResponse(httpResponse.getProtocolVersion(), httpResponse .getStatus(), buf);
                fullHttpResponse.headers().add(httpResponse.headers());
                return fullHttpResponse;
            }
        });

        ctx.fireChannelRead(future);
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
        LOGGER.info("operation failed, status(" + status + "), " + message);

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
