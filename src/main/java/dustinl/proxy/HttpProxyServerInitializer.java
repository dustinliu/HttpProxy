package dustinl.proxy;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * Http proxy server initializer.
 */
public class HttpProxyServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final String HTTP_CODEC_NAME = "httpCodec";
    private static final String HTTP_EXEC_NAME = "httpExec";
    private static final String HTTP_WRITER_NAME = "httpWriter";

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        p.addLast(HTTP_CODEC_NAME, new HttpServerCodec());
        p.addLast(new HttpContentCompressor());
        p.addLast(HTTP_EXEC_NAME, new HttpProxyExecHandler());
        p.addLast(HTTP_WRITER_NAME, new HttpProxyWriterHandler());
    }
}
