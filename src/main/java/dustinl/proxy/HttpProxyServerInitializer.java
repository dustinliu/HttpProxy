package dustinl.proxy;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * Http proxy server initializer.
 */
public class HttpProxyServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final String HTTP_CODEC_NAME = "httpCodec";
    private static final String HTTP_PROXY_NAME = "httpProxy";

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        p.addLast(HTTP_CODEC_NAME, new HttpServerCodec());
        p.addLast(HTTP_PROXY_NAME, new HttpProxyServerHandler());
    }
}
