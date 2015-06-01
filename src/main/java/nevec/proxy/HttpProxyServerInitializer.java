package nevec.proxy;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;

public class HttpProxyServerInitializer extends ChannelInitializer<SocketChannel> {

    private final static String HTTP_CODEC_NAME = "httpCodec";
    private final static String HTTP_PROXY_NAME = "httpProxy";

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        p.addLast(HTTP_CODEC_NAME, new HttpServerCodec());
        p.addLast(HTTP_PROXY_NAME, new HttpProxyServerHandler());
    }
}
