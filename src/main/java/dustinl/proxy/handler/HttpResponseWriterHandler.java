package dustinl.proxy.handler;

import java.util.concurrent.Future;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * get {@Flink FullHttpResponse} from future and write to context.
 */
public class HttpResponseWriterHandler extends SimpleChannelInboundHandler<Future<FullHttpResponse>> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Future<FullHttpResponse> msg) throws Exception {
        FullHttpResponse response = msg.get();
        if (!HttpHeaders.isKeepAlive(response)) {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.writeAndFlush(response);
        }
    }
}
