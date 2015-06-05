package dustinl.proxy.handler;

import java.net.InetSocketAddress;
import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Access log handler.
 */
public class AccessLogHandler extends MessageToMessageCodec<HttpRequest, HttpResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger("access");
    private static final AttributeKey<HttpRequest> REQUEST = AttributeKey.valueOf("request");
    private static final AttributeKey<Long> START_TIME = AttributeKey.valueOf("start_time");

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpResponse msg, List<Object> out) {
        try {
            HttpRequest request = ctx.attr(REQUEST).get();
            String uri = request.getUri();
            String ip = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
            int status = msg.getStatus().code();
            long duration = System.currentTimeMillis() - ctx.attr(START_TIME).get();

            LOGGER.info(String.format("%s %d %s %d", ip, status, uri, duration));
        } finally {
            ReferenceCountUtil.retain(msg);
            out.add(msg);
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpRequest msg, List<Object> out) {
        Attribute<HttpRequest> requestAttr = ctx.attr(REQUEST);
        requestAttr.set(msg);

        Attribute<Long> startTimeAttr = ctx.attr(START_TIME);
        startTimeAttr.set(System.currentTimeMillis());

        ReferenceCountUtil.retain(msg);
        ctx.fireChannelRead(msg);
    }
}
