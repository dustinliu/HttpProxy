package nevec.proxy;

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

public class HttpProxyServerHandler extends SimpleChannelInboundHandler<Object> {

    private HttpRequest request;
    private String scheme;
    private RequestBuilder requestBuilder;

    private static AsyncHttpClient httpClient;
    private static final int IDLE_CONNECTION_TIMEOUT = 10000;
    private static final int CONNECTION_LIFETIME = 100000;
    private static final int MAX_RETRY = 5;
    private static final String HOST_HEADER = "HOST";
    private static final Logger logger = LoggerFactory.getLogger(HttpProxyServerHandler.class);

    static  {
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
                .setFollowRedirects(false)
                .setAllowPoolingConnection(true)
                .setIdleConnectionInPoolTimeoutInMs(IDLE_CONNECTION_TIMEOUT)
                .setMaxConnectionLifeTimeInMs(CONNECTION_LIFETIME)
                .setMaxRequestRetry(MAX_RETRY)
                .build();
        httpClient = new AsyncHttpClient(config);
    }

    HttpProxyServerHandler(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            logger.debug("HttpRequest received");
            HttpRequest request = this.request = (HttpRequest) msg;
            if (HttpHeaders.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }

            requestBuilder = new RequestBuilder();
            requestBuilder.setMethod(request.getMethod().name());

            logger.debug("============ request headers ===============");
            HttpHeaders headers = request.headers();
            if (!headers.isEmpty()) {
                for (Map.Entry<String, String> h : headers) {
                    String key = h.getKey();
                    String value = h.getValue();
                    requestBuilder.addHeader(key, value);
                    logger.debug("[" + key + "] : [" + value + "]");
                }
            }
            logger.debug("========= request headers done ===============");

            try {
                requestBuilder.setUrl(getUrl(request));
            } catch (MalformedURLException e) {
                sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST);
                return;
            }
        }
        if (msg instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) msg;
            ByteBuf content = httpContent.content();
            if (content.isReadable()) {
            }
            if (msg instanceof LastHttpContent) {
                try {
                    writeResponse(ctx);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String getUrl(HttpRequest request) throws MalformedURLException {
        String host = request.headers().get(HOST_HEADER);
        String uri = request.getUri();
        logger.debug("HOST: [" + host + "]");
        logger.debug("URI: [" + uri + "]");
        if (host == null) {
            return uri;
        }

        URL url = new URL(uri); // the uri part may contain full URL
        String ret = url.getProtocol() + "://" + host + url.getPath();
        if (url.getQuery() != null) {
            ret += "?" + url.getQuery();
        }

        logger.debug("URL: [" + ret + "]");
        return ret;
    }

    private void writeResponse(ChannelHandlerContext ctx) throws IOException {
        boolean keepAlive = HttpHeaders.isKeepAlive(request);
        // Build the response object.

        final HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        httpClient.executeRequest(requestBuilder.build(), new AsyncHandler<String>() {
            @Override
            public void onThrowable(Throwable throwable) {
                logger.warn("http client got exception, " + throwable.getMessage());
                sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST);
            }

            @Override
            public STATE onBodyPartReceived(HttpResponseBodyPart httpResponseBodyPart)
                    throws Exception {
                byte[] bytes = httpResponseBodyPart.getBodyPartBytes();
                logger.debug("http client body pard received, length:" + String.valueOf(bytes.length));
                ByteBuf buf = Unpooled.copiedBuffer(bytes);
                ctx.write(new DefaultHttpContent(buf));
                return STATE.CONTINUE;
            }

            @Override
            public STATE onStatusReceived(HttpResponseStatus httpResponseStatus) throws Exception {
                logger.debug("http client staus received");
                HttpVersion version = new HttpVersion(httpResponseStatus.getProtocolName(),
                        httpResponseStatus.getProtocolMajorVersion(), httpResponseStatus
                        .getProtocolMinorVersion(), false);
                response.setProtocolVersion(version);

                io.netty.handler.codec.http.HttpResponseStatus status =
                        new io.netty.handler.codec.http.HttpResponseStatus(httpResponseStatus
                                .getStatusCode(), httpResponseStatus.getStatusText());
                response.setStatus(status);
                return STATE.CONTINUE;
            }

            @Override
            public STATE onHeadersReceived(HttpResponseHeaders httpResponseHeaders)
                    throws Exception {
                logger.debug("http client headers received");
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
                logger.debug("http client request comoleted");
                if (!keepAlive) {
                    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).
                            addListener(ChannelFutureListener.CLOSE);
                } else {
                    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                }
                return "Done";
            }
        });
    }

    private static void sendError(ChannelHandlerContext ctx,
                                  io.netty.handler.codec.http.HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse( HTTP_1_1, status,
                Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set("Content-type", "text/plain; charset=UTF-8");

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
        ctx.write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("exception caught: " + cause.getMessage());
        sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST);
        ctx.close();
    }
}
