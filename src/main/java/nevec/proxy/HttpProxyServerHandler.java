package nevec.proxy;

import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
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

public class HttpProxyServerHandler extends SimpleChannelInboundHandler<Object> {

    private HttpRequest request;
    private String scheme;
    private RequestBuilder requestBuilder;

    private static AsyncHttpClient httpClient;
    private static final int IDLE_CONNECTION_TIMEOUT = 10000;
    private static final int CONNECTION_LIFETIME = 100000;
    private static final int MAX_RETRY = 5;
    private static final String HOST_HEADER = "HOST";

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
            HttpRequest request = this.request = (HttpRequest) msg;
            if (HttpHeaders.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }

            requestBuilder = new RequestBuilder();
            requestBuilder.setMethod(request.getMethod().name());

            HttpHeaders headers = request.headers();
            if (!headers.isEmpty()) {
                for (Map.Entry<String, String> h : headers) {
                    String key = h.getKey();
                    String value = h.getValue();
                    requestBuilder.addHeader(key, value);
                }
            }

            String url = scheme + request.headers().get(HOST_HEADER) + request.getUri();
            requestBuilder.setUrl(url);
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

    private void writeResponse(ChannelHandlerContext ctx) throws IOException {
        boolean keepAlive = HttpHeaders.isKeepAlive(request);
        // Build the response object.

        final HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        httpClient.executeRequest(requestBuilder.build(), new AsyncHandler<String>() {
            @Override
            public void onThrowable(Throwable throwable) {
                sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST);
            }

            @Override
            public STATE onBodyPartReceived(HttpResponseBodyPart httpResponseBodyPart)
                    throws Exception {
                System.out.println("1111111111111111111111111111111111");
                byte[] bytes = httpResponseBodyPart.getBodyPartBytes();
                System.out.print(new String(bytes));
//                ByteBuf buf = ctx.alloc().buffer(bytes.length).setBytes(0, bytes);
                ByteBuf buf = Unpooled.copiedBuffer(bytes);
                ctx.write(new DefaultHttpContent(buf));
                return STATE.CONTINUE;
            }

            @Override
            public STATE onStatusReceived(HttpResponseStatus httpResponseStatus) throws Exception {
                System.out.println("2222222222222222222222222222222222");
                HttpVersion version = new HttpVersion(httpResponseStatus.getProtocolName(),
                        httpResponseStatus.getProtocolMajorVersion(), httpResponseStatus
                        .getProtocolMinorVersion(), true);
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
                System.out.println("3333333333333333333333333333333333");
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
                System.out.println("4444444444444444444444444444444444");
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
        sendError(ctx, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST);
        ctx.close();
    }
}
