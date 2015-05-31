package nevec.proxy;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;

public class HttpProxyServerInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;

    public HttpProxyServerInitializer(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        if (sslCtx != null) {
            p.addLast(sslCtx.newHandler(ch.alloc()));
        }

        String scheme = sslCtx == null ? "http://" : "https://";

        p.addLast(new HttpServerCodec());
//        p.addLast(new HttpRequestDecoder());
//        p.addLast(new HttpObjectAggregator(1048576));
//        p.addLast(new HttpResponseEncoder());
//        p.addLast(new HttpContentCompressor());
//        p.addLast(new ChunkedWriteHandler());
        p.addLast(new HttpProxyServerHandler(scheme));
    }
}
